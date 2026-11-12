package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.IdentityHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Optional public TAN API integration; native personal modifiers and temperature ticking stay intact. */
public final class ToughAsNailsTemperature {
    private static boolean registered;
    private static Object noThermoregulatorEffect;

    private ToughAsNailsTemperature() { }

    /** The positional adapter already supplies the complete local regulator climate. */
    public static Object localThermoregulatorEffect(Object nativeEffect) {
        return registered && Config.SPEC.isLoaded() && Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()
                ? noThermoregulatorEffect : nativeEffect;
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("toughasnails")) event.enqueueWork(ToughAsNailsTemperature::register);
    }

    private static void register() {
        if (registered) return;
        try {
            Class<?> helper = Class.forName("toughasnails.api.temperature.TemperatureHelper");
            Class<?> modifier = Class.forName("toughasnails.api.temperature.IPositionalTemperatureModifier");
            Class<?> temperature = Class.forName("toughasnails.api.temperature.TemperatureLevel");
            Class<?> effect = Class.forName("toughasnails.block.entity.ThermoregulatorBlockEntity$Effect");
            Object noEffect = effect.getMethod("valueOf", String.class).invoke(null, "NONE");
            var levels = new EnumMap<CropClimate, Object>(CropClimate.class);
            var climates = new IdentityHashMap<Object, CropClimate>();
            var valueOf = temperature.getMethod("valueOf", String.class);
            for (CropClimate climate : CropClimate.values()) {
                Object nativeLevel = valueOf.invoke(null, climate.name());
                levels.put(climate, nativeLevel);
                climates.put(nativeLevel, climate);
            }
            Object adapter = Proxy.newProxyInstance(modifier.getClassLoader(), new Class<?>[] { modifier }, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "modify" -> {
                        // TAN has already applied its native weather, altitude and shelter modifiers.
                        CropClimate ambient = climates.get(args[2]);
                        CropClimate climate = GreenhouseEnvironment.controlledClimate((Level) args[0], (BlockPos) args[1], ambient);
                        yield climate == null ? args[2] : levels.get(climate);
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Biome Based Crops room temperature";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            helper.getMethod("registerPositionalTemperatureModifier", modifier).invoke(null, adapter);
            noThermoregulatorEffect = noEffect;
            registered = true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            BiomeBasedCrops.LOGGER.warn("Could not register greenhouse room temperature with Tough As Nails", error);
        }
    }
}
