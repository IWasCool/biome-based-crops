package com.ourcreation.biomebasedcrops;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/** Keeps crop/controller block access local while sampling the biome at a ship's current world position. */
public final class ShipCropEnvironment {
    private ShipCropEnvironment() { }

    public static boolean isOnShip(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos) != null;
    }

    /** Adjacent storage plots do not mean that their ships share an outlet stream. */
    public static boolean sameSpace(Level level, BlockPos first, BlockPos second) {
        return sameSpace(level, first, second, SableCompanion.INSTANCE);
    }

    static boolean sameSpace(Level level, BlockPos first, BlockPos second, SableCompanion companion) {
        SubLevelAccess firstShip = companion.getContaining(level, first);
        SubLevelAccess secondShip = companion.getContaining(level, second);
        return firstShip == null ? secondShip == null
                : secondShip != null && firstShip.getUniqueId().equals(secondShip.getUniqueId());
    }

    public static BlockPos worldPosition(Level level, BlockPos pos) {
        return worldPosition(level, pos, SableCompanion.INSTANCE);
    }

    static BlockPos worldPosition(Level level, BlockPos pos, SableCompanion companion) {
        // Ordinary crop checks need no vectors or projection, even when Sable is installed.
        if (companion.getContaining(level, pos) == null) return pos.immutable();
        // Project the block center, not its lower corner, and read the pose on every query.
        // A crop moving across a biome boundary must not keep the plot's biome or an old world position.
        Vector3d world = companion.projectOutOfSubLevel(level,
                new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), new Vector3d());
        return BlockPos.containing(world.x, world.y, world.z);
    }
}
