package com.ourcreation.biomebasedcrops;

import java.util.Set;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

public final class ThermoregulatorDetector {
    private static final ResourceLocation THERMOREGULATOR_ID = ResourceLocation.fromNamespaceAndPath("toughasnails", "thermoregulator");
    private static final ResourceLocation NORTHSTAR_REGULATOR_ID = ResourceLocation.parse("northstar:temperature_regulator");
    private static final Set<CropClimate> ALL_CLIMATES = Set.of(CropClimate.values());
    private static final AtomicBoolean NORTHSTAR_FAILURE_LOGGED = new AtomicBoolean();
    private static final ClassValue<Optional<NorthstarAccess>> NORTHSTAR_ACCESS = new ClassValue<>() {
        @Override protected Optional<NorthstarAccess> computeValue(Class<?> type) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                if (parent.getName().equals("com.lightning.northstar.block.tech.temperature_regulator.TemperatureRegulatorBlockEntity")) {
                    try {
                        Method sealed = type.getMethod("isSealed", Vec3i.class);
                        Method temperature = type.getMethod("getTemperature");
                        return sealed.getReturnType() == boolean.class && temperature.getReturnType() == float.class
                                ? Optional.of(new NorthstarAccess(sealed, temperature)) : Optional.empty();
                    } catch (ReflectiveOperationException exception) {
                        logNorthstarFailure(exception);
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        }
    };

    private ThermoregulatorDetector() {
    }

    public static boolean canSupportProfile(Level level, BlockPos cropPos, CropProfile profile) {
        return !profile.hasBiomeRule() && hasMatchingThermoregulator(level, cropPos, profile.climates());
    }

    public static boolean hasThermoregulatorNearby(Level level, BlockPos cropPos) {
        return hasMatchingThermoregulator(level, cropPos, ALL_CLIMATES);
    }

    /** Optional active regulator climate, selected by proximity rather than averaged degrees. */
    public static Optional<CropClimate> nearbyClimate(Level level, BlockPos query) {
        if (!Config.SPEC.isLoaded() || !Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()) return Optional.empty();
        int radius = Config.THERMOREGULATOR_RADIUS.get();
        CropClimate selected = null;
        double nearest = radius * radius;
        long chosen = Long.MAX_VALUE;
        for (int chunkX = (query.getX() - radius) >> 4; chunkX <= (query.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (query.getZ() - radius) >> 4; chunkZ <= (query.getZ() + radius) >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity.isRemoved()) continue;
                    BlockPos at = entity.getBlockPos();
                    double distance = at.distSqr(query);
                    if (distance > nearest || distance == nearest && at.asLong() >= chosen) continue;
                    var output = providedClimateAt(level, at, query);
                    if (output.isEmpty() || !AirConditionerAirflow.canReach(level, at, query)) continue;
                    selected = output.get();
                    nearest = distance;
                    chosen = at.asLong();
                }
            }
        }
        return Optional.ofNullable(selected);
    }

    private static boolean hasMatchingThermoregulator(Level level, BlockPos cropPos, Set<CropClimate> climates) {
        if (!Config.SPEC.isLoaded() || !Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()
                || climates.isEmpty()) {
            return false;
        }

        int radius = Config.THERMOREGULATOR_RADIUS.get();
        int minX = cropPos.getX() - radius;
        int maxX = cropPos.getX() + radius;
        int minY = cropPos.getY() - 1;
        int maxY = cropPos.getY() + 3;
        int minZ = cropPos.getZ() - radius;
        int maxZ = cropPos.getZ() + radius;

        // Regulators are block entities. Inspect only loaded chunks instead of reading
        // every block in the volume, which would synchronously load neighboring chunks.
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity.isRemoved() || pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }

                    BlockState state = blockEntity.getBlockState();
                    for (CropClimate climate : climates) {
                        if (supportsClimateAt(level, pos, state, cropPos, climate)) return true;
                    }
                }
            }
        }

        return false;
    }

    /** Only the verified active machine contract may provide a greenhouse climate. */
    public static boolean supportsClimateAt(Level level, BlockPos machinePos, BlockState state, BlockPos query, CropClimate climate) {
        if (!Config.SPEC.isLoaded() || !Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.equals(THERMOREGULATOR_ID)) return supportsClimates(state, Set.of(climate));
        if (!id.equals(NORTHSTAR_REGULATOR_ID)) return false;
        BlockEntity entity = level.getBlockEntity(machinePos);
        if (entity == null || entity.isRemoved()) return false;
        Optional<NorthstarAccess> access = NORTHSTAR_ACCESS.get(entity.getClass());
        if (access.isEmpty()) return false;
        try {
            // Northstar's isSealed includes active kinetic power, no leak, and
            // membership of this exact position in its sealed volume.
            if (!(Boolean) access.get().sealed.invoke(entity, query)) return false;
            float temperature = (Float) access.get().temperature.invoke(entity);
            return Float.isFinite(temperature) && northstarClimate(temperature) == climate;
        } catch (ReflectiveOperationException exception) {
            logNorthstarFailure(exception);
            return false;
        }
    }

    /** Optional API boundary only; gameplay and controls use climate categories. */
    public static CropClimate northstarClimate(float celsius) { return LegacyClimate.fromCelsius(celsius); }

    public static Optional<CropClimate> providedClimateAt(Level level, BlockPos machine, BlockPos query) {
        if (!Config.SPEC.isLoaded() || !Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()) return Optional.empty();
        BlockState state = level.getBlockState(machine);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.equals(THERMOREGULATOR_ID)) {
            if (supportsClimates(state, Set.of(CropClimate.HOT))) return Optional.of(CropClimate.HOT);
            if (supportsClimates(state, Set.of(CropClimate.ICY))) return Optional.of(CropClimate.ICY);
            if (supportsClimates(state, Set.of(CropClimate.NEUTRAL))) return Optional.of(CropClimate.NEUTRAL);
            return Optional.empty();
        }
        if (!id.equals(NORTHSTAR_REGULATOR_ID)) return Optional.empty();
        BlockEntity entity = level.getBlockEntity(machine);
        if (entity == null || entity.isRemoved()) return Optional.empty();
        Optional<NorthstarAccess> access = NORTHSTAR_ACCESS.get(entity.getClass());
        if (access.isEmpty()) return Optional.empty();
        try {
            if (!(Boolean) access.get().sealed.invoke(entity, query)) return Optional.empty();
            float temperature = (Float) access.get().temperature.invoke(entity);
            return Float.isFinite(temperature) ? Optional.of(LegacyClimate.fromCelsius(temperature)) : Optional.empty();
        } catch (ReflectiveOperationException exception) {
            logNorthstarFailure(exception);
            return Optional.empty();
        }
    }

    private static void logNorthstarFailure(ReflectiveOperationException exception) {
        if (NORTHSTAR_FAILURE_LOGGED.compareAndSet(false, true)) {
            BiomeBasedCrops.LOGGER.warn("Could not read Northstar's sealed temperature regulator", exception);
        }
    }

    private record NorthstarAccess(Method sealed, Method temperature) { }

    static boolean supportsClimates(BlockState state, Set<CropClimate> climates) {
        return supportsClimates(state.getOptionalValue(BlockStateProperties.ENABLED).orElse(false),
                booleanState(state, "cooling"), booleanState(state, "heating"), climates);
    }

    private static boolean booleanState(BlockState state, String name) {
        // StateHolder uses identity keys. An independently created property with the
        // same name compares equal but cannot read the actual registered block state.
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property instanceof BooleanProperty booleanProperty && state.getValue(booleanProperty);
    }

    static boolean supportsClimates(boolean enabled, boolean cooling, boolean heating, Set<CropClimate> climates) {
        if (!enabled) {
            return false;
        }

        // TAN consumes fuel into timers and publishes their active modes in these
        // states. Stored fuel alone is not active, and both modes neutralize each other.
        if (!cooling && !heating) {
            return false;
        }
        if (climates.contains(CropClimate.NEUTRAL)) {
            return true;
        }
        if (cooling && heating) {
            return false;
        }
        return cooling && (climates.contains(CropClimate.ICY) || climates.contains(CropClimate.COLD))
                || heating && (climates.contains(CropClimate.WARM) || climates.contains(CropClimate.HOT));
    }
}
