package com.ourcreation.biomebasedcrops;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_TOOLTIPS = ClientConfig.ENABLE_TOOLTIPS;

    public static final ModConfigSpec.BooleanValue BLOCK_NATURAL_GROWTH = BUILDER
            .comment("Enforces matching natural biome, direct thermoregulator climate, or sky-lit greenhouse requirements.")
            .define("blockNaturalGrowth", true);

    public static final ModConfigSpec.BooleanValue BLOCK_BONEMEAL_GROWTH = BUILDER
            .comment("Enforces plant growth requirements for bonemeal and supported fertilizers.")
            .define("blockBonemealGrowth", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CROP_BIOME_DEPENDENCIES = BUILDER
            .comment("Applies biome climate rules to crops, seeds, and crop-like produce.",
                    "Set false to allow ordinary natural and fertilizer growth in any biome and hide crop climate requirement tooltips.",
                    "Can also be changed in game with /biomebasedcrops crops enable or disable (operator permission required).")
            .define("enableCropBiomeDependencies", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SAPLING_BIOME_DEPENDENCIES = BUILDER
            .comment("Applies biome climate rules to saplings, bamboo, cactus, sugar cane, mushrooms, fungi, and similar growable plants.",
                    "Set false to allow ordinary natural and fertilizer growth in any biome and hide their climate requirement tooltips.",
                    "Can also be changed in game with /biomebasedcrops trees enable or disable (operator permission required).")
            .define("enableSaplingBiomeDependencies", true);

    public static final ModConfigSpec.BooleanValue ENABLE_THERMOREGULATOR_COMPATIBILITY = BUILDER
            .comment("Allows active external climate controllers to condition greenhouse air and Tough As Nails thermoregulators to support crops in their four-block front area.",
                    "Direct crop climate support needs no greenhouse or glass; explicit biome rules still require a matching biome.")
            .define("enableThermoregulatorCompatibility", true);

    public static final ModConfigSpec.IntValue THERMOREGULATOR_RADIUS = BUILDER
            .comment("Search radius for other active external climate controllers, such as Northstar's sealed regulator.",
                    "Electrical and Tough As Nails thermoregulators always have a four-block circular front area for direct crop support.")
            .defineInRange("thermoregulatorRadius", 16, 1, 64);

    public static final ModConfigSpec.IntValue CLIMATE_RULES_VERSION = BUILDER
            .comment("Tracks migration of the original controller coverage default; customized radii are preserved.")
            .defineInRange("climateRulesVersion", 0, 0, 1);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SUPPORTED_CROP_NAMESPACES = BUILDER
            .comment("Crop/item namespaces that are treated as crop-compatible when their block behaves like a crop.",
                    "Add Farmer's Delight add-on mod ids here if an add-on uses an unusual namespace.")
            .defineListAllowEmpty("supportedCropNamespaces", List.of(
                    "minecraft",
                    "farmersdelight",
                    "farmersrespite",
                    "nethersdelight",
                    "endsdelight",
                    "miners_delight",
                    "minersdelight",
                    "brewinandchewin",
                    "biomesoplenty",
                    "northstar",
                    "spawn",
                    "supplementaries"
            ), () -> "", Config::validateSimpleString);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CROP_CLIMATE_RULES = BUILDER
            .comment("Ordered crop climate rules. Format: token=hot,warm,neutral,cold,icy",
                    "The longest matching token decides the crop's allowed climates; the first rule wins equal-length ties.",
                    "Bare tokens match block/item paths; tokens containing a namespace also match the full id.",
                    "Add rules for any Farmer's Delight add-on crops that do not match these names.")
            .defineListAllowEmpty("cropClimateRules", List.of(
                    "spruce_sapling=icy,cold,neutral",
                    "birch_sapling=cold,neutral",
                    "cherry_sapling=cold,neutral",
                    "dark_oak_sapling=cold,neutral",
                    "oak_sapling=cold,neutral,warm",
                    "jungle_sapling=warm,hot",
                    "acacia_sapling=warm,hot",
                    "mangrove_propagule=neutral,warm,hot",
                    "biomesoplenty:rainbow_birch_sapling=icy,cold,neutral",
                    "biomesoplenty:snowblossom_sapling=icy,cold,neutral",
                    "biomesoplenty:red_maple_sapling=icy,cold,neutral",
                    "biomesoplenty:orange_maple_sapling=cold,neutral",
                    "biomesoplenty:yellow_maple_sapling=cold,neutral",
                    "biomesoplenty:pine_sapling=cold,neutral,warm,hot",
                    "biomesoplenty:fir_sapling=icy,cold",
                    "biomesoplenty:redwood_sapling=cold,neutral",
                    "biomesoplenty:willow_sapling=neutral,warm",
                    "biomesoplenty:flowering_oak_sapling=cold,neutral,warm",
                    "biomesoplenty:jacaranda_sapling=neutral,warm",
                    "biomesoplenty:magic_sapling=neutral,warm",
                    "biomesoplenty:origin_sapling=icy,cold,neutral",
                    "biomesoplenty:umbran_sapling=cold,neutral",
                    "biomesoplenty:dead_sapling=icy,cold,neutral,warm,hot",
                    "biomesoplenty:cypress_sapling=cold,neutral,warm",
                    "biomesoplenty:palm_sapling=warm,hot",
                    "biomesoplenty:mahogany_sapling=warm,hot",
                    "biomesoplenty:hellbark_sapling=warm,hot",
                    "biomesoplenty:empyreal_sapling=cold",
                    "quark:blue_blossom_sapling=icy,cold",
                    "quark:lavender_blossom_sapling=neutral,warm",
                    "quark:orange_blossom_sapling=warm,hot",
                    "quark:yellow_blossom_sapling=neutral,warm",
                    "quark:red_blossom_sapling=warm,hot",
                    "quark:ancient_sapling=neutral,warm",
                    "spawn:date_sapling=neutral,warm,hot",
                    "azalea=neutral,warm",
                    "flowering_azalea=neutral,warm",
                    "bamboo_sapling=neutral,warm,hot",
                    "bamboo=neutral,warm,hot",
                    "cactus=hot",
                    "sugar_cane=neutral,warm,hot",
                    "brown_mushroom=cold,neutral,warm,hot",
                    "red_mushroom=cold,neutral,warm,hot",
                    "nether_wart=hot",
                    "crimson_fungus=hot",
                    "warped_fungus=hot",
                    "torchflower=warm,hot",
                    "pitcher=warm,hot",
                    "sweet_berry=icy,cold",
                    "glow_berry=cold,neutral,warm",
                    "glow_berries=cold,neutral,warm",
                    "cranberry=icy,cold",
                    "blueberry=icy,cold",
                    "blackberry=cold,neutral",
                    "raspberry=cold,neutral",
                    "strawberry=neutral,warm",
                    "beetroot=cold,neutral",
                    "potato=cold,neutral",
                    "cabbage=cold,neutral",
                    "onion=cold,neutral",
                    "garlic=cold,neutral",
                    "lettuce=cold,neutral",
                    "carrot=cold,neutral",
                    "wheat=neutral,warm",
                    "barley=cold,neutral",
                    "oat=cold,neutral",
                    "rye=cold,neutral",
                    "flax=cold,neutral",
                    "pumpkin=cold,neutral",
                    "soybean=neutral,warm",
                    "tea=neutral,warm",
                    "hop=neutral,warm",
                    "cucumber=neutral,warm",
                    "cotton=neutral,warm",
                    "tomato=warm,hot",
                    "rice=warm,hot",
                    "melon=warm,hot",
                    "grape=warm,hot",
                    "corn=warm,hot",
                    "eggplant=warm,hot",
                    "peanut=warm,hot",
                    "pineapple=warm,hot",
                    "pepper=hot,warm",
                    "chili=hot,warm",
                    "coffee=hot,warm",
                    "cocoa=warm,hot",
                    "spruce=icy,cold",
                    "pine=icy,cold",
                    "fir=icy,cold",
                    "redwood=cold,neutral",
                    "willow=neutral,warm",
                    "maple=cold,neutral",
                    "dark_oak=cold,neutral",
                    "oak=cold,neutral,warm",
                    "birch=cold,neutral",
                    "cherry=cold,neutral",
                    "mangrove=neutral,warm,hot",
                    "jungle=warm,hot",
                    "acacia=warm,hot"
            ), () -> "", Config::validateRule);

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_CROP_CLIMATES = BUILDER
            .comment("Allowed climates for detected crops that do not match any cropClimateRules token.")
            .define("defaultCropClimates", "neutral,warm", Config::validateClimates);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CROP_BIOME_RULES = BUILDER
            .comment("Optional biome overrides for crops and saplings. Format: crop=biome,biome,#biome_tag",
                    "Example: wheat=minecraft:plains,minecraft:forest or rice=#minecraft:is_jungle",
                    "Use * to allow a crop in every biome: wheat=*",
                    "These replace climate requirements; valid greenhouses can emulate the listed biomes, but climate controllers alone cannot.",
                    "Crop tokens match block/item ids like cropClimateRules: longest token wins, first entry breaks ties.",
                    "An empty list keeps the climate rules. Biome and tag ids must include their namespace.")
            .defineListAllowEmpty("cropBiomeRules", List.of(), () -> "", Config::validateBiomeRule);

    static final ModConfigSpec SPEC = BUILDER.build();

    static boolean migrateClimateRules() {
        if (!SPEC.isLoaded() || CLIMATE_RULES_VERSION.get() >= 1) return false;
        if (THERMOREGULATOR_RADIUS.get() == 4) THERMOREGULATOR_RADIUS.set(16);
        CLIMATE_RULES_VERSION.set(1);
        return true;
    }

    static boolean validateBiomeRule(Object obj) {
        if (!(obj instanceof String value)) {
            return false;
        }
        String[] parts = value.split("=", -1);
        if (parts.length != 2 || parts[0].isBlank()) {
            return false;
        }
        for (String selector : parts[1].split(",", -1)) {
            if (BiomeSelector.parse(selector).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static boolean validateRule(Object obj) {
        if (!(obj instanceof String value) || value.isBlank()) {
            return false;
        }

        String[] parts = value.split("=", 2);
        return parts.length == 2 && validateSimpleString(parts[0]) && validateClimates(parts[1]);
    }

    static boolean validateClimates(Object obj) {
        if (!(obj instanceof String value) || value.isBlank()) {
            return false;
        }
        for (String climate : value.split(",", -1)) {
            if (CropClimate.byId(climate).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateSimpleString(Object obj) {
        return obj instanceof String value && !value.isBlank();
    }
}
