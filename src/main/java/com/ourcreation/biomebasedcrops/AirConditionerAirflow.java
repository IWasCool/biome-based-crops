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

    @Nullable public static CropClimate climateAt(Level level, BlockPos query) {
        if (!GreenhouseSettings.enabled() || !level.hasChunkAt(query)) return null;
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
                    if (!inOutletStream(at, face, query)) continue;
                    Vec3 start = Vec3.atCenterOf(at).relative(face, 0.51);
                    if (!visible(level, start, Vec3.atCenterOf(query))) continue;
                    nearest = distance;
                    chosen = at.asLong();
                    result = unit.outletClimate();
                }
            }
        }
        return result;
    }

    /** A short forward cone, never a proximity aura to the sides or rear. */
    static boolean inOutletStream(BlockPos machine, Direction face, BlockPos query) {
        int dx = query.getX() - machine.getX(), dy = query.getY() - machine.getY(), dz = query.getZ() - machine.getZ();
        int forward = dx * face.getStepX() + dz * face.getStepZ();
        int sideways = dx * face.getStepZ() - dz * face.getStepX();
        return forward > 0 && forward <= WIND_REACH && Math.abs(dy) <= 1
                && Math.abs(sideways) <= 0.5 + forward * 0.5;
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
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }
}
