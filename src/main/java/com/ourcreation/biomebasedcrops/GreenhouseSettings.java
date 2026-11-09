package com.ourcreation.biomebasedcrops;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GreenhouseSettings {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_GREENHOUSES = BUILDER
            .comment("Allow enclosed greenhouses to emulate the biome sampled by their core.")
            .define("enableGreenhouses", true);
    public static final ModConfigSpec.BooleanValue ENABLE_AMBIENT_TEMPERATURE = BUILDER
            .comment("Nearby fire, lit campfires, lava, magma, ice, and snow adjust crop temperature.")
            .define("enableAmbientTemperature", true);
    public static final ModConfigSpec.IntValue RADIUS = BUILDER
            .comment("Cultivation and temperature-source radius around a greenhouse core or outdoor plant.")
            .defineInRange("radius", 4, 1, 6);
    public static final ModConfigSpec.IntValue REQUIRED_SPECIMENS = BUILDER
            .comment("Total native specimen blocks required inside a greenhouse or in its floor and walls.")
            .defineInRange("requiredSpecimens", 4, 1, 64);
    public static final ModConfigSpec.IntValue CACHE_TICKS = BUILDER
            .comment("Maximum time before greenhouse and temperature checks refresh, in game ticks.")
            .defineInRange("cacheTicks", 20, 1, 20);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_MATERIAL_RULES = BUILDER
            .comment("Override a biome's native specimens: biome_id=block_id,block_id",
                    "Example: minecraft:desert=minecraft:sand,minecraft:sandstone,minecraft:cactus",
                    "Any listed type counts toward requiredSpecimens. The last entry for a biome wins.")
            .defineListAllowEmpty("biomeMaterialRules", List.of(), () -> "", GreenhouseSettings::validateMaterialRule);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private GreenhouseSettings() {
    }

    public static int radius() {
        return SPEC.isLoaded() ? RADIUS.get() : RADIUS.getDefault();
    }

    public static int requiredSpecimens() {
        return SPEC.isLoaded() ? REQUIRED_SPECIMENS.get() : REQUIRED_SPECIMENS.getDefault();
    }

    public static boolean enabled() {
        return SPEC.isLoaded() ? ENABLE_GREENHOUSES.get() : ENABLE_GREENHOUSES.getDefault();
    }

    public static boolean ambientEnabled() {
        return SPEC.isLoaded() ? ENABLE_AMBIENT_TEMPERATURE.get() : ENABLE_AMBIENT_TEMPERATURE.getDefault();
    }

    static int cacheTicks() {
        return SPEC.isLoaded() ? CACHE_TICKS.get() : CACHE_TICKS.getDefault();
    }

    static boolean validateMaterialRule(Object value) {
        if (!(value instanceof String raw)) {
            return false;
        }
        String[] pair = raw.split("=", -1);
        if (pair.length != 2 || !validId(pair[0])) {
            return false;
        }
        if (pair[1].split(",", -1).length > 32) {
            return false;
        }
        for (String material : pair[1].split(",", -1)) {
            if (!validId(material)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validId(String value) {
        String text = value.trim();
        ResourceLocation id = ResourceLocation.tryParse(text);
        return text.indexOf(':') > 0 && id != null && !id.getPath().isEmpty();
    }
}
