package com.ourcreation.biomebasedcrops;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

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
    public static final int INTERIOR_BLOCKS_PER_UNIT = 256;
    private static final TagKey<Block> GLASS = tag("greenhouse_glass");
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong CLIMATE_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<Integer> NATIVE_QUERY_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_RETAINED_AIR = 256;

    private GreenhouseEnvironment() {
    }

    /** Read the world biome at a crop's current ship pose while bypassing greenhouse query overlays. */
    public static Holder<Biome> nativeBiome(Level level, BlockPos pos) {
        return withoutOverlay(() -> level.getBiome(ShipCropEnvironment.worldPosition(level, pos)));
    }

    /** Controller pricing uses the room's common native baseline without starting a scan. */
    public static CropClimate naturalClimateForController(Level level, BlockPos pos) {
        if (ShipCropEnvironment.isOnShip(level, pos)) return naturalClimate(level, pos);
        if (!GreenhouseSettings.enabled()) return naturalClimate(level, controllerFront(level, pos));
        var geometry = controllerGeometry(level, pos, false);
        BlockPos at = geometry == null ? controllerFront(level, pos) : geometry.anchor;
        return naturalClimate(level, at);
    }

    private static CropClimate naturalClimate(Level level, BlockPos pos) {
        return NorthstarAtmosphere.naturalClimate(level, pos).orElseGet(() -> CropClimate.at(nativeBiome(level, pos), pos));
    }

    /** Completed geometry only, with optional bounded discovery for a newly placed controller. */
    public static RoomClimateState controllerAirState(Level level, BlockPos pos, boolean discover) {
        if (level.isClientSide || !loaded(level, pos) || !GreenhouseSettings.enabled()
                || ShipCropEnvironment.isOnShip(level, pos)) return null;
        return withoutOverlay(() -> {
            var geometry = controllerGeometry(level, pos, discover);
            if (geometry == null) return null;
            RoomClimateState air = airState(level, geometry, cache(level));
            return roomCapacity(level, geometry).sufficient() ? air : null;
        });
    }

    private static BlockPos controllerFront(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(AirConditionerBlock.FACING) ? pos.relative(state.getValue(AirConditionerBlock.FACING)) : pos;
    }

    private static GreenhouseRoomScanner.Geometry controllerGeometry(Level level, BlockPos pos, boolean discover) {
        BlockPos front = controllerFront(level, pos);
        if (!loaded(level, front) || sealed(level, front)) return null;
        var geometry = GreenhouseRoomScanner.completedAt(level, front);
        if (geometry == null && discover) geometry = GreenhouseRoomScanner.find(level, front);
        return geometry != null && geometry.live && geometry.ready && geometry.status.equals("closed")
                && geometry.interior.contains(front.asLong()) ? geometry : null;
    }

    /** The inlet-side room is irrelevant: inspect only the air cell facing the outlet. */
    public static Inspection controllerInspection(Level level, BlockPos pos) {
        return controllerRoomInfo(level, pos).inspection();
    }

    public static ControllerRoomInfo controllerRoomInfo(Level level, BlockPos pos) {
        if (!loaded(level, pos)) return controllerInfo(result(false, null, CropClimate.NEUTRAL, 0, 0, List.of(), "unloaded"));
        BlockPos front = controllerFront(level, pos);
        if (!loaded(level, front)) return controllerInfo(result(false, null, CropClimate.NEUTRAL, 0, 0, List.of(), "unloaded"));
        if (ShipCropEnvironment.isOnShip(level, pos))
            return controllerInfo(result(false, null, naturalClimate(level, pos), 0, 0, List.of(),
                    sealed(level, front) ? "blocked_outlet" : "outside"));
        if (!GreenhouseSettings.enabled()) return controllerInfo(inspect(level, front));
        if (sealed(level, front)) return controllerInfo(result(false, null, naturalClimate(level, front), 0, 0, List.of(), "blocked_outlet"));
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.findForControllerInspection(level, front);
            if (!geometry.live || !geometry.ready || !geometry.status.equals("closed"))
                return controllerInfo(outdoorRoom(level, front, level.getGameTime(), geometry.status).inspection);
            RoomCapacity capacity = roomCapacity(level, geometry);
            Inspection inspection = evaluatedRoom(level, geometry, cache(level), front).inspection;
            if (!capacity.changeRequested && inspection.climate() == naturalClimate(level, geometry.anchor)
                    || inspection.valid() && (inspection.climate() == naturalClimate(level, geometry.anchor)
                    || optionalControlMatches(level, geometry, inspection.climate())))
                return new ControllerRoomInfo(inspection, capacity.units, 0, Math.max(capacity.interiorCapacity, inspection.roomVolume()));
            return new ControllerRoomInfo(inspection, capacity.units, capacity.required, capacity.interiorCapacity);
        });
    }

    private static ControllerRoomInfo controllerInfo(Inspection inspection) {
        return new ControllerRoomInfo(inspection, 0, 0, INTERIOR_BLOCKS_PER_UNIT);
    }

    public record ControllerRoomInfo(Inspection inspection, int installedUnits, int requiredUnits, int interiorCapacity) { }

    private static boolean servesRoom(Level level, GreenhouseRoomScanner.Geometry geometry, BlockPos machine) {
        return loaded(level, machine) && level.getBlockEntity(machine) instanceof AirConditionerBlockEntity unit
                && !unit.isRemoved() && geometry.interior.contains(controllerFront(level, machine).asLong());
    }

    private static RoomCapacity roomCapacity(Level level, GreenhouseRoomScanner.Geometry geometry) {
        int units = 0;
        boolean changeRequested = false;
        CropClimate natural = naturalClimate(level, geometry.anchor);
        for (long packed : geometry.machines) {
            BlockPos at = BlockPos.of(packed);
            if (servesRoom(level, geometry, at)) {
                units++;
                changeRequested |= ((AirConditionerBlockEntity) level.getBlockEntity(at)).selectedClimate() != natural;
            }
        }
        int volume = geometry.habitatVolume();
        int required = volume <= INTERIOR_BLOCKS_PER_UNIT ? 0
                : (volume + INTERIOR_BLOCKS_PER_UNIT - 1) / INTERIOR_BLOCKS_PER_UNIT;
        int capacity = (int) Math.min(Integer.MAX_VALUE, (long) INTERIOR_BLOCKS_PER_UNIT * Math.max(1, units));
        return new RoomCapacity(units, required, capacity, changeRequested);
    }

    private record RoomCapacity(int units, int required, int interiorCapacity, boolean changeRequested) {
        boolean sufficient() { return required <= units; }
    }

    private static boolean optionalControlMatches(Level level, GreenhouseRoomScanner.Geometry geometry, CropClimate climate) {
        for (long packed : geometry.machines) {
            BlockPos at = BlockPos.of(packed);
            if (loaded(level, at) && !(level.getBlockEntity(at) instanceof AirConditionerBlockEntity)
                    && ThermoregulatorDetector.providedClimateAt(level, at, geometry.anchor).orElse(null) == climate) return true;
        }
        return false;
    }

    private static RoomClimateState airState(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache) {
        CropClimate nativeClimate = naturalClimate(level, geometry.anchor);
        boolean breathable = NorthstarAtmosphere.naturalAtmosphereBreathable(level)
                || NorthstarAtmosphere.nativeProviderBreathable(level, geometry.anchor);
        RoomClimateState state = cache.airStates.get(geometry);
        if (state == null) {
            RoomClimateState.Snapshot retained = cache.retainedAir.remove(geometry.key);
            state = retained == null ? new RoomClimateState(geometry.interior.size(), nativeClimate, breathable, level.getGameTime())
                    : RoomClimateState.restore(retained, level.getGameTime(), nativeClimate, breathable);
            cache.airStates.put(geometry, state);
        }
        state.setLeakStart(geometry.leaking() ? geometry.leakSince() : -1, level.getGameTime());
        state.advance(level.getGameTime(), nativeClimate, breathable, geometry.live);
        boolean sufficientCapacity = roomCapacity(level, geometry).sufficient();
        for (long packed : geometry.machines) {
            BlockPos at = BlockPos.of(packed);
            if (loaded(level, at) && level.getBlockEntity(at) instanceof AirConditionerBlockEntity
                    && (!sufficientCapacity || !servesRoom(level, geometry, at))) state.removeDrive(packed);
        }
        Long providersTick = cache.providerTicks.get(geometry);
        if (providersTick == null || providersTick != level.getGameTime()) {
            cache.providerTicks.put(geometry, level.getGameTime());
            for (long packed : geometry.machines) {
                BlockPos at = BlockPos.of(packed);
                if (!loaded(level, at) || level.getBlockEntity(at) instanceof AirConditionerBlockEntity) continue;
                var output = ThermoregulatorDetector.providedClimateAt(level, at, geometry.anchor);
                if (output.isPresent()) state.addPaidDrive(packed, level.getGameTime(), output.get(), false);
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
        state.advance(level.getGameTime(), state.naturalClimate(), state.naturalBreathable(), true);
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
                if (geometry.live && loaded(level, geometry.anchor)) {
                    RoomClimateState air = airState(level, geometry, cache);
                    if (air.leakDissipated(level.getGameTime())) GreenhouseRoomScanner.expireLeak(level, geometry);
                }
                else {
                    RoomClimateState air = entry.getValue();
                    air.advance(level.getGameTime(), air.naturalClimate(), air.naturalBreathable(), false);
                }
            }
            return null;
        });
    }

    /** Actual climate at this exact position; controller settings are never a growth bypass. */
    public static CropClimate currentClimate(Level level, BlockPos pos) {
        CropClimate natural = loaded(level, pos) ? naturalClimate(level, pos) : CropClimate.NEUTRAL;
        if (!loaded(level, pos)) return natural;
        if (ShipCropEnvironment.isOnShip(level, pos)) {
            if (nativeQueryActive()) return natural;
            CropClimate wind = localControllerClimate(level, pos);
            return wind == null ? natural : wind;
        }
        if (level.isClientSide) {
            CropClimate room = GreenhouseClientBiomes.climateAt(level, pos, null);
            if (room != null) return room;
        } else {
            var air = physicalAirAt(level, pos);
            if (air != null) return roomClimateAt(level, air, pos, level.getGameTime());
        }
        if (nativeQueryActive()) return natural;
        CropClimate local = localControllerClimate(level, pos);
        return local == null ? natural : local;
    }

    @Nullable
    private static CropClimate localControllerClimate(Level level, BlockPos pos) {
        CropClimate outlet = AirConditionerAirflow.climateAt(level, pos);
        if (outlet != null) return outlet;
        return withoutOverlay(() -> ThermoregulatorDetector.nearbyClimate(level, pos).orElse(null));
    }

    private static CropClimate roomClimateAt(Level level, RoomClimateState air, BlockPos pos, long tick) {
        if (air.hasControlAt(pos, tick)) return air.climateAt(pos, tick);
        CropClimate wind = localControllerClimate(level, pos);
        return wind == null ? air.naturalClimate() : wind;
    }

    /** Optional integrations use categories too; Celsius is converted only at their API boundary. */
    @Nullable
    public static CropClimate controlledClimate(Level level, BlockPos pos) {
        if (nativeQueryActive() || !loaded(level, pos)) return null;
        if (!level.isClientSide && level.getServer() != null && !level.getServer().isSameThread()) return null;
        if (ShipCropEnvironment.isOnShip(level, pos) || !GreenhouseSettings.enabled()) return localControllerClimate(level, pos);
        if (level.isClientSide) {
            CropClimate climate = GreenhouseClientBiomes.climateAt(level, pos, null);
            return climate != null ? climate : localControllerClimate(level, pos);
        }
        var air = physicalAirAt(level, pos);
        return air != null && air.hasControlAt(pos, level.getGameTime())
                ? air.climateAt(pos, level.getGameTime()) : localControllerClimate(level, pos);
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
        return air != null && !air.leaking() && air.breathable();
    }

    private static RoomClimateState physicalAirAt(Level level, BlockPos pos) {
        if (level.isClientSide || NATIVE_QUERY_DEPTH.get() != 0 || !loaded(level, pos) || !GreenhouseSettings.enabled()) return null;
        if (ShipCropEnvironment.isOnShip(level, pos)) return null;
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
            if (state.leaking() || !state.breathable() || !state.consumeOxygen(millibuckets)) return false;
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
            Room room = evaluatedRoom(level, geometry, cache, pos);
            CropClimate actual = roomClimateAt(level, air, pos, level.getGameTime());
            ResourceLocation biome = room.inspection.valid() ? room.inspection.targetBiome() : null;
            long tick = level.getGameTime();
            java.util.function.Predicate<BlockPos> coverage = sample -> roomClimateAt(level, air, sample, tick) == actual;
            return new AtmosphereRegion(geometry.interior, biome, actual, air.leaking() ? 0 : air.oxygenPercent(), coverage);
        });
    }

    public record AtmosphereRegion(Set<Long> interior, ResourceLocation biome, CropClimate climate,
                                   float oxygenPercent, java.util.function.Predicate<BlockPos> coverage) { }

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
        if (ShipCropEnvironment.isOnShip(level, pos)) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        return withoutOverlay(() -> regionOf(roomAt(level, pos), pos));
    }

    private static BiomeRegion regionOf(Room room, BlockPos pos) {
        return room.inspection.valid() && room.interior.contains(pos.asLong()) ? room.region : null;
    }

    public static Inspection inspect(Level level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return result(false, null, CropClimate.NEUTRAL, 0, 0, List.of(), "unloaded");
        }
        if (!GreenhouseSettings.enabled()) {
            CropClimate natural = CropClimate.at(nativeBiome(level, pos), pos);
            return result(false, null, natural, 0, 0, List.of(), "disabled", natural);
        }
        return roomAt(level, pos).inspection;
    }

    /** Planning a different biome reports its deficits without changing the room's actual biome. */
    public static Inspection inspectForBiome(Level level, BlockPos pos, ResourceLocation target) {
        Inspection current = inspect(level, pos);
        if (!current.enclosed() || !GreenhouseSettings.enabled() || level.isClientSide) return current;
        var geometry = GreenhouseRoomScanner.completedAroundController(level, pos);
        var recipe = GreenhouseRecipes.serverCatalog(level.registryAccess().registryOrThrow(Registries.BIOME)).get(target);
        if (geometry == null || recipe == null) return current;
        Match requested = match(new Definition(target, recipe), geometry);
        String status;
        if (current.status().equals(key("filling_air"))) status = "filling_air";
        else if (requested.floor.found() < requested.floor.required()) status = "missing_floor";
        else if (!requested.complete()) status = "missing_specimens";
        else if (recipe.climate() != current.climate()) status = "wrong_climate";
        else if (!current.valid() || !target.equals(current.targetBiome())) status = "ambiguous";
        else status = geometry.leaking() ? "leaking" : "valid";
        return new Inspection(true, target, current.climate(), geometry.glass,
                requested.matched, requested.totalRequired, key(status), recipe.specimens(), requested.progress,
                current.baseClimate(), current.roomVolume(), current.floorArea(), requested.floor, geometry.glassProgress());
    }

    /** Exact biome rules use only the single biome recognized inside this greenhouse. */
    public static boolean canSupport(Level level, BlockPos pos, CropProfile profile) {
        if (!loaded(level, pos) || !GreenhouseSettings.enabled() || ShipCropEnvironment.isOnShip(level, pos)) return false;
        Room room = roomAt(level, pos);
        if (!hasGrowingLight(level, pos) || !room.inspection.valid() || !room.interior.contains(pos.asLong())) return false;
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        return biomes.getHolder(room.inspection.targetBiome()).map(biome -> profile.allows(biome, pos)).orElse(false);
    }

    public static CropClimate effectiveClimate(Level level, BlockPos pos) {
        if (!loaded(level, pos)) return CropClimate.NEUTRAL;
        return currentClimate(level, pos);
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
                candidate = evaluatedRoom(level, geometry, cache, start);
            }
            if (selected == null || candidate.inspection.valid() && !selected.inspection.valid()
                    || candidate.inspection.enclosed() && !selected.inspection.enclosed()) selected = candidate;
            if (candidate.inspection.valid()) break;
        }
        return selected != null ? selected : outdoorRoom(level, pos, level.getGameTime(), "open");
    }

    private static Room evaluatedRoom(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache, BlockPos query) {
        RoomClimateState air = airState(level, geometry, cache);
        RoomInputs inputs = new RoomInputs(level.getGameTime(), query.asLong(), air.climateAt(query, level.getGameTime()),
                air.breathable(), roomCapacity(level, geometry), geometry.materialRevision,
                geometry.leaking() ? geometry.leakSince() : -1);
        Room room = cache.rooms.get(geometry);
        if (room == null || !inputs.equals(room.inputs)) {
            room = evaluate(level, geometry, cache, level.getGameTime(), query, inputs);
            cache.rooms.put(geometry, room);
        }
        return room;
    }

    private static Room evaluate(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache, long tick, BlockPos query, RoomInputs inputs) {
        BlockPos anchor = geometry.anchor;
        RoomClimateState air = airState(level, geometry, cache);
        CropClimate baseClimate = CropClimate.at(nativeBiome(level, anchor), anchor);
        CropClimate actual = air.climateAt(query, tick);
        MaterialMatches materials = cache.materialMatches.get(geometry);
        if (materials == null || materials.materialRevision != geometry.materialRevision) {
            materials = materialMatches(level, geometry, cache);
            cache.materialMatches.put(geometry, materials);
        }
        List<Match> complete = new java.util.ArrayList<>(materials.complete);
        List<Match> warmEnough = new java.util.ArrayList<>();
        Match closest = materials.closest;
        int roomVolume = geometry.habitatVolume();
        for (Match match : complete) {
            if (actual == match.definition.recipe.climate()) {
                warmEnough.add(match);
            }
        }
        ResourceLocation nativeId = nativeBiome(level, anchor).unwrapKey().map(biome -> biome.location()).orElse(null);
        java.util.Comparator<Match> specificity = java.util.Comparator
                .comparingInt(Match::requiredTotal).reversed()
                .thenComparing(java.util.Comparator.comparingInt((Match match) -> match.definition.recipe.requirements().size()).reversed())
                .thenComparing(match -> !match.definition.id.equals(nativeId))
                .thenComparing(match -> match.definition.id.toString());
        complete.sort(specificity);
        warmEnough.sort(specificity);
        Match chosen = !warmEnough.isEmpty() ? warmEnough.getFirst() : !complete.isEmpty() ? complete.getFirst() : closest;
        String status;
        boolean naturallySupported = actual == air.naturalClimate() && !warmEnough.isEmpty();
        RoomCapacity capacity = inputs.capacity;
        if (geometry.leakExpired(tick) && actual == air.naturalClimate()) status = "open";
        else if (!naturallySupported && capacity.units > 0 && (capacity.changeRequested || actual != air.naturalClimate())
                && !capacity.sufficient()
                && !optionalControlMatches(level, geometry, actual)) status = "insufficient_capacity";
        else if (!air.breathable()) status = "filling_air";
        else if (warmEnough.size() > 1) status = "ambiguous";
        else if (!warmEnough.isEmpty()) status = geometry.leaking() ? "leaking" : "valid";
        else if (chosen != null && chosen.floor.found() < chosen.floor.required()) status = "missing_floor";
        else if (!complete.isEmpty()) status = "wrong_climate";
        else status = "missing_specimens";
        // A room with no recognizable specimens has no plausible target to advertise.
        if (chosen != null && chosen.matched == 0 && chosen.floor.found() == 0) chosen = null;
        ResourceLocation target = chosen == null ? null : chosen.definition.id;
        List<RequirementProgress> progress = chosen == null ? List.of() : chosen.progress;
        List<ResourceLocation> specimens = chosen == null ? List.of() : chosen.definition.recipe.specimens();
        Inspection inspection = new Inspection(true, target,
                actual,
                geometry.glass, chosen == null ? 0 : chosen.matched, chosen == null ? 0 : chosen.totalRequired,
                key(status), specimens, progress, baseClimate, roomVolume, geometry.floorSamples.size(),
                chosen == null ? FloorProgress.none() : chosen.floor, geometry.glassProgress());
        return new Room(tick, query.asLong(), inspection, geometry.interior, inputs);
    }

    private static MaterialMatches materialMatches(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache) {
        List<Match> complete = new java.util.ArrayList<>();
        Match closest = null;
        for (Definition definition : definitions(level, cache)) {
            Match match = match(definition, geometry);
            if (closest == null || compareProgress(match, closest) < 0) closest = match;
            if (match.complete()) complete.add(match);
        }
        // Extra identifying vegetation can refine a simpler habitat (for example birch forest into
        // old-growth birch forest). Separate complete layouts and identical overrides remain ambiguous.
        List<Match> specific = complete.stream().filter(candidate -> complete.stream().noneMatch(other ->
                candidate.definition.recipe.climate() == other.definition.recipe.climate()
                        && other.definition.recipe.specimens().size() > candidate.definition.recipe.specimens().size()
                        && other.definition.recipe.specimens().containsAll(candidate.definition.recipe.specimens()))).toList();
        return new MaterialMatches(geometry.materialRevision, specific, closest);
    }

    private static List<Definition> definitions(Level level, Cache cache) {
        if (cache.definitions == null) {
            cache.definitions = GreenhouseRecipes.serverCatalog(level.registryAccess().registryOrThrow(Registries.BIOME))
                    .entrySet().stream().map(entry -> new Definition(entry.getKey(), entry.getValue()))
                    .sorted(java.util.Comparator.comparing(definition -> definition.id.toString())).toList();
        }
        return cache.definitions;
    }

    private static Match match(Definition definition, GreenhouseRoomScanner.Geometry geometry) {
        int matched = 0;
        int totalRequired = 0;
        boolean complete = true;
        List<RequirementProgress> progress = new java.util.ArrayList<>();
        for (var required : definition.recipe.requirements()) {
            Map<ResourceLocation, Integer> counts = required.minHeight() == 2 ? geometry.canopy
                    : required.minHeight() == 1 ? geometry.features
                    : geometry.inside;
            int found = counts.getOrDefault(required.block(), 0);
            int scaled = GreenhouseRecipes.scaledCount(required, geometry.habitatVolume(), geometry.floorSamples.size(),
                    GreenhouseSettings.materialReferenceVolume());
            totalRequired += scaled;
            matched += Math.min(found, scaled);
            complete &= found >= scaled;
            progress.add(new RequirementProgress(required.block(), found, scaled));
        }
        var floor = definition.recipe.floor();
        int floorFound = floor.blocks().stream().mapToInt(block -> geometry.floorCounts.getOrDefault(block, 0)).sum();
        for (var material : definition.recipe.requirements()) {
            if (floor.blocks().contains(material.block())) continue;
            Map<ResourceLocation, Integer> bases = geometry.sceneryBases.get(material.block());
            if (bases != null) floorFound += floor.blocks().stream().mapToInt(block -> bases.getOrDefault(block, 0)).sum();
        }
        int floorRequired = floor.required(geometry.floorSamples.size());
        FloorProgress floorProgress = new FloorProgress(floor.blocks(), floorFound, floorRequired, geometry.floorSamples.size());
        return new Match(definition, matched, complete && floorFound >= floorRequired, List.copyOf(progress), totalRequired, floorProgress);
    }

    private static int compareProgress(Match first, Match second) {
        long a = (long) first.matchedTotal() * second.requiredTotal();
        long b = (long) second.matchedTotal() * first.requiredTotal();
        if (a != b) return Long.compare(b, a);
        if (first.matched != second.matched) return Integer.compare(second.matched, first.matched);
        int distinct = Integer.compare(second.definition.recipe.requirements().size(), first.definition.recipe.requirements().size());
        return distinct != 0 ? distinct : first.definition.id.toString().compareTo(second.definition.id.toString());
    }

    private static Room outdoorRoom(Level level, BlockPos pos, long tick, String status) {
        CropClimate natural = CropClimate.at(nativeBiome(level, pos), pos);
        return new Room(tick, pos.asLong(), result(false, null, natural,
                0, 0, List.of(), status, natural), Set.of());
    }

    /** Sky-exposed glass, including a brief opening in a known glass roof. */
    public static boolean hasGrowingLight(Level level, BlockPos plant) {
        if (hasSkyGlassAbove(level, plant)) return true;
        if (level.isClientSide || !loaded(level, plant) || nativeQueryActive()) return false;
        var geometry = GreenhouseRoomScanner.completedAt(level, plant);
        return geometry != null && geometry.leaking()
                && !geometry.leakExpired(level.getGameTime())
                && geometry.hasRoofOpeningAbove(plant);
    }

    /** A transparent glass covering must have an unobstructed column all the way to the sky. */
    public static boolean hasSkyGlassAbove(Level level, BlockPos plant) {
        if (!loaded(level, plant) || !level.dimensionType().hasSkyLight()) return false;
        Cache cache = cache(level);
        if (cache.skyTick != level.getGameTime()) {
            cache.skyTick = level.getGameTime();
            cache.skyColumns.clear();
        }
        long key = BlockPos.asLong(plant.getX(), 0, plant.getZ());
        int bottom = plant.getY() + 1;
        SkyColumn column = cache.skyColumns.get(key);
        if (column == null || bottom < column.fromY) {
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    plant.getX(), plant.getZ());
            int glass = Integer.MIN_VALUE, opaque = Integer.MIN_VALUE;
            BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos(plant.getX(), bottom, plant.getZ());
            for (int y = bottom; y < top; y++) {
                sample.setY(y);
                if (!loaded(level, sample)) return false;
                BlockState state = level.getBlockState(sample);
                if (state.isAir() || state.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                if (isGlass(state) && state.getLightBlock(level, sample) == 0) glass = y;
                else if (state.getLightBlock(level, sample) > 0 || state.isCollisionShapeFullBlock(level, sample)) opaque = y;
            }
            column = new SkyColumn(bottom, glass, opaque);
            cache.skyColumns.put(key, column);
        }
        return column.highestGlass >= bottom && column.highestOpaque < bottom;
    }

    private record SkyColumn(int fromY, int highestGlass, int highestOpaque) { }

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
            cache.skyColumns.clear();
            cache.revision = revision;
        }
        return cache;
    }

    private static Inspection result(boolean enclosed, ResourceLocation target, CropClimate climate,
            int glass, int specimens, List<ResourceLocation> materials, String status) {
        return result(enclosed, target, climate, glass, specimens, materials, status, climate);
    }

    private static Inspection result(boolean enclosed, ResourceLocation target, CropClimate climate,
            int glass, int specimens, List<ResourceLocation> materials, String status, CropClimate baseClimate) {
        return new Inspection(enclosed, target, climate, glass, specimens,
                GreenhouseSettings.requiredSpecimens(), key(status), materials, List.of(), baseClimate, 0, 0, FloorProgress.none(), GlassProgress.none());
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, path));
    }

    private static String key(String status) {
        return "biome_based_crops.greenhouse.status." + status;
    }

    public record Inspection(boolean enclosed, ResourceLocation targetBiome, CropClimate climate,
            int glass, int specimens, int requiredSpecimens, String status, List<ResourceLocation> specimenBlocks,
            List<RequirementProgress> requirements, CropClimate baseClimate, int roomVolume, int floorArea, FloorProgress floor,
            GlassProgress glazing) {
        public Inspection {
            specimenBlocks = List.copyOf(specimenBlocks);
            requirements = List.copyOf(requirements);
            if (roomVolume < 0 || roomVolume > 1048576) throw new IllegalArgumentException("Invalid greenhouse room volume");
            if (floorArea < 0 || floorArea > 1048576) throw new IllegalArgumentException("Invalid greenhouse floor area");
            java.util.Objects.requireNonNull(floor, "floor");
            java.util.Objects.requireNonNull(glazing, "glazing");
        }

        public boolean valid() {
            return status.equals(key("valid")) || status.equals(key("leaking"));
        }
    }

    public record RequirementProgress(ResourceLocation block, int found, int required) {
        public int missing() { return Math.max(0, required - found); }
    }

    public record FloorProgress(List<ResourceLocation> blocks, int found, int required, int total) {
        public FloorProgress { blocks = List.copyOf(blocks); }
        public static FloorProgress none() { return new FloorProgress(List.of(), 0, 0, 0); }
        public int missing() { return Math.max(0, required - found); }
    }

    public record GlassProgress(int roofFound, int roofRequired, int wallFound, int wallRequired,
                                int wallBlocks, int entrances, int devices) {
        public GlassProgress {
            if (roofFound < 0 || roofRequired < 0 || wallFound < 0 || wallRequired < 0
                    || wallBlocks < 0 || entrances < 0 || devices < 0) throw new IllegalArgumentException("Negative glass progress");
        }
        public static GlassProgress none() { return new GlassProgress(0, 0, 0, 0, 0, 0, 0); }
        public int roofMissing() { return Math.max(0, roofRequired - roofFound); }
        public int wallMissing() { return Math.max(0, wallRequired - wallFound); }
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

    private record Match(Definition definition, int matched, boolean complete, List<RequirementProgress> progress, int totalRequired,
                         FloorProgress floor) {
        int matchedTotal() { return matched + Math.min(floor.found(), floor.required()); }
        int requiredTotal() { return totalRequired + floor.required(); }
    }

    private record MaterialMatches(long materialRevision, List<Match> complete, Match closest) {
    }

    private record RoomInputs(long tick, long query, CropClimate climate, boolean breathable, RoomCapacity capacity,
                              long materialRevision, long leakSince) { }

    private record Room(long tick, long query, Inspection inspection, Set<Long> interior, RoomInputs inputs, BiomeRegion region) {
        Room(long tick, long query, Inspection inspection, Set<Long> interior) {
            this(tick, query, inspection, interior, null);
        }
        Room(long tick, long query, Inspection inspection, Set<Long> interior, RoomInputs inputs) {
            this(tick, query, inspection, interior, inputs, inspection.valid() ? new BiomeRegion(inspection.targetBiome(), interior) : null);
        }
    }

    private static final class Cache {
        long revision = Long.MIN_VALUE;
        long skyTick = Long.MIN_VALUE;
        final Map<Long, SkyColumn> skyColumns = new java.util.HashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, Room> rooms = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, MaterialMatches> materialMatches = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, RoomClimateState> airStates = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, Long> providerTicks = new java.util.WeakHashMap<>();
        final java.util.LinkedHashMap<GreenhouseRoomScanner.RoomKey, RoomClimateState.Snapshot> retainedAir = new java.util.LinkedHashMap<>();
        List<Definition> definitions;
    }
}
