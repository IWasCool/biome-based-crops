package com.ourcreation.biomebasedcrops;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class GreenhouseEnvironment {
    private static final TagKey<Block> HEAT = tag("heat_sources");
    private static final TagKey<Block> COOLING = tag("cooling_sources");
    private static final TagKey<Block> GLASS = tag("greenhouse_glass");
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final CropClimate[] CLIMATES = CropClimate.values();
    private static final ThreadLocal<Integer> NATIVE_QUERY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private GreenhouseEnvironment() {
    }

    /** Read the stored world biome while bypassing greenhouse query overlays. */
    public static Holder<Biome> nativeBiome(Level level, BlockPos pos) {
        return withoutOverlay(() -> level.getBiome(pos));
    }

    private static <T> T withoutOverlay(java.util.function.Supplier<T> action) {
        int previous = NATIVE_QUERY_DEPTH.get();
        NATIVE_QUERY_DEPTH.set(previous + 1);
        try {
            return action.get();
        } finally {
            if (previous == 0) NATIVE_QUERY_DEPTH.remove();
            else NATIVE_QUERY_DEPTH.set(previous);
        }
    }

    /** Resolve an exact gameplay position without changing persistent chunk biome palettes. */
    public static Holder<Biome> overlayBiome(Level level, BlockPos pos, Holder<Biome> nativeBiome) {
        if (NATIVE_QUERY_DEPTH.get() != 0) return nativeBiome;
        if (level.isClientSide) return GreenhouseClientBiomes.biomeAt(level, pos, nativeBiome);
        BiomeRegion region = regionAt(level, pos);
        return region == null ? nativeBiome
                : level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(region.biome())
                        .<Holder<Biome>>map(biome -> biome).orElse(nativeBiome);
    }

    /** A valid room's exact air/plant cells, excluding its floor, walls, and roof. */
    public static BiomeRegion regionAt(Level level, BlockPos pos) {
        if (level.isClientSide || NATIVE_QUERY_DEPTH.get() != 0 || !GreenhouseSettings.enabled() || !loaded(level, pos)) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        Cache cache = cache(level);
        Room room = cache.rooms.get(pos.asLong());
        if (!fresh(room, level.getGameTime())) {
            // Most biome queries are outdoors. A bounded room has a ceiling at
            // most five cells overhead; reject those queries before flood fill.
            return withoutOverlay(() -> {
                if (sealed(level, pos) || !hasNearbyCeiling(level, pos)) return null;
                return regionOf(roomAt(level, pos), pos);
            });
        }
        return regionOf(room, pos);
    }

    private static BiomeRegion regionOf(Room room, BlockPos pos) {
        if (!room.inspection.valid() || !room.interior.contains(pos.asLong())) return null;
        return new BiomeRegion(room.inspection.targetBiome(), room.interior);
    }

    private static boolean hasNearbyCeiling(Level level, BlockPos pos) {
        for (int height = 1; height <= 5; height++) {
            BlockPos above = pos.above(height);
            if (!loaded(level, above)) return false;
            if (sealed(level, above)) return true;
        }
        return false;
    }

    public static Inspection inspect(Level level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return result(false, null, CropClimate.NEUTRAL, new Temperature(0, 0), 0, 0, List.of(), "unloaded");
        }
        if (!GreenhouseSettings.enabled()) {
            Temperature temperature = temperature(level, pos);
            return result(false, null, shifted(CropClimate.at(nativeBiome(level, pos), pos), temperature, 2),
                    temperature, 0, 0, List.of(), "disabled", CropClimate.at(nativeBiome(level, pos), pos));
        }
        return roomAt(level, pos).inspection;
    }

    /** Exact biome rules use the recognized biome and variants with an identical habitat recipe. */
    public static boolean canSupport(Level level, BlockPos pos, CropProfile profile) {
        if (!loaded(level, pos) || !GreenhouseSettings.enabled()) return false;
        Room room = roomAt(level, pos);
        if (!room.inspection.valid() || !room.interior.contains(pos.asLong())) return false;
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        return room.aliases.stream().anyMatch(id -> biomes.getHolder(id).map(biome -> profile.allows(biome, pos)).orElse(false));
    }

    public static CropClimate effectiveClimate(Level level, BlockPos pos) {
        if (!loaded(level, pos)) return CropClimate.NEUTRAL;
        return inspect(level, pos).climate();
    }

    public static void clearCache() {
        CACHES.clear();
    }

    private static Room roomAt(Level level, BlockPos pos) {
        return withoutOverlay(() -> findRoomAt(level, pos));
    }

    private static Room findRoomAt(Level level, BlockPos pos) {
        Cache cache = cache(level);
        long now = level.getGameTime();
        Room existing = cache.rooms.get(pos.asLong());
        if (fresh(existing, now)) {
            if (existing.inspection.enclosed()) return existing;
            // Room geometry and source scans may be cached, but a biome/datapack
            // change must take effect immediately for plants in the open air.
            String status = existing.inspection.status().substring(key("").length());
            return outdoorRoom(level, pos, existing.tick, status);
        }
        java.util.ArrayList<BlockPos> starts = new java.util.ArrayList<>();
        if (!sealed(level, pos)) {
            starts.add(pos);
        } else {
            // A probe aimed at soil, glass, or an old core can inspect its adjacent room.
            // A failed outdoor start must not prevent trying the indoor side of that wall.
            for (Direction direction : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH,
                    Direction.WEST, Direction.EAST, Direction.DOWN}) {
                BlockPos next = pos.relative(direction);
                if (loaded(level, next) && !sealed(level, next)) starts.add(next);
            }
        }
        Room selected = null;
        for (BlockPos start : starts) {
            Room candidate = cache.rooms.get(start.asLong());
            if (!fresh(candidate, now)) {
                candidate = scanRoom(level, start, now, cache);
                if (candidate.inspection.enclosed()) {
                    for (long member : candidate.interior) cache.rooms.put(member, candidate);
                } else {
                    cache.rooms.put(start.asLong(), candidate);
                }
            }
            if (selected == null || candidate.inspection.valid() && !selected.inspection.valid()
                    || candidate.inspection.enclosed() && !selected.inspection.enclosed()) {
                selected = candidate;
            }
            if (candidate.inspection.valid()) break;
        }
        if (selected == null) selected = outdoorRoom(level, pos, now, "open");
        cache.rooms.put(pos.asLong(), selected);
        return selected;
    }

    private static boolean fresh(Room room, long now) {
        return room != null && now >= room.tick && now - room.tick < GreenhouseSettings.cacheTicks();
    }

    private static Room scanRoom(Level level, BlockPos start, long tick, Cache cache) {
        int maxSide = GreenhouseSettings.radius() * 2 + 1;
        Set<Long> interior = new HashSet<>();
        Set<Long> shell = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        interior.add(start.asLong());
        queue.add(start);
        int minX = start.getX(), maxX = minX;
        int minY = start.getY(), maxY = minY;
        int minZ = start.getZ(), maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!loaded(level, next)) return outdoorRoom(level, start, tick, "unloaded");
                long packed = next.asLong();
                if (shell.contains(packed) || interior.contains(packed)) continue;
                if (sealed(level, next)) {
                    shell.add(packed);
                    continue;
                }
                minX = Math.min(minX, next.getX()); maxX = Math.max(maxX, next.getX());
                minY = Math.min(minY, next.getY()); maxY = Math.max(maxY, next.getY());
                minZ = Math.min(minZ, next.getZ()); maxZ = Math.max(maxZ, next.getZ());
                if (maxX - minX + 1 > maxSide || maxZ - minZ + 1 > maxSide || maxY - minY + 1 > 5) {
                    return outdoorRoom(level, start, tick, "open");
                }
                interior.add(packed);
                queue.addLast(next);
            }
        }
        // Bounds come from the room itself, so crops at opposite edges use the same climate.
        BlockPos anchor = new BlockPos((minX + maxX) >> 1, minY, (minZ + maxZ) >> 1);
        Set<Long> observed = new HashSet<>(interior);
        observed.addAll(shell);
        Map<ResourceLocation, Integer> counts = new java.util.HashMap<>();
        int glass = 0, heat = 0, cooling = 0;
        boolean glassRoof = false;
        int radius = GreenhouseSettings.radius();
        for (long packed : observed) {
            BlockPos at = BlockPos.of(packed);
            BlockState state = level.getBlockState(at);
            ResourceLocation block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            counts.merge(block, 1, Integer::sum);
            if (isGlass(state)) {
                glass++;
                if (shell.contains(packed) && interior.contains(at.below().asLong()) && !interior.contains(at.above().asLong())) {
                    glassRoof = true;
                }
            }
            if (Math.abs(at.getX() - anchor.getX()) <= radius && Math.abs(at.getZ() - anchor.getZ()) <= radius
                    && at.getY() >= minY - 1 && at.getY() <= minY + 2) {
                heat = Math.max(heat, heatStrength(state));
                cooling = Math.max(cooling, coolingStrength(state));
            }
        }
        Temperature temperature = GreenhouseSettings.ambientEnabled() ? new Temperature(heat, cooling) : new Temperature(0, 0);
        CropClimate baseClimate = CropClimate.at(nativeBiome(level, anchor), anchor);
        CropClimate actual = shifted(baseClimate, temperature, 4);
        Map<CropClimate, Boolean> thermostat = new java.util.EnumMap<>(CropClimate.class);
        List<Match> complete = new java.util.ArrayList<>();
        List<Match> warmEnough = new java.util.ArrayList<>();
        Match closest = null;
        for (Definition definition : definitions(level, cache)) {
            Match match = match(definition, counts);
            if (closest == null || compareProgress(match, closest) < 0) closest = match;
            if (!match.complete()) continue;
            complete.add(match);
            if (actual == definition.recipe.climate() || thermostat.computeIfAbsent(definition.recipe.climate(), climate ->
                    ThermoregulatorDetector.canSupportProfile(level, anchor, new CropProfile(Set.of(climate))))) {
                warmEnough.add(match);
            }
        }
        ResourceLocation nativeId = nativeBiome(level, anchor).unwrapKey().map(biome -> biome.location()).orElse(null);
        java.util.Comparator<Match> specificity = java.util.Comparator
                .comparingInt((Match match) -> match.definition.recipe.count()).reversed()
                .thenComparing(java.util.Comparator.comparingInt((Match match) -> match.definition.recipe.requirements().size()).reversed())
                .thenComparing(match -> !match.definition.id.equals(nativeId))
                .thenComparing(match -> match.definition.id.toString());
        complete.sort(specificity);
        warmEnough.sort(specificity);
        Match chosen = !warmEnough.isEmpty() ? warmEnough.getFirst() : !complete.isEmpty() ? complete.getFirst() : closest;
        Set<ResourceLocation> aliases = new HashSet<>();
        if (!warmEnough.isEmpty()) {
            Match top = warmEnough.getFirst();
            for (Match match : warmEnough) {
                if (sameSignature(top.definition.recipe, match.definition.recipe)) aliases.add(match.definition.id);
            }
        }
        String status;
        if (glass < 4 || !glassRoof) status = "no_glass";
        else if (!warmEnough.isEmpty()) status = "valid";
        else if (!complete.isEmpty()) status = "wrong_temperature";
        else status = "missing_specimens";
        // A room with no recognizable specimens has no plausible target to advertise.
        if (chosen != null && chosen.matched == 0) chosen = null;
        ResourceLocation target = chosen == null ? null : chosen.definition.id;
        List<RequirementProgress> progress = chosen == null ? List.of() : chosen.progress;
        List<ResourceLocation> specimens = chosen == null ? List.of() : chosen.definition.recipe.specimens();
        Inspection inspection = new Inspection(true, target,
                status.equals("valid") ? chosen.definition.recipe.climate() : actual, temperature.heat, temperature.cooling,
                glass, chosen == null ? 0 : chosen.matched, chosen == null ? 0 : chosen.definition.recipe.count(), key(status), specimens, progress, baseClimate);
        return new Room(tick, inspection, Set.copyOf(interior), Set.copyOf(aliases));
    }

    private static List<Definition> definitions(Level level, Cache cache) {
        if (cache.definitions == null) {
            cache.definitions = level.registryAccess().registryOrThrow(Registries.BIOME).holders()
                    .map(biome -> new Definition(biome.key().location(), GreenhouseRecipes.forServerBiome(biome)))
                    .sorted(java.util.Comparator.comparing(definition -> definition.id.toString())).toList();
        }
        return cache.definitions;
    }

    private static Match match(Definition definition, Map<ResourceLocation, Integer> counts) {
        int matched = 0;
        boolean complete = true;
        List<RequirementProgress> progress = new java.util.ArrayList<>();
        for (var required : definition.recipe.requirements()) {
            int found = counts.getOrDefault(required.block(), 0);
            matched += Math.min(found, required.count());
            complete &= found >= required.count();
            progress.add(new RequirementProgress(required.block(), found, required.count()));
        }
        return new Match(definition, matched, complete, List.copyOf(progress));
    }

    private static int compareProgress(Match first, Match second) {
        long a = (long) first.matched * second.definition.recipe.count();
        long b = (long) second.matched * first.definition.recipe.count();
        if (a != b) return Long.compare(b, a);
        if (first.matched != second.matched) return Integer.compare(second.matched, first.matched);
        int distinct = Integer.compare(second.definition.recipe.requirements().size(), first.definition.recipe.requirements().size());
        return distinct != 0 ? distinct : first.definition.id.toString().compareTo(second.definition.id.toString());
    }

    private static boolean sameSignature(GreenhouseRecipes.Recipe first, GreenhouseRecipes.Recipe second) {
        if (first.climate() != second.climate() || first.count() != second.count()) return false;
        return new HashSet<>(first.requirements()).equals(new HashSet<>(second.requirements()));
    }

    private static Room outdoorRoom(Level level, BlockPos pos, long tick, String status) {
        Temperature temperature = temperature(level, pos);
        return new Room(tick, result(false, null, shifted(CropClimate.at(nativeBiome(level, pos), pos), temperature, 2),
                temperature, 0, 0, List.of(), status, CropClimate.at(nativeBiome(level, pos), pos)), Set.of(), Set.of());
    }

    private static Temperature temperature(Level level, BlockPos pos) {
        if (!GreenhouseSettings.ambientEnabled()) return new Temperature(0, 0);
        Cache cache = cache(level);
        long now = level.getGameTime();
        long key = pos.asLong();
        TimedTemperature cached = cache.temperatures.get(key);
        if (cached != null && now >= cached.tick && now - cached.tick < GreenhouseSettings.cacheTicks()) return cached.temperature;
        int radius = GreenhouseSettings.radius();
        int heat = 0;
        int cooling = 0;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, 2, radius))) {
            if (loaded(level, at)) {
                BlockState state = level.getBlockState(at);
                heat = Math.max(heat, heatStrength(state));
                cooling = Math.max(cooling, coolingStrength(state));
            }
        }
        Temperature result = new Temperature(heat, cooling);
        cache.temperatures.put(key, new TimedTemperature(now, result));
        return result;
    }

    static CropClimate shifted(CropClimate original, int heat, int cooling, int cap) {
        int steps = Mth.clamp((heat - cooling) / 8, -cap, cap);
        return CLIMATES[Mth.clamp(original.ordinal() + steps, 0, CLIMATES.length - 1)];
    }

    private static CropClimate shifted(CropClimate original, Temperature temperature, int cap) {
        return shifted(original, temperature.heat, temperature.cooling, cap);
    }

    public static int heatStrength(BlockState state) {
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) return 0;
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)) return 8;
        if (state.is(Blocks.LAVA)) return 32;
        if (state.is(Blocks.MAGMA_BLOCK)) return 16;
        return state.is(HEAT) ? 8 : 0;
    }

    public static int coolingStrength(BlockState state) {
        if (state.is(Blocks.BLUE_ICE)) return 32;
        if (state.is(Blocks.PACKED_ICE)) return 24;
        if (state.is(Blocks.ICE)) return 16;
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(COOLING) ? 8 : 0;
    }

    private static boolean sealed(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(BlockStateProperties.OPEN);
        }
        return isGlass(state) || state.getBlock() instanceof FarmBlock || state.getBlock() instanceof DirtPathBlock
                || state.getBlock() instanceof SoulSandBlock || state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isGlass(BlockState state) {
        return state.is(GLASS) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS);
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static Cache cache(Level level) {
        return CACHES.computeIfAbsent(level, unused -> new Cache());
    }

    private static Inspection result(boolean enclosed, ResourceLocation target, CropClimate climate, Temperature temperature,
            int glass, int specimens, List<ResourceLocation> materials, String status) {
        return result(enclosed, target, climate, temperature, glass, specimens, materials, status, climate);
    }

    private static Inspection result(boolean enclosed, ResourceLocation target, CropClimate climate, Temperature temperature,
            int glass, int specimens, List<ResourceLocation> materials, String status, CropClimate baseClimate) {
        return new Inspection(enclosed, target, climate, temperature.heat, temperature.cooling, glass, specimens,
                GreenhouseSettings.requiredSpecimens(), key(status), materials, List.of(), baseClimate);
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, path));
    }

    private static String key(String status) {
        return "biome_based_crops.greenhouse.status." + status;
    }

    public record Inspection(boolean enclosed, ResourceLocation targetBiome, CropClimate climate, int heat, int cooling,
            int glass, int specimens, int requiredSpecimens, String status, List<ResourceLocation> specimenBlocks,
            List<RequirementProgress> requirements, CropClimate baseClimate) {
        public Inspection {
            specimenBlocks = List.copyOf(specimenBlocks);
            requirements = List.copyOf(requirements);
        }

        public Inspection(boolean enclosed, ResourceLocation targetBiome, CropClimate climate, int heat, int cooling,
                int glass, int specimens, int requiredSpecimens, String status, List<ResourceLocation> specimenBlocks) {
            this(enclosed, targetBiome, climate, heat, cooling, glass, specimens, requiredSpecimens, status, specimenBlocks, List.of(), climate);
        }

        public Inspection(boolean enclosed, ResourceLocation targetBiome, CropClimate climate, int heat, int cooling,
                int glass, int specimens, int requiredSpecimens, String status, List<ResourceLocation> specimenBlocks,
                List<RequirementProgress> requirements) {
            this(enclosed, targetBiome, climate, heat, cooling, glass, specimens, requiredSpecimens, status, specimenBlocks, requirements, climate);
        }

        public boolean valid() {
            return status.equals(key("valid"));
        }
    }

    public record RequirementProgress(ResourceLocation block, int found, int required) {
    }

    public record BiomeRegion(ResourceLocation biome, Set<Long> interior) {
        public BiomeRegion {
            interior = Set.copyOf(interior);
        }
    }

    private record Definition(ResourceLocation id, GreenhouseRecipes.Recipe recipe) {
    }

    private record Match(Definition definition, int matched, boolean complete, List<RequirementProgress> progress) {
    }

    private record Temperature(int heat, int cooling) {
    }

    private record TimedTemperature(long tick, Temperature temperature) {
    }

    private record Room(long tick, Inspection inspection, Set<Long> interior, Set<ResourceLocation> aliases) {
    }

    private static final class Cache {
        final Map<Long, Room> rooms = boundedMap(4096);
        final Map<Long, TimedTemperature> temperatures = boundedMap(4096);
        List<Definition> definitions;
    }

    private static <V> Map<Long, V> boundedMap(int limit) {
        return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) {
                return size() > limit;
            }
        });
    }
}
