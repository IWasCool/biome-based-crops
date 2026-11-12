package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Proxy;
import java.util.EnumMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Optional public TAN API integration; native personal modifiers and temperature ticking stay intact. */
public final class ToughAsNailsTemperature {
    private static boolean registered;

    private ToughAsNailsTemperature() { }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("toughasnails")) event.enqueueWork(ToughAsNailsTemperature::register);
    }

    private static void register() {
        if (registered) return;
        try {
            Class<?> helper = Class.forName("toughasnails.api.temperature.TemperatureHelper");
            Class<?> modifier = Class.forName("toughasnails.api.temperature.IPositionalTemperatureModifier");
            Class<?> temperature = Class.forName("toughasnails.api.temperature.TemperatureLevel");
            var levels = new EnumMap<CropClimate, Object>(CropClimate.class);
            var valueOf = temperature.getMethod("valueOf", String.class);
            for (CropClimate climate : CropClimate.values()) levels.put(climate, valueOf.invoke(null, climate.name()));
            Object adapter = Proxy.newProxyInstance(modifier.getClassLoader(), new Class<?>[] { modifier }, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "modify" -> {
                        CropClimate climate = GreenhouseEnvironment.controlledClimate((Level) args[0], (BlockPos) args[1]);
                        yield climate == null ? args[2] : levels.get(climate);
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Biome Based Crops room temperature";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            helper.getMethod("registerPositionalTemperatureModifier", modifier).invoke(null, adapter);
            registered = true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            BiomeBasedCrops.LOGGER.warn("Could not register greenhouse room temperature with Tough As Nails", error);
        }
    }
}
