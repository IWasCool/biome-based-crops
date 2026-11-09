package com.ourcreation.biomebasedcrops;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Incremental geometry cache; climate-machine changes never restart its flood fill. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID)
public final class GreenhouseRoomScanner {
    private static final Map<Level, State> STATES = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong EPOCH = new java.util.concurrent.atomic.AtomicLong();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int HORIZONTAL = (1 << Direction.NORTH.ordinal()) | (1 << Direction.SOUTH.ordinal())
            | (1 << Direction.WEST.ordinal()) | (1 << Direction.EAST.ordinal());
    private static final int ROOF = 1 << Direction.UP.ordinal();

    private GreenhouseRoomScanner() { }

    public static boolean hasTracking(Level level) {
        if (level.isClientSide || level.getServer() != null && !level.getServer().isSameThread()) return false;
        State state = STATES.get(level);
        return state != null && state.epoch == EPOCH.get()
                && (!state.completed.isEmpty() || !state.pending.isEmpty() || !state.negative.isEmpty()
                        || GreenhouseEnvironment.hasRetainedAir(level));
    }

    static Geometry completedAroundController(Level level, BlockPos pos) {
        State state = STATES.get(level);
        if (state == null || state.epoch != EPOCH.get()) return null;
        List<Geometry> candidates = state.sections.get(SectionPos.asLong(pos));
        if (candidates == null) return null;
        Geometry closest = null;
        double distance = Double.MAX_VALUE;
        for (Geometry geometry : candidates) {
            if (!geometry.live || !geometry.ready) continue;
            boolean connected = geometry.interior.contains(pos.asLong());
            if (!connected) for (Direction direction : DIRECTIONS) {
                if (geometry.interior.contains(pos.relative(direction).asLong())) { connected = true; break; }
            }
            if (!connected) continue;
            double candidate = geometry.anchor.distSqr(pos);
            if (candidate < distance || candidate == distance && (closest == null || geometry.anchor.asLong() < closest.anchor.asLong())) {
                closest = geometry; distance = candidate;
            }
        }
        if (closest != null) closest.lastUse = System.nanoTime();
        return closest;
    }

    /** Explicit surveys take precedence over incidental underground biome queries. */
    public static void prioritize(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        State state = state(level);
        state.priority = pos.immutable();
        state.negative.remove(pos.asLong());
    }

    static Geometry find(Level level, BlockPos pos) {
        State state = state(level);
        Geometry existing = indexed(state, pos);
        if (existing != null) {
            if (state.priority != null && state.priority.distManhattan(pos) <= 1) state.priority = null;
            return existing;
        }
        boolean urgent = state.priority != null && state.priority.distManhattan(pos) <= 1;
        Negative negative = state.negative.get(pos.asLong());
        if (!urgent && negative != null && level.getGameTime() - negative.tick < 20) return Geometry.status(negative.status, pos);
        for (Scan scan : state.pending) {
            if (!scan.done && scan.containsBounds(pos)) {
                if (urgent) { scan.priority = true; state.priority = null; }
                return Geometry.status("scanning", pos);
            }
        }
        if (openToSky(level, pos)) return Geometry.status("open", pos);
        int pendingLimit = Math.min(4, Math.max(1, 1048576 / GreenhouseSettings.maxRoomBlocks()));
        if (state.pending.size() >= pendingLimit) {
            if (!urgent) return Geometry.status("scanning", pos);
            Scan abandoned = state.pending.removeLast();
            abandoned.fail("scanning");
        }
        Scan scan = new Scan(level, pos, state);
        scan.priority = urgent;
        if (urgent) state.priority = null;
        if (urgent) state.pending.addFirst(scan); else state.pending.addLast(scan);
        // A small bounded first step keeps ordinary rooms responsive. A larger
        // room continues through the global background budget, never to completion here.
        if (state.initialTick != level.getGameTime()) { state.initialTick = level.getGameTime(); state.initialSpent = 0; }
        int allowance = Math.max(0, Math.min(1024, GreenhouseSettings.scanBudget()) - state.initialSpent);
        state.initialSpent += scan.advance(allowance);
        if (scan.done) {
            state.pending.remove(scan);
            return scan.result;
        }
        return Geometry.status("scanning", pos);
    }

    private static Geometry indexed(State state, BlockPos pos) {
        List<Geometry> rooms = state.sections.get(SectionPos.asLong(pos));
        if (rooms == null) return null;
        for (Iterator<Geometry> iterator = rooms.iterator(); iterator.hasNext();) {
            Geometry room = iterator.next();
            if (!room.live) { iterator.remove(); continue; }
            if (!room.ready) continue;
            if (room.interior.contains(pos.asLong())) { room.lastUse = System.nanoTime(); return room; }
        }
        return null;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        if (!STATES.containsKey(level)) return;
        State state = state(level);
        GreenhouseEnvironment.withoutOverlay(() -> {
            int budget = GreenhouseSettings.scanBudget() - (state.initialTick == level.getGameTime() ? state.initialSpent : 0);
            Scan urgent = null;
            for (Scan scan : state.pending) if (scan.priority && !scan.done) { urgent = scan; break; }
            if (urgent != null) { state.pending.remove(urgent); state.pending.addFirst(urgent); }
            int jobs = state.pending.size();
            while (budget > 0 && jobs-- > 0 && !state.pending.isEmpty()) {
                Scan scan = state.pending.removeFirst();
                int used = scan.advance(scan.priority ? budget : Math.min(256, budget));
                budget -= Math.max(1, used);
                if (!scan.done) state.pending.addLast(scan);
                if (jobs == 0 && budget > 0 && !state.pending.isEmpty()) jobs = state.pending.size();
            }
            int cleanupBudget = 1024;
            while (cleanupBudget-- > 0 && !state.cleanup.isEmpty()) {
                Cleanup cleanup = state.cleanup.peekFirst();
                if (!cleanup.sections.hasNext()) { state.cleanup.removeFirst(); continue; }
                long section = cleanup.sections.nextLong();
                List<Geometry> rooms = state.sections.get(section);
                long chunk = ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section));
                List<Geometry> inChunk = state.chunks.get(chunk);
                if (inChunk != null) { inChunk.removeIf(room -> !room.live); if (inChunk.isEmpty()) state.chunks.remove(chunk); }
                if (rooms != null) {
                    rooms.removeIf(room -> !room.live);
                    if (rooms.isEmpty()) state.sections.remove(section);
                }
            }
            return null;
        });
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) STATES.remove(level);
    }

    static void clear() { EPOCH.incrementAndGet(); }

    private static State state(Level level) {
        synchronized (STATES) {
            State state = STATES.get(level);
            if (state == null || state.epoch != EPOCH.get()) {
                state = new State(); STATES.put(level, state);
            }
            return state;
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        State state = STATES.get(level);
        if (state == null) return;
        long chunk = event.getChunk().getPos().toLong();
        List<Geometry> affected = state.chunks.remove(chunk);
        if (affected != null) for (Geometry room : new ArrayList<>(affected)) {
            if (room.live) GreenhouseEnvironment.retainRoomAir(level, room);
            invalidate(state, room);
        }
        for (Scan scan : state.pending) if (!scan.done && scan.chunks.contains(chunk)) scan.fail("unloaded");
        GreenhouseEnvironment.invalidateClimate();
    }

    public static void blockChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
        State state = STATES.get(level);
        if (state == null || level.isClientSide || before == after) return;
        boolean oldSeal = GreenhouseEnvironment.sealedState(level, pos, before);
        boolean newSeal = GreenhouseEnvironment.sealedState(level, pos, after);
        ResourceLocation oldId = BuiltInRegistries.BLOCK.getKey(before.getBlock());
        ResourceLocation newId = BuiltInRegistries.BLOCK.getKey(after.getBlock());
        if (oldSeal == newSeal && oldId.equals(newId)) return;
        if (oldSeal != newSeal) {
            GreenhouseEnvironment.discardRetainedAirAt(level, pos);
            // A distant roof or doorway may have caused any failed flood fill.
            state.negative.clear();
        } else state.negative.remove(pos.asLong());
        java.util.HashSet<Geometry> touched = new java.util.HashSet<>();
        for (Direction direction : DIRECTIONS) {
            BlockPos near = pos.relative(direction);
            List<Geometry> candidates = state.sections.get(SectionPos.asLong(near));
            if (candidates == null) continue;
            for (Geometry geometry : candidates) {
                if (geometry.live && (geometry.interior.contains(pos.asLong()) || geometry.interior.contains(near.asLong()))) touched.add(geometry);
            }
        }
        for (Geometry geometry : touched) {
            if (oldSeal != newSeal) invalidate(state, geometry);
            else {
                adjustCounts(geometry.counts, oldId, newId);
                geometry.materialRevision++;
                geometry.machines.remove(pos.asLong());
                if (machine(after)) geometry.machines.add(pos.asLong());
                boolean oldGlass = GreenhouseEnvironment.isGlass(before), newGlass = GreenhouseEnvironment.isGlass(after);
                if (oldGlass != newGlass) {
                    int delta = newGlass ? 1 : -1, roles = geometry.boundaryRoles.get(pos.asLong());
                    geometry.glass += delta;
                    if ((roles & ROOF) != 0) geometry.roofGlass += delta;
                    int faces = Integer.bitCount(roles & HORIZONTAL);
                    geometry.wallGlass += delta * faces;
                    if (faces > 0) geometry.wallGlassBlocks += delta;
                }
            }
        }
        for (Scan scan : state.pending) {
            if (scan.done) continue;
            if ((oldSeal != newSeal || GreenhouseEnvironment.isGlass(before) != GreenhouseEnvironment.isGlass(after))
                    && (scan.visited.contains(pos.asLong()) || scan.shell.containsKey(pos.asLong()))) {
                scan.fail("scanning");
            } else if (oldSeal == newSeal && scan.observed.contains(pos.asLong())) {
                adjustCounts(scan.counts, oldId, newId);
                scan.machines.remove(pos.asLong());
                if (machine(after)) scan.machines.add(pos.asLong());
            }
        }
        GreenhouseEnvironment.invalidateClimate();
    }

    private static void invalidate(State state, Geometry room) {
        if (!room.live) return;
        room.live = false;
        state.completed.remove(room);
        state.cleanup.addLast(new Cleanup(room.sections.iterator()));
        // Drop the large cell set immediately; the lightweight section references
        // are cleaned a bounded number at a time on subsequent level ticks.
        room.interior = LongSets.EMPTY_SET;
        room.boundaryRoles = new Long2ByteOpenHashMap();
    }

    static void evictForMemoryPressure(Level level, Geometry room) {
        State state = STATES.get(level);
        if (state == null || !room.live) return;
        GreenhouseEnvironment.retainRoomAir(level, room);
        invalidate(state, room);
    }

    private static void adjustCounts(Map<ResourceLocation, Integer> counts, ResourceLocation oldId, ResourceLocation newId) {
        counts.computeIfPresent(oldId, (id, count) -> count <= 1 ? null : count - 1);
        counts.merge(newId, 1, Integer::sum);
    }

    private static boolean machine(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.equals(ResourceLocation.parse("biome_based_crops:air_conditioner"))
                || id.equals(ResourceLocation.parse("toughasnails:thermoregulator"))
                || id.equals(ResourceLocation.parse("northstar:temperature_regulator"));
    }

    private static boolean openToSky(Level level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) <= pos.getY();
    }

    static final class Geometry {
        boolean live = true;
        boolean ready = true;
        final String status;
        final BlockPos anchor;
        final RoomKey key;
        LongSet interior;
        final LongSet sections;
        final Map<ResourceLocation, Integer> counts;
        final LongSet machines;
        Long2ByteOpenHashMap boundaryRoles;
        int glass, roof, roofGlass, wall, wallGlass, wallGlassBlocks;
        long materialRevision;
        long lastUse = System.nanoTime();

        Geometry(String status, BlockPos anchor, LongSet interior, LongSet sections, Map<ResourceLocation, Integer> counts,
                LongSet machines, Long2ByteOpenHashMap roles, RoomKey key) {
            this.status = status; this.anchor = anchor; this.interior = LongSets.unmodifiable(interior);
            this.sections = sections; this.counts = counts; this.machines = machines; this.boundaryRoles = roles; this.key = key;
        }
        static Geometry status(String status, BlockPos at) {
            return new Geometry(status, at.immutable(), LongSets.EMPTY_SET, LongSets.EMPTY_SET, new HashMap<>(),
                    new LongOpenHashSet(), new Long2ByteOpenHashMap(), null);
        }
        boolean glassValid() {
            return roof > 0 && roofGlass >= Math.ceil(roof * GreenhouseSettings.roofGlassRatio())
                    && wallGlassBlocks >= 4 && wall > 0 && wallGlass >= Math.ceil(wall * GreenhouseSettings.wallGlassRatio());
        }
    }

    /** The fingerprint identifies exact air cells without retaining their potentially huge set. */
    record RoomKey(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int volume, long hashSum, long hashXor) {
        boolean touches(BlockPos pos) {
            return pos.getX() >= minX - 1 && pos.getX() <= maxX + 1
                    && pos.getY() >= minY - 1 && pos.getY() <= maxY + 1
                    && pos.getZ() >= minZ - 1 && pos.getZ() <= maxZ + 1;
        }
    }

    private static final class State {
        final long epoch = EPOCH.get();
        long initialTick = Long.MIN_VALUE;
        int initialSpent;
        final Long2ObjectOpenHashMap<List<Geometry>> sections = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<List<Geometry>> chunks = new Long2ObjectOpenHashMap<>();
        final ArrayDeque<Scan> pending = new ArrayDeque<>();
        final List<Geometry> completed = new ArrayList<>();
        final ArrayDeque<Cleanup> cleanup = new ArrayDeque<>();
        final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Negative> negative = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
        BlockPos priority;
    }

    private record Cleanup(it.unimi.dsi.fastutil.longs.LongIterator sections) { }
    private record Negative(long tick, String status) { }

    private static final class Scan {
        final Level level;
        final State owner;
        final BlockPos start;
        final LongOpenHashSet visited = new LongOpenHashSet();
        final LongOpenHashSet observed = new LongOpenHashSet();
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final Long2ByteOpenHashMap shell = new Long2ByteOpenHashMap();
        final Map<ResourceLocation, Integer> counts = new HashMap<>();
        final LongOpenHashSet machines = new LongOpenHashSet();
        final LongOpenHashSet sections = new LongOpenHashSet();
        final LongOpenHashSet chunks = new LongOpenHashSet();
        int minX, maxX, minY, maxY, minZ, maxZ;
        long hashSum, hashXor;
        int glass, roof, roofGlass, wall, wallGlass, wallGlassBlocks;
        BlockPos floorPoint;
        boolean done;
        boolean priority;
        Geometry result;
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry> finishing;
        it.unimi.dsi.fastutil.longs.LongIterator indexing;

        Scan(Level level, BlockPos start, State owner) {
            this.level = level; this.start = start.immutable(); this.owner = owner;
            minX = maxX = start.getX(); minY = maxY = start.getY(); minZ = maxZ = start.getZ(); floorPoint = start.immutable();
            visited.add(start.asLong()); fingerprint(start.asLong()); queue.enqueue(start.asLong()); observe(start);
        }
        boolean containsBounds(BlockPos pos) {
            return pos.getX() >= minX - 1 && pos.getX() <= maxX + 1 && pos.getY() >= minY - 1
                    && pos.getY() <= maxY + 1 && pos.getZ() >= minZ - 1 && pos.getZ() <= maxZ + 1;
        }
        int advance(int budget) {
            if (done) return 0;
            int used = 0;
            while (used < budget && !queue.isEmpty() && !done) {
                BlockPos current = BlockPos.of(queue.dequeueLong()); used++;
                if (openToSky(level, current)) { fail("open"); break; }
                for (Direction direction : DIRECTIONS) {
                    BlockPos next = current.relative(direction);
                    if (!GreenhouseEnvironment.loaded(level, next)) { fail("unloaded"); break; }
                    long packed = next.asLong();
                    if (visited.contains(packed)) continue;
                    if (GreenhouseEnvironment.sealed(level, next)) {
                        shell.put(packed, (byte)(shell.get(packed) | 1 << direction.ordinal()));
                        observe(next);
                    } else if (visited.add(packed)) {
                        if (visited.size() > GreenhouseSettings.maxRoomBlocks()) { fail("too_large"); break; }
                        fingerprint(packed);
                        queue.enqueue(packed); observe(next);
                        minX = Math.min(minX, next.getX()); maxX = Math.max(maxX, next.getX());
                        minZ = Math.min(minZ, next.getZ()); maxZ = Math.max(maxZ, next.getZ());
                        if (next.getY() < minY) { minY = next.getY(); floorPoint = next.immutable(); }
                        maxY = Math.max(maxY, next.getY());
                    }
                }
            }
            if (!done && queue.isEmpty()) {
                if (finishing == null) finishing = shell.long2ByteEntrySet().fastIterator();
                while (used < budget && finishing.hasNext()) {
                    var entry = finishing.next(); used++;
                    BlockPos at = BlockPos.of(entry.getLongKey());
                    if (!GreenhouseEnvironment.loaded(level, at)) { fail("unloaded"); break; }
                    int mask = entry.getByteValue(), exposed = 0;
                    for (Direction direction : DIRECTIONS) {
                        int bit = 1 << direction.ordinal();
                        if ((mask & bit) != 0 && !visited.contains(at.relative(direction).asLong())) exposed |= bit;
                    }
                    entry.setValue((byte)exposed);
                    boolean glazed = GreenhouseEnvironment.isGlass(level.getBlockState(at));
                    if ((exposed & ROOF) != 0) { roof++; if (glazed) roofGlass++; }
                    int faces = Integer.bitCount(exposed & HORIZONTAL);
                    wall += faces;
                    if (glazed && faces > 0) { wallGlass += faces; wallGlassBlocks++; }
                }
                if (!done && !finishing.hasNext() && result == null) beginFinish();
                while (!done && result != null && used < budget && indexing.hasNext()) {
                    long section = indexing.nextLong(); used++;
                    owner.sections.computeIfAbsent(section, ignored -> new ArrayList<>()).add(result);
                    long chunk = ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section));
                    List<Geometry> inChunk = owner.chunks.computeIfAbsent(chunk, ignored -> new ArrayList<>());
                    if (!inChunk.contains(result)) inChunk.add(result);
                }
                if (!done && result != null && !indexing.hasNext()) finish();
            }
            return used;
        }
        void observe(BlockPos pos) {
            if (!observed.add(pos.asLong())) return;
            BlockState state = level.getBlockState(pos);
            counts.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()), 1, Integer::sum);
            if (GreenhouseEnvironment.isGlass(state)) glass++;
            if (machine(state)) machines.add(pos.asLong());
            sections.add(SectionPos.asLong(pos));
            chunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        void fingerprint(long packed) {
            hashSum += it.unimi.dsi.fastutil.HashCommon.mix(packed);
            hashXor ^= it.unimi.dsi.fastutil.HashCommon.mix(packed ^ 0x9E3779B97F4A7C15L);
        }
        void beginFinish() {
            BlockPos anchor = new BlockPos((minX + maxX) >> 1, minY, (minZ + maxZ) >> 1);
            if (!visited.contains(anchor.asLong())) anchor = floorPoint;
            RoomKey key = new RoomKey(minX, minY, minZ, maxX, maxY, maxZ, visited.size(), hashSum, hashXor);
            result = new Geometry("closed", anchor, visited, sections, counts, machines, shell, key);
            result.glass = glass; result.roof = roof; result.roofGlass = roofGlass;
            result.wall = wall; result.wallGlass = wallGlass; result.wallGlassBlocks = wallGlassBlocks;
            result.ready = false;
            indexing = sections.iterator();
        }
        void finish() {
            result.ready = true;
            done = true;
            owner.completed.add(result);
            // Bound retained cell memory as well as room count. Eviction affects
            // cache residency, never the supported physical room dimensions.
            long cells = owner.completed.stream().mapToLong(room -> room.interior.size()).sum();
            while (owner.completed.size() > 64 || cells > 1048576L) {
                Geometry oldest = owner.completed.stream().filter(room -> room != result)
                        .min(java.util.Comparator.comparingLong(room -> room.lastUse)).orElse(null);
                if (oldest == null) break;
                cells -= oldest.interior.size();
                evictForMemoryPressure(level, oldest);
            }
            GreenhouseEnvironment.invalidateClimate();
        }
        void fail(String status) {
            if (result != null && !result.ready) invalidate(owner, result);
            done = true; result = Geometry.status(status, start);
            if (!status.equals("scanning")) {
                owner.negative.put(start.asLong(), new Negative(level.getGameTime(), status));
                if (owner.negative.size() > 1024) owner.negative.removeFirst();
            }
        }
    }
}
