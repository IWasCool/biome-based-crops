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
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public final class GreenhouseEnvironment {
    public static final int INTERIOR_BLOCKS_PER_UNIT = 256;
    private static final TagKey<Block> GLASS = tag("greenhouse_glass");
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong CLIMATE_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<Boolean> NATIVE_QUERY = new ThreadLocal<>();
    private static final int MAX_RETAINED_AIR = 256;

    private GreenhouseEnvironment() {
    }

    /** Read the world biome at a crop's current ship pose while bypassing greenhouse query overlays. */
    public static Holder<Biome> nativeBiome(Level level, BlockPos pos) {
        return withoutOverlay(() -> level.getBiome(ShipCropEnvironment.worldPosition(level, pos)));
    }

    /** Controller pricing uses the room's common native baseline without starting a scan. */
    public static CropClimate naturalClimateForController(Level level, BlockPos pos) {
        if (!GreenhouseSettings.enabled()) return naturalClimate(level, controllerFront(level, pos));
        var geometry = controllerGeometry(level, pos, false);
        BlockPos at = geometry == null ? controllerFront(level, pos) : geometry.anchor;
        return naturalClimate(level, at);
    }

    /** Ambient climate at the current world position, without room or outlet conditioning. */
    static CropClimate naturalClimate(Level level, BlockPos pos) {
        BlockPos world = ShipCropEnvironment.worldPosition(level, pos);
        return NorthstarAtmosphere.naturalClimate(level, world).orElseGet(() -> CropClimate.at(nativeBiome(level, pos), world));
    }

    /** Completed geometry only, with optional bounded discovery for a newly placed controller. */
    public static RoomClimateState controllerAirState(Level level, BlockPos pos, boolean discover) {
        if (level.isClientSide || !loaded(level, pos) || !GreenhouseSettings.enabled()) return null;
        return withoutOverlay(() -> {
            var geometry = controllerGeometry(level, pos, discover);
            if (geometry == null) return null;
            CropClimate natural = naturalClimate(level, geometry.anchor);
            RoomCapacity capacity = roomCapacity(level, geometry, natural);
            RoomClimateState air = airState(level, geometry, cache(level), natural, capacity);
            return capacity.sufficient() ? air : null;
        });
    }

    private static BlockPos controllerFront(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(AirConditionerBlock.FACING) ? pos.relative(state.getValue(AirConditionerBlock.FACING)) : pos;
    }

    private static GreenhouseRoomScanner.Geometry controllerGeometry(Level level, BlockPos pos, boolean discover) {
        BlockPos front = controllerFront(level, pos);
        if (!ShipCropEnvironment.sameSpace(level, pos, front) || !loaded(level, front) || sealed(level, front)) return null;
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

    private static boolean servesRoom(GreenhouseRoomScanner.Geometry geometry, AirConditionerBlockEntity unit) {
        if (unit.isRemoved()) return false;
        BlockPos at = unit.getBlockPos();
        BlockState state = unit.getBlockState();
        BlockPos front = state.hasProperty(AirConditionerBlock.FACING) ? at.relative(state.getValue(AirConditionerBlock.FACING)) : at;
        return geometry.interior.contains(front.asLong());
    }

    /** An installed inward-facing regulator marks deliberate biome registration; it need not change the climate. */
    private static boolean hasRegistrationController(Level level, GreenhouseRoomScanner.Geometry geometry) {
        for (long packed : geometry.machines) {
            BlockPos at = BlockPos.of(packed);
            if (!loaded(level, at)) continue;
            var entity = level.getBlockEntity(at);
            if (entity == null || entity.isRemoved()) continue;
            if (entity instanceof AirConditionerBlockEntity unit) {
                if (servesRoom(geometry, unit)) {
                    geometry.bindController(at, level.getGameTime());
                    return true;
                }
                continue;
            }
            BlockState state = entity.getBlockState();
            if (!ThermoregulatorDetector.isExternalController(state)) continue;
            BlockPos front = regulatorFront(at, state);
            if (front != null && loaded(level, front) && geometry.interior.contains(front.asLong())) {
                geometry.bindController(at, level.getGameTime());
                return true;
            }
        }
        return false;
    }

    @Nullable
    static BlockPos regulatorFront(BlockPos at, BlockState state) {
        var facing = state.getBlock().getStateDefinition().getProperty("facing");
        if (!(facing instanceof DirectionProperty direction)) return null;
        Direction face = state.getValue(direction);
        return face.getAxis().isHorizontal() ? at.relative(face) : null;
    }

    private static RoomCapacity roomCapacity(Level level, GreenhouseRoomScanner.Geometry geometry) {
        return roomCapacity(level, geometry, naturalClimate(level, geometry.anchor));
    }

    private static RoomCapacity roomCapacity(Level level, GreenhouseRoomScanner.Geometry geometry, CropClimate natural) {
        int units = 0;
        boolean changeRequested = false;
        for (long packed : geometry.machines) {
            BlockPos at = BlockPos.of(packed);
            if (loaded(level, at) && level.getBlockEntity(at) instanceof AirConditionerBlockEntity unit
                    && servesRoom(geometry, unit)) {
                units++;
                changeRequested |= unit.selectedClimate() != natural;
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
        return airState(level, geometry, cache, nativeClimate, roomCapacity(level, geometry, nativeClimate));
    }

    private static RoomClimateState airState(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache,
                                             CropClimate nativeClimate, RoomCapacity capacity) {
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
        boolean sufficientCapacity = capacity.sufficient();
        for (long packed : geometry.machines) {
            BlockPos at = BlockPos.of(packed);
            if (loaded(level, at) && level.getBlockEntity(at) instanceof AirConditionerBlockEntity unit
                    && (!sufficientCapacity || !servesRoom(geometry, unit))) state.removeDrive(packed);
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
        cache.growingRooms.remove(geometry);
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
                if (geometry.live && geometry.spaceMatches(level) && loaded(level, geometry.anchor)) {
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
        BlockPos roomPosition = nativeQueryActive() ? pos : ShipGreenhouseQueries.roomPosition(level, pos);
        if (!roomPosition.equals(pos)) return currentClimate(level, roomPosition);
        CropClimate natural = loaded(level, pos) ? naturalClimate(level, pos) : CropClimate.NEUTRAL;
        if (!loaded(level, pos) || nativeQueryActive()) return natural;
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
        return localControllerClimate(level, pos, null);
    }

    @Nullable
    private static CropClimate localControllerClimate(Level level, BlockPos pos, @Nullable CropClimate ambient) {
        CropClimate outlet = AirConditionerAirflow.climateAt(level, pos, ambient);
        if (outlet != null) return outlet;
        return withoutOverlay(() -> ThermoregulatorDetector.nearbyClimate(level, pos, ambient).orElse(null));
    }

    private static CropClimate roomClimateAt(Level level, RoomClimateState air, BlockPos pos, long tick) {
        CropClimate controlled = air.controlledClimateAt(pos, tick);
        if (controlled != null) return controlled;
        CropClimate wind = localControllerClimate(level, pos);
        return wind == null ? air.naturalClimate() : wind;
    }

    /** Optional integrations use categories too; Celsius is converted only at their API boundary. */
    @Nullable
    public static CropClimate controlledClimate(Level level, BlockPos pos) {
        return controlledClimate(level, pos, null);
    }

    /** Optional temperature APIs can supply their native weather/altitude/shelter baseline without another query. */
    @Nullable
    public static CropClimate controlledClimate(Level level, BlockPos pos, @Nullable CropClimate ambient) {
        if (nativeQueryActive()) return null;
        if (!level.isClientSide && level.getServer() != null && !level.getServer().isSameThread()) return null;
        pos = ShipGreenhouseQueries.knownRoomPosition(level, pos);
        if (!loaded(level, pos)) return null;
        // Optional APIs may pass a storage-plot baseline; sample ship ambient at its live pose.
        if (ambient != null && ShipCropEnvironment.isOnShip(level, pos)) ambient = null;
        if (!GreenhouseSettings.enabled()) return localControllerClimate(level, pos, ambient);
        if (level.isClientSide) {
            CropClimate climate = GreenhouseClientBiomes.climateAt(level, pos, null);
            return climate != null ? climate : localControllerClimate(level, pos, ambient);
        }
        var air = physicalAirAt(level, pos);
        CropClimate controlled = air == null ? null : air.controlledClimateAt(pos, level.getGameTime());
        return controlled != null ? controlled : localControllerClimate(level, pos, ambient);
    }

    public static float oxygenPercent(Level level, BlockPos pos) {
        BlockPos roomPosition = nativeQueryActive() ? pos : ShipGreenhouseQueries.knownRoomPosition(level, pos);
        if (!roomPosition.equals(pos)) return oxygenPercent(level, roomPosition);
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
        BlockPos roomPosition = nativeQueryActive() ? pos : ShipGreenhouseQueries.knownRoomPosition(level, pos);
        if (!roomPosition.equals(pos)) return greenhouseProvidesOxygen(level, roomPosition);
        if (level.isClientSide) return GreenhouseClientBiomes.hasOxygen(level, pos);
        var air = physicalAirAt(level, pos);
        return air != null && !air.leaking() && air.breathable();
    }

    private static RoomClimateState physicalAirAt(Level level, BlockPos pos) {
        if (level.isClientSide || nativeQueryActive() || !loaded(level, pos) || !GreenhouseSettings.enabled()) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.completedAt(level, pos);
            return geometry == null ? null : airState(level, geometry, cache(level));
        });
    }

    public static boolean consumeRoomOxygen(Level level, BlockPos pos, float millibuckets) {
        if (nativeQueryActive() || level.isClientSide
                || level.getServer() != null && !level.getServer().isSameThread()) return false;
        BlockPos roomPosition = ShipGreenhouseQueries.knownRoomPosition(level, pos);
        if (!roomPosition.equals(pos)) return consumeRoomOxygen(level, roomPosition, millibuckets);
        if (level.isClientSide || !loaded(level, pos) || !GreenhouseSettings.enabled()) return false;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.completedAt(level, pos);
            if (geometry == null) return false;
            RoomClimateState state = airState(level, geometry, cache(level));
            if (state.leaking() || !state.breathable() || !state.consumeOxygen(millibuckets)) return false;
            invalidateClimate();
            return true;
        });
    }

    public static AtmosphereRegion atmosphereRegionAt(Level level, BlockPos pos) {
        BlockPos roomPosition = nativeQueryActive() ? pos : ShipGreenhouseQueries.knownRoomPosition(level, pos);
        if (!roomPosition.equals(pos)) return atmosphereRegionAt(level, roomPosition);
        if (level.isClientSide || nativeQueryActive() || !loaded(level, pos) || !GreenhouseSettings.enabled()) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        return withoutOverlay(() -> {
            var geometry = GreenhouseRoomScanner.completedAt(level, pos);
            if (geometry == null) return null;
            Cache cache = cache(level);
            RoomClimateState air = airState(level, geometry, cache);
            Room room = evaluatedRoom(level, geometry, cache, pos);
            CropClimate actual = roomClimateAt(level, air, pos, level.getGameTime());
            ResourceLocation biome = room.inspection.valid() ? room.inspection.targetBiome() : null;
            long tick = level.getGameTime();
            java.util.function.Predicate<BlockPos> coverage = sample -> roomClimateAt(level, air, sample, tick) == actual;
            return new AtmosphereRegion(pos.immutable(), geometry.interior, biome, actual,
                    air.leaking() ? 0 : air.oxygenPercent(), coverage);
        });
    }

    public record AtmosphereRegion(BlockPos position, Set<Long> interior, ResourceLocation biome, CropClimate climate,
                                   float oxygenPercent, java.util.function.Predicate<BlockPos> coverage) { }

    public static <T> T withoutOverlay(java.util.function.Supplier<T> action) {
        if (nativeQueryActive()) return action.get();
        NATIVE_QUERY.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            NATIVE_QUERY.remove();
        }
    }

    public static boolean nativeQueryActive() { return NATIVE_QUERY.get() == Boolean.TRUE; }

    /** Resolve an exact gameplay position without changing persistent chunk biome palettes. */
    public static Holder<Biome> overlayBiome(Level level, BlockPos pos, Holder<Biome> nativeBiome) {
        if (nativeQueryActive()) return nativeBiome;
        pos = ShipGreenhouseQueries.knownRoomPosition(level, pos);
        Holder<Biome> registered;
        if (level.isClientSide) {
            registered = GreenhouseClientBiomes.registeredBiomeAt(level, pos);
        } else {
            BiomeRegion region = localRegionAt(level, pos);
            registered = region == null ? null
                    : level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(region.biome()).orElse(null);
        }
        if (registered != null) return registered;
        return ShipCropEnvironment.isOnShip(level, pos) ? nativeBiome(level, pos) : nativeBiome;
    }

    /** A valid room's exact air/plant cells, excluding its floor, walls, and roof. */
    public static BiomeRegion regionAt(Level level, BlockPos pos) {
        if (level.isClientSide || nativeQueryActive()) return null;
        return localRegionAt(level, ShipGreenhouseQueries.knownRoomPosition(level, pos));
    }

    private static BiomeRegion localRegionAt(Level level, BlockPos pos) {
        if (!GreenhouseSettings.enabled()) return null;
        if (level.getServer() != null && !level.getServer().isSameThread()) return null;
        var geometry = GreenhouseRoomScanner.completedAt(level, pos);
        if (geometry == null || !loaded(level, pos)) return null;
        return withoutOverlay(() -> {
            if (!hasRegistrationController(level, geometry)) return null;
            Cache cache = cache(level);
            Room room = evaluatedHabitatRoom(level, geometry, cache, pos, cache.rooms);
            return room.inspection.valid() ? room.region : null;
        });
    }

    public static Inspection inspect(Level level, BlockPos pos) {
        BlockPos roomPosition = nativeQueryActive() ? pos : ShipGreenhouseQueries.roomPosition(level, pos);
        if (!roomPosition.equals(pos)) return inspect(level, roomPosition);
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
        BlockPos roomPosition = nativeQueryActive() ? pos : ShipGreenhouseQueries.roomPosition(level, pos);
        if (!roomPosition.equals(pos)) return inspectForBiome(level, roomPosition, target);
        Inspection current = inspect(level, pos);
        if (!current.enclosed() || !GreenhouseSettings.enabled() || level.isClientSide
                || current.status().equals(key("missing_controller"))) return current;
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

    /** Plant habitat checks do not require publishing that habitat as a world biome. */
    public static boolean canSupport(Level level, BlockPos pos, CropProfile profile) {
        if (!loaded(level, pos) || !GreenhouseSettings.enabled()) return false;
        Room room = roomAt(level, pos, false);
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
        ShipGreenhouseLight.clear();
        GreenhouseRecipes.clearCache();
        GreenhouseRoomScanner.clear();
    }

    /** Energy, setpoint, and active-mode changes keep the already scanned room geometry. */
    public static void invalidateClimate() {
        CLIMATE_REVISION.incrementAndGet();
    }

    private static Room roomAt(Level level, BlockPos pos) {
        return roomAt(level, pos, true);
    }

    private static Room roomAt(Level level, BlockPos pos, boolean registrationRequired) {
        return withoutOverlay(() -> findRoomAt(level, pos, registrationRequired));
    }

    private static Room findRoomAt(Level level, BlockPos pos, boolean registrationRequired) {
        java.util.ArrayList<BlockPos> starts = new java.util.ArrayList<>();
        if (!sealed(level, pos)) starts.add(pos);
        else for (Direction direction : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.DOWN}) {
            BlockPos next = pos.relative(direction);
            if (ShipCropEnvironment.sameSpace(level, pos, next) && loaded(level, next) && !sealed(level, next)) starts.add(next);
        }
        Room selected = null;
        for (BlockPos start : starts) {
            var geometry = GreenhouseRoomScanner.find(level, start);
            Room candidate;
            if (!geometry.status.equals("closed") || !geometry.live) {
                candidate = outdoorRoom(level, start, level.getGameTime(), geometry.status);
            } else {
                Cache cache = cache(level);
                candidate = registrationRequired ? evaluatedRoom(level, geometry, cache, start)
                        : evaluatedHabitatRoom(level, geometry, cache, start, cache.growingRooms);
            }
            if (selected == null || candidate.inspection.valid() && !selected.inspection.valid()
                    || candidate.inspection.enclosed() && !selected.inspection.enclosed()) selected = candidate;
            if (candidate.inspection.valid()) break;
        }
        return selected != null ? selected : outdoorRoom(level, pos, level.getGameTime(), "open");
    }

    private static Room evaluatedRoom(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache, BlockPos query) {
        // Ordinary enclosed buildings must not enter biome recipe matching simply because
        // their materials happen to resemble a habitat. Check only the scanned machine index.
        if (!hasRegistrationController(level, geometry)) {
            Room cached = cache.rooms.get(geometry);
            if (cached != null && cached.tick == level.getGameTime() && cached.query == query.asLong()
                    && cached.inspection.status().equals(key("missing_controller"))) return cached;
            CropClimate natural = naturalClimate(level, geometry.anchor);
            Inspection missing = new Inspection(true, null, natural, geometry.glass, 0, 0,
                    key("missing_controller"), List.of(), List.of(), natural, geometry.habitatVolume(),
                    geometry.floorSamples.size(), FloorProgress.none(), geometry.glassProgress());
            Room room = new Room(level.getGameTime(), query.asLong(), missing, geometry.interior);
            cache.rooms.put(geometry, room);
            return room;
        }
        return evaluatedHabitatRoom(level, geometry, cache, query, cache.rooms);
    }

    private static Room evaluatedHabitatRoom(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache,
                                             BlockPos query, Map<GreenhouseRoomScanner.Geometry, Room> rooms) {
        BlockPos worldAnchor = ShipCropEnvironment.worldPosition(level, geometry.anchor);
        Holder<Biome> baseline = withoutOverlay(() -> level.getBiome(worldAnchor));
        CropClimate natural = NorthstarAtmosphere.naturalClimate(level, worldAnchor)
                .orElseGet(() -> CropClimate.at(baseline, worldAnchor));
        RoomCapacity capacity = roomCapacity(level, geometry, natural);
        RoomClimateState air = airState(level, geometry, cache, natural, capacity);
        long tick = level.getGameTime();
        // Cells with the same effective climate share the room evaluation.
        RoomInputs inputs = new RoomInputs(tick, baseline, air.climateAt(query, tick), natural,
                air.breathable(), capacity, geometry.materialRevision,
                geometry.leaking() ? geometry.leakSince() : -1);
        Room room = rooms.get(geometry);
        if (room == null || !inputs.equals(room.inputs)) {
            room = evaluate(level, geometry, cache, query, inputs);
            rooms.put(geometry, room);
        }
        return room;
    }

    private static Room evaluate(Level level, GreenhouseRoomScanner.Geometry geometry, Cache cache, BlockPos query, RoomInputs inputs) {
        BlockPos anchor = geometry.anchor;
        Holder<Biome> nativeBiome = inputs.biome;
        CropClimate baseClimate = CropClimate.at(nativeBiome, anchor);
        CropClimate actual = inputs.climate;
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
        ResourceLocation nativeId = nativeBiome.unwrapKey().map(biome -> biome.location()).orElse(null);
        java.util.Comparator<Match> specificity = java.util.Comparator
                .comparingInt(Match::requiredTotal).reversed()
                .thenComparing(java.util.Comparator.comparingInt((Match match) -> match.definition.recipe.requirements().size()).reversed())
                .thenComparing(match -> !match.definition.id.equals(nativeId))
                .thenComparing(match -> match.definition.id.toString());
        complete.sort(specificity);
        warmEnough.sort(specificity);
        Match chosen = !warmEnough.isEmpty() ? warmEnough.getFirst() : !complete.isEmpty() ? complete.getFirst() : closest;
        String status;
        boolean naturallySupported = actual == inputs.naturalClimate && !warmEnough.isEmpty();
        RoomCapacity capacity = inputs.capacity;
        if (geometry.leakExpired(inputs.tick) && actual == inputs.naturalClimate) status = "open";
        else if (!naturallySupported && capacity.units > 0 && (capacity.changeRequested || actual != inputs.naturalClimate)
                && !capacity.sufficient()
                && !optionalControlMatches(level, geometry, actual)) status = "insufficient_capacity";
        else if (!inputs.breathable) status = "filling_air";
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
        return new Room(inputs.tick, query.asLong(), inspection, geometry.interior, inputs);
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
        if (ShipCropEnvironment.isOnShip(level, plant)) return ShipGreenhouseLight.hasSkyGlassAbove(level, plant);
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
                || state.getBlock() instanceof SoulSandBlock || state.isCollisionShapeFullBlock(level, pos)
                || ThermoregulatorDetector.isExternalController(state);
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
            cache.growingRooms.clear();
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

    private record RoomInputs(long tick, Holder<Biome> biome, CropClimate climate, CropClimate naturalClimate,
                              boolean breathable, RoomCapacity capacity, long materialRevision, long leakSince) { }

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
        // Valid plant habitats must never populate the cache used for world-biome registration.
        final Map<GreenhouseRoomScanner.Geometry, Room> growingRooms = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, MaterialMatches> materialMatches = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, RoomClimateState> airStates = new java.util.WeakHashMap<>();
        final Map<GreenhouseRoomScanner.Geometry, Long> providerTicks = new java.util.WeakHashMap<>();
        final java.util.LinkedHashMap<GreenhouseRoomScanner.RoomKey, RoomClimateState.Snapshot> retainedAir = new java.util.LinkedHashMap<>();
        List<Definition> definitions;
    }
}
