package com.ourcreation.biomebasedcrops;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Local outlet wind; sealed-room sharing is handled by the connected room air state. */
public final class AirConditionerAirflow {
    public static final int WIND_REACH = 4;
    private AirConditionerAirflow() { }

    public static boolean clearOutlet(Level level, BlockPos machine, Direction face) {
        Vec3 start = Vec3.atCenterOf(machine).relative(face, 0.51);
        return visible(level, start, start.relative(face, 0.48));
    }

    /** Local conditioning can grow climate-based crops without an enclosure. */
    public static boolean canSupportProfile(Level level, BlockPos cropPos, CropProfile profile) {
        if (profile.hasBiomeRule()) return false;
        CropClimate climate = climateAt(level, cropPos);
        return climate != null && profile.allows(climate);
    }

    @Nullable public static CropClimate climateAt(Level level, BlockPos query) {
        if (!level.hasChunkAt(query)) return null;
        CropClimate result = null;
        double nearest = Double.POSITIVE_INFINITY;
        long chosen = Long.MAX_VALUE;
        for (int cx = (query.getX() - WIND_REACH) >> 4; cx <= (query.getX() + WIND_REACH) >> 4; cx++) {
            for (int cz = (query.getZ() - WIND_REACH) >> 4; cz <= (query.getZ() + WIND_REACH) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof AirConditionerBlockEntity unit) || !unit.hasPaidAirflow()) continue;
                    BlockPos at = unit.getBlockPos();
                    double distance = at.distSqr(query);
                    if (distance > nearest || distance == nearest && at.asLong() >= chosen) continue;
                    Direction face = unit.getBlockState().getValue(AirConditionerBlock.FACING);
                    if (!canReach(level, at, face, query)) continue;
                    nearest = distance;
                    chosen = at.asLong();
                    result = unit.outletClimate();
                }
            }
        }
        return result;
    }

    /** A four-block horizontal half-disc in front of the outlet, one block above and below. */
    static boolean inOutletStream(BlockPos machine, Direction face, BlockPos query) {
        int dx = query.getX() - machine.getX(), dy = query.getY() - machine.getY(), dz = query.getZ() - machine.getZ();
        int forward = dx * face.getStepX() + dz * face.getStepZ();
        return forward > 0 && Math.abs(dy) <= 1 && (long) dx * dx + (long) dz * dz <= WIND_REACH * WIND_REACH;
    }

    /** Both regulator types use the same clear front outlet and rounded local coverage. */
    public static boolean canReach(Level level, BlockPos machine, Direction face, BlockPos query) {
        return inOutletStream(machine, face, query) && ShipCropEnvironment.sameSpace(level, machine, query)
                && clearOutlet(level, machine, face)
                && visible(level, Vec3.atCenterOf(machine).relative(face, 0.51), Vec3.atCenterOf(query));
    }

    public static boolean canReach(Level level, BlockPos machine, BlockPos query) {
        Vec3 center = Vec3.atCenterOf(machine), end = Vec3.atCenterOf(query);
        Vec3 direction = end.subtract(center);
        if (direction.lengthSqr() == 0) return true;
        double extent = Math.max(Math.abs(direction.x), Math.max(Math.abs(direction.y), Math.abs(direction.z)));
        Vec3 start = center.add(direction.scale(0.51 / extent));
        return visible(level, start, end);
    }

    private static boolean visible(Level level, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++)
            if (!level.hasChunkAt(BlockPos.containing(start.lerp(end, i / (double) steps)))) return false;
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        // Bamboo, cocoa and other plants can have a collider of their own. Their
        // target cell is being conditioned, rather than forming a wall before it.
        return hit.getType() == HitResult.Type.MISS
                || hit.getBlockPos().equals(BlockPos.containing(end))
                && CropMatcher.categoryForState(level.getBlockState(hit.getBlockPos())).isPresent();
    }
}
