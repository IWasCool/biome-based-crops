package com.ourcreation.biomebasedcrops;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;

/** Checks a ship's local glass roof against sunlight at its current world pose. */
final class ShipGreenhouseLight {
    private static final Map<Level, Columns> CACHES = Collections.synchronizedMap(new WeakHashMap<>());

    private ShipGreenhouseLight() { }

    static void clear() { CACHES.clear(); }

    static boolean hasTracking(Level level) {
        Columns columns = CACHES.get(level);
        return columns != null && columns.tick == level.getGameTime() && !columns.values.isEmpty();
    }

    static void blockChanged(Level level, BlockPos pos) {
        Columns columns = CACHES.get(level);
        if (columns != null)
            columns.values.remove(new ColumnKey(ShipCropEnvironment.spaceId(level, pos), pos.getX(), pos.getZ()));
    }

    static boolean hasSkyGlassAbove(Level level, BlockPos plant) {
        return hasSkyGlassAbove(level, plant, SableCompanion.INSTANCE);
    }

    static boolean hasSkyGlassAbove(Level level, BlockPos plant, SableCompanion companion) {
        if (!level.dimensionType().hasSkyLight() || !GreenhouseEnvironment.loaded(level, plant)) return false;
        SubLevelAccess ship = companion.getContaining(level, plant);
        if (ship == null) return false;

        Columns columns = CACHES.computeIfAbsent(level, unused -> new Columns());
        if (columns.tick != level.getGameTime()) {
            columns.tick = level.getGameTime();
            columns.values.clear();
        }
        ColumnKey key = new ColumnKey(ship.getUniqueId(), plant.getX(), plant.getZ());
        int bottom = plant.getY() + 1;
        Column column = columns.values.get(key);
        if (column == null || bottom < column.fromY) {
            int glass = Integer.MIN_VALUE, opaque = Integer.MIN_VALUE;
            BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos(plant.getX(), bottom, plant.getZ());
            // A storage heightmap need not describe a moving roof. The local
            // column is bounded by build height, and shared by its crops this tick.
            for (int y = bottom; y < level.getMaxBuildHeight(); y++) {
                sample.setY(y);
                if (!ShipCropEnvironment.sameSpace(level, plant, sample, companion)
                        || !GreenhouseEnvironment.loaded(level, sample)) return false;
                BlockState state = level.getBlockState(sample);
                if (state.isAir() || state.is(BlockTags.LEAVES)) continue;
                if (GreenhouseEnvironment.isGlass(state) && state.getLightBlock(level, sample) == 0) glass = y;
                else if (state.getLightBlock(level, sample) > 0 || state.isCollisionShapeFullBlock(level, sample)) opaque = y;
            }
            column = new Column(bottom, glass, opaque);
            columns.values.put(key, column);
        }
        if (column.highestGlass < bottom || column.highestOpaque >= bottom) return false;

        // Read the current pose even when the local roof was cached. The point
        // just beyond its outer face stays outside the glass when the ship tilts.
        Vector3d exterior = ship.logicalPose().transformPosition(
                new Vector3d(plant.getX() + .5, column.highestGlass + 1.001, plant.getZ() + .5), new Vector3d());
        if (!Double.isFinite(exterior.x) || !Double.isFinite(exterior.y) || !Double.isFinite(exterior.z)) return false;
        BlockPos world = BlockPos.containing(exterior.x, exterior.y, exterior.z);
        if (level.getChunkSource().getChunkNow(world.getX() >> 4, world.getZ() >> 4) == null) return false;
        // Do not load terrain to answer a crop query. Native terrain above the
        // transformed roof blocks sunlight, including caves and world overhangs.
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, world.getX(), world.getZ()) <= exterior.y;
    }

    private record ColumnKey(UUID spaceId, int x, int z) { }
    private record Column(int fromY, int highestGlass, int highestOpaque) { }
    private static final class Columns {
        long tick = Long.MIN_VALUE;
        final Map<ColumnKey, Column> values = new HashMap<>();
    }
}
