package com.ourcreation.biomebasedcrops;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Northstar's real atmosphere and powered sealer contracts, without a mandatory dependency. */
public final class NorthstarAtmosphere {
    private static final ThreadLocal<Integer> NATIVE_QUERY_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final int MAX_CACHED_PROVIDERS = 256;
    private static final Map<Level, LinkedHashMap<Long, Object>> ROOM_PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> NATIVE_PLANET_DIMENSIONS = Set.of(
            "northstar:earth_orbit", "northstar:mars", "northstar:mars_orbit", "northstar:mercury",
            "northstar:mercury_orbit", "northstar:moon", "northstar:venus", "northstar:venus_orbit");

    private NorthstarAtmosphere() {}

    /** Room or direct outlet climate; native planet sampling must never read this override. */
    @Nullable
    public static Float controlledTemperature(Level level, Vec3i query) {
        CropClimate climate = GreenhouseEnvironment.controlledClimate(level, new BlockPos(query));
        return climate == null ? null : LegacyClimate.toCelsius(climate);
    }

    @Nullable
    public static Float controlledTemperature(Level level, Vec3 query) {
        return controlledTemperature(level, BlockPos.containing(query));
    }

    /** Classify an optional planet's native ambient at its API boundary. */
    public static Optional<CropClimate> naturalClimate(Level level, BlockPos pos) {
        OptionalDouble value = naturalTemperature(level, pos);
        return value.isPresent() ? Optional.of(LegacyClimate.fromCelsius((float) value.getAsDouble())) : Optional.empty();
    }

    /** Provider-free ambient Celsius for the actual planets shipped by the verified Northstar release. */
    public static OptionalDouble naturalTemperature(Level level, BlockPos pos) {
        if (!NATIVE_PLANET_DIMENSIONS.contains(level.dimension().location().toString()) || !loaded(level, pos)) {
            return OptionalDouble.empty();
        }
        Optional<Method> method = TemperatureAccess.VALUE;
        if (method.isEmpty()) return OptionalDouble.empty();
        return GreenhouseEnvironment.withoutOverlay(() -> {
            try {
                float temperature = (Float) method.get().invoke(null, level, pos);
                return Float.isFinite(temperature) ? OptionalDouble.of(temperature) : OptionalDouble.empty();
            } catch (ReflectiveOperationException | RuntimeException error) {
                logFailure(error);
                return OptionalDouble.empty();
            }
        });
    }

    /** The planet's unmodified atmosphere; room oxygen never changes this result. */
    public static boolean naturalAtmosphereBreathable(Level level) {
        Optional<Access> access = LazyAccess.VALUE;
        if (access.isEmpty()) return true;
        try {
            Object manager = access.get().dimension.invoke(null, level);
            return (Boolean) access.get().naturalOxygen.invoke(manager);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logFailure(error);
            return true;
        }
    }

    /** Only a real, currently active Northstar provider sealing this exact position may supply oxygen. */
    public static boolean nativeProviderBreathable(Level level, BlockPos pos) {
        Optional<Access> access = LazyAccess.VALUE;
        if (access.isEmpty() || !loaded(level, pos)) return false;
        int previous = NATIVE_QUERY_DEPTH.get();
        NATIVE_QUERY_DEPTH.set(previous + 1);
        try {
            Object manager = access.get().dimension.invoke(null, level);
            return access.get().sealer.invoke(manager, pos) != null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            logFailure(error);
            return false;
        } finally {
            if (previous == 0) NATIVE_QUERY_DEPTH.remove();
            else NATIVE_QUERY_DEPTH.set(previous);
        }
    }

    /** Called only after Northstar failed to find its own sealer. Does not modify provider registries. */
    @Nullable
    public static Object greenhouseProvider(Level level, Vec3i query) {
        BlockPos pos = new BlockPos(query);
        if (NATIVE_QUERY_DEPTH.get() != 0 || !loaded(level, pos)
                || naturalAtmosphereBreathable(level) || !GreenhouseEnvironment.greenhouseProvidesOxygen(level, pos)) return null;
        Optional<Access> access = LazyAccess.VALUE;
        if (access.isEmpty()) return null;
        synchronized (ROOM_PROVIDERS) {
            var providers = ROOM_PROVIDERS.computeIfAbsent(level, key -> new LinkedHashMap<>(16, 0.75F, true));
            Object provider = providers.get(pos.asLong());
            if (provider != null) return provider;
            if (providers.size() >= MAX_CACHED_PROVIDERS) providers.pollFirstEntry();
            provider = createProvider(level, pos.immutable(), access.get().provider);
            providers.put(pos.asLong(), provider);
            return provider;
        }
    }

    @Nullable
    public static Object greenhouseProvider(Level level, Vec3 query) {
        return greenhouseProvider(level, BlockPos.containing(query));
    }

    private static Object createProvider(Level level, BlockPos boundPos, Class<?> provider) {
        WeakReference<Level> reference = new WeakReference<>(level);
        return Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[] { provider }, (proxy, method, arguments) -> {
            return switch (method.getName()) {
                case "isSealed" -> {
                    Level current = reference.get();
                    BlockPos pos = arguments[0] instanceof Vec3 point ? BlockPos.containing(point)
                            : new BlockPos((Vec3i) arguments[0]);
                    yield pos.equals(boundPos) && current != null && loaded(current, pos)
                            && GreenhouseEnvironment.greenhouseProvidesOxygen(current, pos);
                }
                case "drainOxygen" -> {
                    Level current = reference.get();
                    float amount = (Float) arguments[0];
                    if (current != null && Float.isFinite(amount) && amount > 0 && loaded(current, boundPos)
                            && GreenhouseEnvironment.greenhouseProvidesOxygen(current, boundPos)) {
                        GreenhouseEnvironment.consumeRoomOxygen(current, boundPos, amount);
                    }
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "Biome Based Crops room oxygen";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static void logFailure(Exception error) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            BiomeBasedCrops.LOGGER.warn("Could not read Northstar's atmosphere and oxygen sealers", error);
        }
    }

    private static final class LazyAccess {
        private static final Optional<Access> VALUE = find();

        private static Optional<Access> find() {
            if (!ModList.get().isLoaded("northstar")) return Optional.empty();
            try {
                Class<?> manager = Class.forName("com.lightning.northstar.world.oxygen.NorthstarOxygen");
                Class<?> provider = Class.forName("com.lightning.northstar.world.oxygen.NorthstarOxygen$Provider");
                Method dimension = manager.getMethod("getDimension", Level.class);
                Method natural = manager.getMethod("hasOxygen");
                Method sealer = manager.getMethod("getSealer", Vec3i.class);
                if (natural.getReturnType() != boolean.class || sealer.getReturnType() != provider) return Optional.empty();
                return Optional.of(new Access(dimension, natural, sealer, provider));
            } catch (ReflectiveOperationException | RuntimeException error) {
                logFailure(error);
                return Optional.empty();
            }
        }
    }

    private static final class TemperatureAccess {
        private static final Optional<Method> VALUE = find();

        private static Optional<Method> find() {
            if (!ModList.get().isLoaded("northstar")) return Optional.empty();
            try {
                Method method = Class.forName("com.lightning.northstar.world.temperature.NorthstarTemperature")
                        .getMethod("getBaseTemperature", Level.class, BlockPos.class);
                return method.getReturnType() == float.class ? Optional.of(method) : Optional.empty();
            } catch (ReflectiveOperationException | RuntimeException error) {
                logFailure(error);
                return Optional.empty();
            }
        }
    }

    private record Access(Method dimension, Method naturalOxygen, Method sealer, Class<?> provider) {}
}
