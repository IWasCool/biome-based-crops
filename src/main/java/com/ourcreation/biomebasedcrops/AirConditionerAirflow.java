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
    // At most three intermediate climates separate a controller's target from ambient.
    public static final int INFLUENCE_REACH = WIND_REACH + CropClimate.values().length - 2;
    private static final CropClimate[] CLIMATES = CropClimate.values();
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
        return climateAt(level, query, null);
    }

    @Nullable public static CropClimate climateAt(Level level, BlockPos query, @Nullable CropClimate ambient) {
        if (!level.hasChunkAt(query)) return null;
        CropClimate result = null;
        double nearest = Double.POSITIVE_INFINITY;
        long chosen = Long.MAX_VALUE;
        for (int cx = (query.getX() - INFLUENCE_REACH) >> 4; cx <= (query.getX() + INFLUENCE_REACH) >> 4; cx++) {
            for (int cz = (query.getZ() - INFLUENCE_REACH) >> 4; cz <= (query.getZ() + INFLUENCE_REACH) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof AirConditionerBlockEntity unit) || !unit.hasPaidAirflow()) continue;
                    BlockPos at = unit.getBlockPos();
                    double distance = at.distSqr(query);
                    if (distance > nearest || distance == nearest && at.asLong() >= chosen) continue;
                    Direction face = unit.getBlockState().getValue(AirConditionerBlock.FACING);
                    if (!inOutletArea(at, face, query, INFLUENCE_REACH)) continue;
                    CropClimate climate = conditionedClimateAt(level, at, query, unit.outletClimate(), ambient);
                    if (climate == null) continue;
                    if (!canReach(level, at, face, query)) continue;
                    nearest = distance;
                    chosen = at.asLong();
                    result = climate;
                }
            }
        }
        return result;
    }

    /** A four-block horizontal half-disc in front of the outlet, one block above and below. */
    static boolean inOutletStream(BlockPos machine, Direction face, BlockPos query) {
        return inOutletArea(machine, face, query, WIND_REACH);
    }

    private static boolean inOutletArea(BlockPos machine, Direction face, BlockPos query, int reach) {
        int dx = query.getX() - machine.getX(), dy = query.getY() - machine.getY(), dz = query.getZ() - machine.getZ();
        int forward = dx * face.getStepX() + dz * face.getStepZ();
        return forward > 0 && Math.abs(dy) <= 1 && (long) dx * dx + (long) dz * dz <= reach * reach;
    }

    /** Each one-block annulus outside the full-strength core moves one category toward ambient. */
    static CropClimate falloffClimate(CropClimate target, CropClimate natural, BlockPos machine, BlockPos query) {
        int difference = natural.ordinal() - target.ordinal();
        int steps = Math.min(falloffSteps(machine, query), Math.abs(difference));
        return CLIMATES[target.ordinal() + Integer.signum(difference) * steps];
    }

    @Nullable
    static CropClimate conditionedClimateAt(Level level, BlockPos machine, BlockPos query, CropClimate target) {
        return conditionedClimateAt(level, machine, query, target, null);
    }

    @Nullable
    static CropClimate conditionedClimateAt(Level level, BlockPos machine, BlockPos query, CropClimate target,
                                           @Nullable CropClimate ambient) {
        // Preserve the core's cheap path: native biome/planet lookup is needed only in the transition.
        if (falloffSteps(machine, query) == 0) return target;
        CropClimate natural = ambient != null ? ambient : GreenhouseEnvironment.naturalClimate(level, query);
        CropClimate climate = falloffClimate(target, natural, machine, query);
        return climate == natural ? null : climate;
    }

    private static int falloffSteps(BlockPos machine, BlockPos query) {
        long dx = (long) query.getX() - machine.getX(), dz = (long) query.getZ() - machine.getZ();
        long distanceSquared = dx * dx + dz * dz;
        for (int steps = 0; steps < CLIMATES.length - 1; steps++) {
            int radius = WIND_REACH + steps;
            if (distanceSquared <= radius * radius) return steps;
        }
        return CLIMATES.length - 1;
    }

    /** Both regulator types share a clear front core and its rounded transition toward ambient. */
    public static boolean canReach(Level level, BlockPos machine, Direction face, BlockPos query) {
        return inOutletArea(machine, face, query, INFLUENCE_REACH) && ShipCropEnvironment.sameSpace(level, machine, query)
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
