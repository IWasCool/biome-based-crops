package com.ourcreation.biomebasedcrops;

import java.util.Set;

import net.minecraft.core.BlockPos;
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
    private static final Set<CropClimate> ALL_CLIMATES = Set.of(CropClimate.values());

    private ThermoregulatorDetector() {
    }

    public static boolean canSupportProfile(Level level, BlockPos cropPos, CropProfile profile) {
        return !profile.hasBiomeRule() && hasMatchingThermoregulator(level, cropPos, profile.climates());
    }

    public static boolean hasThermoregulatorNearby(Level level, BlockPos cropPos) {
        return hasMatchingThermoregulator(level, cropPos, ALL_CLIMATES);
    }

    private static boolean hasMatchingThermoregulator(Level level, BlockPos cropPos, Set<CropClimate> climates) {
        if (!Config.SPEC.isLoaded() || !ModList.get().isLoaded("toughasnails") || !Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()
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
        Block thermoregulator = BuiltInRegistries.BLOCK.get(THERMOREGULATOR_ID);

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
                    if (state.is(thermoregulator) && supportsClimates(state, climates)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

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
