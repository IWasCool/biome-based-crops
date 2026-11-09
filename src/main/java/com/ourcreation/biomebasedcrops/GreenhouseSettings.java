package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GreenhouseSettings {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_GREENHOUSES = BUILDER
            .comment("Recognize enclosed greenhouse biomes from their required native materials and climate; no core is required.")
            .define("enableGreenhouses", true);
    public static final ModConfigSpec.IntValue MAX_ROOM_BLOCKS = BUILDER
            .comment("Maximum connected interior cells in a greenhouse. Dimensions and shape are unrestricted within this volume.")
            .defineInRange("maxRoomBlocks", 262144, 64, 1048576);
    public static final ModConfigSpec.IntValue SCAN_BLOCKS_PER_TICK = BUILDER
            .comment("Background room-scan work per level tick. Large rooms finish over several ticks without a large synchronous flood fill.")
            .defineInRange("scanBlocksPerTick", 1024, 256, 16384);
    public static final ModConfigSpec.IntValue MATERIAL_REFERENCE_VOLUME = BUILDER
            .comment("Interior volume covered by each biome's base material recipe. Larger rooms scale every material count proportionally.",
                    "Rooms up to this volume keep the base counts; larger requirements round up for each distinct material.")
            .defineInRange("materialReferenceVolume", GreenhouseRecipes.REFERENCE_VOLUME, 64, 1048576);
    public static final ModConfigSpec.DoubleValue MIN_ROOF_GLASS_RATIO = BUILDER
            .comment("Minimum fraction of exposed ceiling faces made from glass. Glass orientation and colour do not matter.")
            .defineInRange("minimumRoofGlassRatio", 0.5D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue MIN_WALL_GLASS_RATIO = BUILDER
            .comment("Minimum fraction of exposed wall faces made from glass; at least four wall glass blocks are also required.")
            .defineInRange("minimumWallGlassRatio", 0.1D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue REQUIRED_SPECIMENS = BUILDER
            .comment("Default count for EACH material in generated mod-biome recipes and bare-ID biomeMaterialRules entries.",
                    "All listed material types are required. Curated vanilla recipes and explicit *count entries keep their own counts.")
            .defineInRange("requiredSpecimens", 4, 1, 64);
    public static final ModConfigSpec.IntValue CACHE_TICKS = BUILDER
            .comment("Maximum time before greenhouse and temperature checks refresh, in game ticks.")
            .defineInRange("cacheTicks", 20, 1, 20);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_MATERIAL_RULES = BUILDER
            .comment("Override a biome's complete material recipe: biome_id=block_id*count,block_id*count",
                    "Example: minecraft:desert=minecraft:sand*8,minecraft:sandstone*4,minecraft:cactus*2",
                    "EVERY listed type and its full count is required; materials are not alternatives.",
                    "Bare block ids use requiredSpecimens for EACH type. Duplicate ids are rejected.",
                    "Up to 32 distinct types with 1 to 64 blocks each. The last entry for a biome wins.")
            .defineListAllowEmpty("biomeMaterialRules", List.of(), () -> "", GreenhouseSettings::validateMaterialRule);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private GreenhouseSettings() {
    }

    public static int maxRoomBlocks() { return SPEC.isLoaded() ? MAX_ROOM_BLOCKS.get() : MAX_ROOM_BLOCKS.getDefault(); }
    public static int scanBudget() { return SPEC.isLoaded() ? SCAN_BLOCKS_PER_TICK.get() : SCAN_BLOCKS_PER_TICK.getDefault(); }
    public static int materialReferenceVolume() {
        return SPEC.isLoaded() ? MATERIAL_REFERENCE_VOLUME.get() : MATERIAL_REFERENCE_VOLUME.getDefault();
    }
    public static double roofGlassRatio() { return SPEC.isLoaded() ? MIN_ROOF_GLASS_RATIO.get() : MIN_ROOF_GLASS_RATIO.getDefault(); }
    public static double wallGlassRatio() { return SPEC.isLoaded() ? MIN_WALL_GLASS_RATIO.get() : MIN_WALL_GLASS_RATIO.getDefault(); }

    public static int requiredSpecimens() {
        return SPEC.isLoaded() ? REQUIRED_SPECIMENS.get() : REQUIRED_SPECIMENS.getDefault();
    }

    public static boolean enabled() {
        return SPEC.isLoaded() ? ENABLE_GREENHOUSES.get() : ENABLE_GREENHOUSES.getDefault();
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
        try {
            parseRequirements(pair[1], 4);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static List<GreenhouseRecipes.MaterialRequirement> parseRequirements(String raw, int defaultCount) {
        String[] materials = raw.split(",", -1);
        if (materials.length < 1 || materials.length > 32 || defaultCount < 1 || defaultCount > 64) {
            throw new IllegalArgumentException("Invalid greenhouse material count");
        }
        List<GreenhouseRecipes.MaterialRequirement> requirements = new ArrayList<>();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String material : materials) {
            String[] value = material.trim().split("\\*", -1);
            if (value.length > 2 || !validId(value[0])) {
                throw new IllegalArgumentException("Invalid greenhouse material id");
            }
            ResourceLocation id = ResourceLocation.parse(value[0].trim());
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate greenhouse material id");
            int count = defaultCount;
            if (value.length == 2) {
                if (!value[1].trim().matches("[0-9]+")) throw new IllegalArgumentException("Invalid greenhouse material amount");
                count = Integer.parseInt(value[1].trim());
            }
            requirements.add(new GreenhouseRecipes.MaterialRequirement(id, count));
        }
        return List.copyOf(requirements);
    }

    private static boolean validId(String value) {
        String text = value.trim();
        ResourceLocation id = ResourceLocation.tryParse(text);
        return text.indexOf(':') > 0 && id != null && !id.getPath().isEmpty();
    }
}
