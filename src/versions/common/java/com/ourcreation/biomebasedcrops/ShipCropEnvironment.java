package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * Optional ship projection for targets without a published Sable Companion artifact.
 * No Minecraft-specific companion JAR is bundled into these builds. If an installed
 * ship mod supplies the companion API, bind it once; otherwise use ordinary positions.
 */
public final class ShipCropEnvironment {
    private static final Binding COMPANION = Binding.find();

    private ShipCropEnvironment() {}

    public static boolean isOnShip(Level level, BlockPos pos) {
        return containing(level, pos) != null;
    }

    public static boolean sameSpace(Level level, BlockPos first, BlockPos second) {
        Object firstShip = containing(level, first);
        Object secondShip = containing(level, second);
        if (firstShip == null) return secondShip == null;
        if (secondShip == null) return false;
        try {
            return COMPANION.uniqueId.invoke(firstShip).equals(COMPANION.uniqueId.invoke(secondShip));
        } catch (ReflectiveOperationException | RuntimeException error) {
            COMPANION.report(error);
            return firstShip == secondShip;
        }
    }

    public static BlockPos worldPosition(Level level, BlockPos pos) {
        if (containing(level, pos) == null) return pos.immutable();
        try {
            Vector3d projected = (Vector3d) COMPANION.project.invoke(COMPANION.instance, level,
                    new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), new Vector3d());
            return BlockPos.containing(projected.x, projected.y, projected.z);
        } catch (ReflectiveOperationException | RuntimeException error) {
            COMPANION.report(error);
            return pos.immutable();
        }
    }

    private static Object containing(Level level, BlockPos pos) {
        if (COMPANION == null) return null;
        try {
            return COMPANION.containing.invoke(COMPANION.instance, level, pos);
        } catch (ReflectiveOperationException | RuntimeException error) {
            COMPANION.report(error);
            return null;
        }
    }

    private static final class Binding {
        private final Object instance;
        private final Method containing;
        private final Method project;
        private final Method uniqueId;
        private boolean reported;

        private Binding(Object instance, Method containing, Method project, Method uniqueId) {
            this.instance = instance;
            this.containing = containing;
            this.project = project;
            this.uniqueId = uniqueId;
        }

        private static Binding find() {
            try {
                Class<?> companion = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                Class<?> subLevel = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
                Field instance = companion.getField("INSTANCE");
                return new Binding(instance.get(null), companion.getMethod("getContaining", Level.class, BlockPos.class),
                        companion.getMethod("projectOutOfSubLevel", Level.class, Vector3d.class, Vector3d.class),
                        subLevel.getMethod("getUniqueId"));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException | LinkageError error) {
                BiomeBasedCrops.LOGGER.warn("The installed Sable Companion API is incompatible; ship projection is unavailable", error);
                return null;
            }
        }

        private synchronized void report(Exception error) {
            if (!reported) {
                reported = true;
                BiomeBasedCrops.LOGGER.warn("The installed Sable Companion API could not project this environment", error);
            }
        }
    }
}
