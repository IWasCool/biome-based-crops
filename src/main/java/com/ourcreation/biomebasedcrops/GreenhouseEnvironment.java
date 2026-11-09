package com.ourcreation.biomebasedcrops;

import java.util.Collections;
import java.util.HashSet;
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
    private static final TagKey<Block> GLASS = tag("greenhouse_glass");
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong CLIMATE_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<Integer> NATIVE_QUERY_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_RETAINED_AIR = 256;

    private GreenhouseEnvironment() {
    }

    /** Read the stored world biome while bypassing greenhouse query overlays. */
    public static Holder<Biome> nativeBiome(Level level, BlockPos pos) {
        return withoutOverlay(() -> level.getBiome(pos));
    }

    /** Controller pricing uses the room's common native baseline without starting a scan. */
    public static CropClimate naturalClimateForController(Level level, BlockPos pos) {
        var geometry = GreenhouseRoomScanner.completedAroundController(level, pos);
        BlockPos at = geometry == null ? pos : geometry.anchor;
        return CropClimate.at(nativeBiome(level, at), at);
    }

    public static int temperatureForClimate(CropClimate climate) {
        return switch (climate) { case ICY -> -10; case COLD -> 5; case NEUTRAL -> 20; case WARM -> 30; case HOT -> 45; };
    }

    public static float naturalTemperatureForController(Level level, BlockPos pos) {
        var geometry = GreenhouseRoomScanner.completedAroundController(level, pos);
        return naturalTemperature(level, geometry == null ? pos : geometry.anchor);
    }

    private static float naturalTemperature(Level level, BlockPos pos) {
        var nativePlanet = NorthstarAtmosphere.naturalTemperature(level, pos);
        return nativePlanet.isPresent() ? (float) nativePlanet.getAsDouble() : temperatureForClimate(CropClimate.at(nativeBiome(level, pos), pos));
    }

    /** Completed geometry only, with optional bounded discovery for a newly placed controller. */
    public static RoomClimateState controllerAirState(Level level, BlockPos pos, boolean discover) {
        if (level.isClientSide || !loaded(level, pos)) return null;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.completedAroundController(level, pos);
            if (geometry == null && discover) {
                roomAt(level, pos);
                geometry = GreenhouseRoomScanner.completedAroundController(level, pos);
            }
            return geometry == null || !geometry.live ? null : airState(level, geometry, cache(level));
        });
    }

    private static RoomClimateState airState(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache) {
        float nativeTemperature = naturalTemperature(level, geometry.anchor);
        boolean breathable = NorthstarAtmosphere.naturalAtmosphereBreathable(level)
                || NorthstarAtmosphere.nativeProviderBreathable(level, geometry.anchor);
        RoomClimateState state = cache.airStates.get(geometry);
        if (state == null) {
            RoomClimateState.Snapshot retained = cache.retainedAir.remove(geometry.key);
            state = retained == null ? new RoomClimateState(geometry.interior.size(), nativeTemperature, breathable, level.getGameTime())
                    : RoomClimateState.restore(retained, level.getGameTime(), nativeTemperature, breathable);
            cache.airStates.put(geometry, state);
        }
        state.advance(level.getGameTime(), nativeTemperature, breathable, geometry.live);
        Long providersTick = cache.providerTicks.get(geometry);
        if (providersTick == null || providersTick != level.getGameTime()) {
            cache.providerTicks.put(geometry, level.getGameTime());
            for (long packed : geometry.machines) {
                BlockPos at = BlockPos.of(packed);
                if (!loaded(level, at) || level.getBlockEntity(at) instanceof AirConditionerBlockEntity) continue;
                var output = ThermoregulatorDetector.providedTemperatureAt(level, at, geometry.anchor);
                if (output.isPresent()) state.addPaidDrive(packed, level.getGameTime(), (float) output.getAsDouble(), false);
            }
        }
        return state;
    }

    static boolean hasRetainedAir(Level level) {
        Cache cache = CACHES.get(level);
        return cache != null && !cache.retainedAir.isEmpty();
    }

    /** Retain only small physical values when scanner memory pressure drops an unchanged room. */
    static void retainRoomAir(Level level, GreenhouseRoomScanner.Geometry geometry) {
        Cache cache = CACHES.get(level);
        if (cache == null || geometry.key == null) return;
        RoomClimateState state = cache.airStates.remove(geometry);
        if (state == null) return;
        state.advance(level.getGameTime(), state.naturalTemperature(), state.naturalBreathable(), true);
        cache.retainedAir.put(geometry.key, state.snapshot());
        while (cache.retainedAir.size() > MAX_RETAINED_AIR) cache.retainedAir.pollFirstEntry();
        cache.rooms.remove(geometry);
        cache.materialMatches.remove(geometry);
        cache.providerTicks.remove(geometry);
    }

    static void discardRetainedAirAt(Level level, BlockPos pos) {
        Cache cache = CACHES.get(level);
        if (cache != null) cache.retainedAir.keySet().removeIf(key -> key.touches(pos));
    }

    public static void advanceRoomAir(Level level) {
        Cache cache = CACHES.get(level);
        if (cache == null) return;
        withoutOverlay(() -> {
            for (var entry : new java.util.ArrayList<>(cache.airStates.entrySet())) {
                var geometry = entry.getKey();
                if (geometry == null) continue;
                if (geometry.live && loaded(level, geometry.anchor)) airState(level, geometry, cache);
                else {
                    RoomClimateState air = entry.getValue();
                    air.advance(level.getGameTime(), air.naturalTemperature(), air.naturalBreathable(), false);
                }
            }
            return null;
        });
    }

    public static float currentTemperature(Level level, BlockPos pos) {
        float natural = loaded(level, pos) ? naturalTemperature(level, pos) : 20F;
        if (level.isClientSide) return GreenhouseClientBiomes.temperatureAt(level, pos, natural);
        var air = physicalAirAt(level, pos);
        return air == null ? natural : air.currentTemperature();
    }

    public static float oxygenPercent(Level level, BlockPos pos) {
        float natural = NorthstarAtmosphere.naturalAtmosphereBreathable(level)
                || NorthstarAtmosphere.nativeProviderBreathable(level, pos) ? 100F : 0F;
        if (level.isClientSide) return GreenhouseClientBiomes.oxygenAt(level, pos, natural);
        var air = physicalAirAt(level, pos);
        return air == null ? natural : Math.max(natural, air.oxygenPercent());
    }

    public static boolean isBreathable(Level level, BlockPos pos) {
        return NorthstarAtmosphere.naturalAtmosphereBreathable(level)
                || NorthstarAtmosphere.nativeProviderBreathable(level, pos) || greenhouseProvidesOxygen(level, pos);
    }

    public static boolean greenhouseProvidesOxygen(Level level, BlockPos pos) {
        if (level.isClientSide) return GreenhouseClientBiomes.hasOxygen(level, pos);
        var air = physicalAirAt(level, pos);
        return air != null && air.breathable();
    }

    private static RoomClimateState physicalAirAt(Level level, BlockPos pos) {
        if (level.isClientSide || NATIVE_QUERY_DEPTH.get() != 0 || !loaded(level, pos) || !GreenhouseSettings.enabled()) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.find(level, pos);
            return !geometry.live || !geometry.status.equals("closed") || !geometry.interior.contains(pos.asLong())
                    ? null : airState(level, geometry, cache(level));
        });
    }

    public static boolean consumeRoomOxygen(Level level, BlockPos pos, float millibuckets) {
        if (level.isClientSide || !loaded(level, pos) || !GreenhouseSettings.enabled()) return false;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.find(level, pos);
            if (!geometry.live || !geometry.status.equals("closed") || !geometry.interior.contains(pos.asLong())) return false;
            RoomClimateState state = airState(level, geometry, cache(level));
            if (!state.breathable() || !state.consumeOxygen(millibuckets)) return false;
            invalidateClimate();
            return true;
        });
    }

    public static AtmosphereRegion atmosphereRegionAt(Level level, BlockPos pos) {
        if (level.isClientSide || NATIVE_QUERY_DEPTH.get() != 0 || !loaded(level, pos) || !GreenhouseSettings.enabled()) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.find(level, pos);
            if (!geometry.live || !geometry.status.equals("closed") || !geometry.interior.contains(pos.asLong())) return null;
            Cache cache = cache(level);
            RoomClimateState air = airState(level, geometry, cache);
            Room room = cache.rooms.get(geometry);
            if (room == null || !fresh(room, level.getGameTime())) {
                room = evaluate(level, geometry, cache, level.getGameTime());
                cache.rooms.put(geometry, room);
            }
            return new AtmosphereRegion(geometry.interior, room.inspection.valid() ? room.inspection.targetBiome() : null,
                    air.currentTemperature(), air.oxygenPercent());
        });
    }

    public record AtmosphereRegion(Set<Long> interior, ResourceLocation biome, float temperature, float oxygenPercent) { }

    public static <T> T withoutOverlay(java.util.function.Supplier<T> action) {
        int previous = NATIVE_QUERY_DEPTH.get();
        NATIVE_QUERY_DEPTH.set(previous + 1);
        try {
            return action.get();
        } finally {
            if (previous == 0) NATIVE_QUERY_DEPTH.remove();
            else NATIVE_QUERY_DEPTH.set(previous);
        }
    }

    public static boolean nativeQueryActive() { return NATIVE_QUERY_DEPTH.get() != 0; }

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
        return withoutOverlay(() -> regionOf(roomAt(level, pos), pos));
    }

    private static BiomeRegion regionOf(Room room, BlockPos pos) {
        return room.inspection.valid() && room.interior.contains(pos.asLong()) ? room.region : null;
    }

    public static Inspection inspect(Level level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return result(false, null, CropClimate.NEUTRAL, new Temperature(0, 0), 0, 0, List.of(), "unloaded");
        }
        if (!GreenhouseSettings.enabled()) {
            CropClimate natural = CropClimate.at(nativeBiome(level, pos), pos);
            return result(false, null, natural, new Temperature(0, 0), 0, 0, List.of(), "disabled", natural);
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
        if (level.isClientSide) return CropClimate.at(level.getBiome(pos), pos);
        return inspect(level, pos).climate();
    }

    public static void clearCache() {
        CLIMATE_REVISION.incrementAndGet();
        CACHES.clear();
        GreenhouseRecipes.clearCache();
        GreenhouseRoomScanner.clear();
    }

    /** Energy, setpoint, and active-mode changes keep the already scanned room geometry. */
    public static void invalidateClimate() {
        CLIMATE_REVISION.incrementAndGet();
    }

    private static Room roomAt(Level level, BlockPos pos) {
        return withoutOverlay(() -> findRoomAt(level, pos));
    }

    private static Room findRoomAt(Level level, BlockPos pos) {
        java.util.ArrayList<BlockPos> starts = new java.util.ArrayList<>();
        if (!sealed(level, pos)) starts.add(pos);
        else for (Direction direction : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.DOWN}) {
            BlockPos next = pos.relative(direction);
            if (loaded(level, next) && !sealed(level, next)) starts.add(next);
        }
        Room selected = null;
        for (BlockPos start : starts) {
            var geometry = GreenhouseRoomScanner.find(level, start);
            Room candidate;
            if (!geometry.status.equals("closed") || !geometry.live) {
                candidate = outdoorRoom(level, start, level.getGameTime(), geometry.status);
            } else {
                Cache cache = cache(level);
                candidate = cache.rooms.get(geometry);
                if (candidate == null || !fresh(candidate, level.getGameTime())) {
                    candidate = evaluate(level, geometry, cache, level.getGameTime());
                    cache.rooms.put(geometry, candidate);
                }
            }
            if (selected == null || candidate.inspection.valid() && !selected.inspection.valid()
                    || candidate.inspection.enclosed() && !selected.inspection.enclosed()) selected = candidate;
            if (candidate.inspection.valid()) break;
        }
        return selected != null ? selected : outdoorRoom(level, pos, level.getGameTime(), "open");
    }

    private static boolean fresh(Room room, long now) {
        return room != null && now == room.tick;
    }

    private static Room evaluate(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache, long tick) {
        BlockPos anchor = geometry.anchor;
        RoomClimateState air = airState(level, geometry, cache);
        CropClimate baseClimate = CropClimate.at(nativeBiome(level, anchor), anchor);
        CropClimate actual = ThermoregulatorDetector.northstarClimate(air.currentTemperature());
        MaterialMatches materials = cache.materialMatches.get(geometry);
        if (materials == null || materials.materialRevision != geometry.materialRevision) {
            materials = materialMatches(level, geometry, cache);
            cache.materialMatches.put(geometry, materials);
        }
        List<Match> complete = new java.util.ArrayList<>(materials.complete);
        List<Match> warmEnough = new java.util.ArrayList<>();
        Match closest = materials.closest;
        int roomVolume = geometry.interior.size();
        for (Match match : complete) {
            if (actual == match.definition.recipe.climate()) {
                warmEnough.add(match);
            }
        }
        ResourceLocation nativeId = nativeBiome(level, anchor).unwrapKey().map(biome -> biome.location()).orElse(null);
        java.util.Comparator<Match> specificity = java.util.Comparator
                .comparingInt(Match::totalRequired).reversed()
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
        if (!geometry.glassValid()) status = "no_glass";
        else if (!air.breathable()) status = "filling_air";
        else if (!warmEnough.isEmpty()) status = "valid";
        else if (!complete.isEmpty()) status = "wrong_temperature";
        else status = "missing_specimens";
        // A room with no recognizable specimens has no plausible target to advertise.
        if (chosen != null && chosen.matched == 0) chosen = null;
        ResourceLocation target = chosen == null ? null : chosen.definition.id;
        List<RequirementProgress> progress = chosen == null ? List.of() : chosen.progress;
        List<ResourceLocation> specimens = chosen == null ? List.of() : chosen.definition.recipe.specimens();
        Inspection inspection = new Inspection(true, target,
                actual, 0, 0,
                geometry.glass, chosen == null ? 0 : chosen.matched, chosen == null ? 0 : chosen.totalRequired,
                key(status), specimens, progress, baseClimate, roomVolume);
        return new Room(tick, inspection, geometry.interior, Set.copyOf(aliases));
    }

    private static MaterialMatches materialMatches(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache) {
        List<Match> complete = new java.util.ArrayList<>();
        Match closest = null;
        for (Definition definition : definitions(level, cache)) {
            Match match = match(definition, geometry.counts, geometry.interior.size());
            if (closest == null || compareProgress(match, closest) < 0) closest = match;
            if (match.complete()) complete.add(match);
        }
        return new MaterialMatches(geometry.materialRevision, List.copyOf(complete), closest);
    }

    private static List<Definition> definitions(Level level, Cache cache) {
        if (cache.definitions == null) {
            cache.definitions = level.registryAccess().registryOrThrow(Registries.BIOME).holders()
                    .map(biome -> new Definition(biome.key().location(), GreenhouseRecipes.forServerBiome(biome)))
                    .sorted(java.util.Comparator.comparing(definition -> definition.id.toString())).toList();
        }
        return cache.definitions;
    }

    private static Match match(Definition definition, Map<ResourceLocation, Integer> counts, int roomVolume) {
        int matched = 0;
        int totalRequired = 0;
        boolean complete = true;
        List<RequirementProgress> progress = new java.util.ArrayList<>();
        for (var required : definition.recipe.requirements()) {
            int found = counts.getOrDefault(required.block(), 0);
            int scaled = GreenhouseRecipes.scaledCount(required.count(), roomVolume);
            totalRequired += scaled;
            matched += Math.min(found, scaled);
            complete &= found >= scaled;
            progress.add(new RequirementProgress(required.block(), found, scaled));
        }
        return new Match(definition, matched, complete, List.copyOf(progress), totalRequired);
    }

    private static int compareProgress(Match first, Match second) {
        long a = (long) first.matched * second.totalRequired;
        long b = (long) second.matched * first.totalRequired;
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
        CropClimate natural = CropClimate.at(nativeBiome(level, pos), pos);
        return new Room(tick, result(false, null, natural, new Temperature(0, 0),
                0, 0, List.of(), status, natural), Set.of(), Set.of());
    }

    static boolean sealed(Level level, BlockPos pos) {
        return sealedState(level, pos, level.getBlockState(pos));
    }

    static boolean sealedState(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(BlockStateProperties.OPEN);
        }
        return isGlass(state) || state.getBlock() instanceof FarmBlock || state.getBlock() instanceof DirtPathBlock
                || state.getBlock() instanceof SoulSandBlock || state.isCollisionShapeFullBlock(level, pos);
    }

    static boolean isGlass(BlockState state) {
        return state.is(GLASS) || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS);
    }

    static boolean loaded(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static Cache cache(Level level) {
        Cache cache = CACHES.computeIfAbsent(level, unused -> new Cache());
        long revision = CLIMATE_REVISION.get();
        if (cache.revision != revision) {
            cache.rooms.clear();
            cache.revision = revision;
        }
        return cache;
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
            List<RequirementProgress> requirements, CropClimate baseClimate, int roomVolume) {
        public Inspection {
            specimenBlocks = List.copyOf(specimenBlocks);
            requirements = List.copyOf(requirements);
            if (roomVolume < 0 || roomVolume > 1048576) throw new IllegalArgumentException("Invalid greenhouse room volume");
        }

        public Inspection(boolean enclosed, ResourceLocation targetBiome, CropClimate climate, int heat, int cooling,
                int glass, int specimens, int requiredSpecimens, String status, List<ResourceLocation> specimenBlocks,
                List<RequirementProgress> requirements, CropClimate baseClimate) {
            this(enclosed, targetBiome, climate, heat, cooling, glass, specimens, requiredSpecimens, status,
                    specimenBlocks, requirements, baseClimate, 0);
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
            // Completed geometry already supplies one stable, immutable primitive
            // view. Preserve its identity for local client-region synchronization.
            if (!(interior instanceof it.unimi.dsi.fastutil.longs.LongSet)) interior = Set.copyOf(interior);
        }
    }

    private record Definition(ResourceLocation id, GreenhouseRecipes.Recipe recipe) {
    }

    private record Match(Definition definition, int matched, boolean complete, List<RequirementProgress> progress, int totalRequired) {
    }

    private record MaterialMatches(long materialRevision, List<Match> complete, Match closest) {
    }

    private record Temperature(int heat, int cooling) {
    }

    private record Room(long tick, Inspection inspection, Set<Long> interior, Set<ResourceLocation> aliases, BiomeRegion region) {
        Room(long tick, Inspection inspection, Set<Long> interior, Set<ResourceLocation> aliases) {
            this(tick, inspection, interior, aliases, inspection.valid() ? new BiomeRegion(inspection.targetBiome(), interior) : null);
        }
    }

    private static final class Cache {
        long revision = Long.MIN_VALUE;
        final Map<GreenhouseRoomScanner.Geometry, Room> rooms = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, MaterialMatches> materialMatches = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, RoomClimateState> airStates = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, Long> providerTicks = new java.util.WeakHashMap<>();
        final java.util.LinkedHashMap<GreenhouseRoomScanner.RoomKey, RoomClimateState.Snapshot> retainedAir = new java.util.LinkedHashMap<>();
        List<Definition> definitions;
    }
}
