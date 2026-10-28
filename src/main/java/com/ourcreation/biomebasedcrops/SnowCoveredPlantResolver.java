package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Reads Snow! Real Magic!'s public contained-state API without requiring the optional mod. */
public final class SnowCoveredPlantResolver {
    private static final String SNOW_BLOCK_ENTITY = "snownee.snow.block.entity.SnowBlockEntity";
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final ClassValue<Optional<Method>> GETTERS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                if (parent.getName().equals(SNOW_BLOCK_ENTITY)) {
                    try {
                        Method getter = type.getMethod("getContainedState");
                        return getter.getReturnType() == BlockState.class ? Optional.of(getter) : Optional.empty();
                    } catch (NoSuchMethodException e) {
                        logFailure(e);
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        }
    };

    private SnowCoveredPlantResolver() {
    }

    public static BlockState resolve(Level level, BlockPos pos, BlockState state) {
        // Ordinary plants need no block-entity lookup or optional-mod class loading.
        if (!state.getBlock().getClass().getName().startsWith("snownee.snow.block.")) {
            return state;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return state;
        }
        Optional<Method> getter = GETTERS.get(entity.getClass());
        if (getter.isEmpty()) {
            return state;
        }
        try {
            Object contained = getter.get().invoke(entity);
            return contained instanceof BlockState plant ? plant : state;
        } catch (ReflectiveOperationException e) {
            logFailure(e);
            return state;
        }
    }

    private static void logFailure(ReflectiveOperationException exception) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            BiomeBasedCrops.LOGGER.warn("Unable to resolve Snow! Real Magic! contained plants", exception);
        }
    }
}
