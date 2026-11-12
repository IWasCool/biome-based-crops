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
            .defineInRange("maxRoomBlocks", 1048576, 64, 1048576);
    public static final ModConfigSpec.IntValue SCAN_BLOCKS_PER_TICK = BUILDER
            .comment("Background room-scan work per level tick. Large rooms finish over several ticks without a large synchronous flood fill.")
            .defineInRange("scanBlocksPerTick", 1024, 256, 16384);
    public static final ModConfigSpec.IntValue CONSTRUCTION_RULES_VERSION = BUILDER
            .comment("Tracks glass-roof and room-volume default migrations; customized settings are preserved.")
            .defineInRange("constructionRulesVersion", 0, 0, 4);
    public static final ModConfigSpec.IntValue MATERIAL_REFERENCE_VOLUME = BUILDER
            .comment("Default habitat material percentages are the recipe amount divided by this reference volume.",
                    "Requirements round up proportionally for every room size, without a fixed minimum or floor-area density.",
                    "Explicit legacy biomeMaterialRules retain their configured minimum amount and proportional large-room scaling.")
            .defineInRange("materialReferenceVolume", GreenhouseRecipes.REFERENCE_VOLUME, 64, 1048576);
    public static final ModConfigSpec.DoubleValue MIN_ROOF_GLASS_RATIO = BUILDER
            .comment("Legacy survey ratio retained for existing configs. Does not control biome registration or plant growth.",
                    "Only each crop or sapling's column needs sky-exposed glass; the rest of the roof may be solid.")
            .defineInRange("minimumRoofGlassRatio", 1.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue MIN_WALL_GLASS_RATIO = BUILDER
            .comment("Legacy setting retained for existing configs. Ignored: sealed walls may use any suitable material.")
            .defineInRange("minimumWallGlassRatio", 0.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue REQUIRED_SPECIMENS = BUILDER
            .comment("Default count for EACH material in generated mod-biome recipes and bare-ID biomeMaterialRules entries.",
                    "All listed material types are required. Curated vanilla recipes and explicit *count entries keep their own counts.")
            .defineInRange("requiredSpecimens", 4, 1, 64);
    public static final ModConfigSpec.IntValue CACHE_TICKS = BUILDER
            .comment("Maximum time before greenhouse and temperature checks refresh, in game ticks.")
            .defineInRange("cacheTicks", 20, 1, 20);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_MATERIAL_RULES = BUILDER
            .comment("Override a biome's complete material recipe: biome_id=block_id*count,block_id*count",
                    "Example: minecraft:desert=minecraft:sandstone*16,minecraft:cactus*3,minecraft:dead_bush*8",
                    "EVERY listed type and its full count is required; materials are not alternatives.",
                    "Bare block ids use requiredSpecimens for EACH type. Duplicate ids are rejected.",
                    "Up to 32 distinct types with 1 to 64 blocks each. The last entry for a biome wins.")
            .defineListAllowEmpty("biomeMaterialRules", List.of(), () -> "", GreenhouseSettings::validateMaterialRule);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_FLOOR_RULES = BUILDER
            .comment("Override native floor coverage: biome_id=percent:block_id,block_id",
                    "Example: minecraft:desert=100:minecraft:sand. Listed blocks are alternatives; walls never count.",
                    "Farmland and climate units may sit directly above a listed native floor block.",
                    "Required interior scenery above the room's base floor may retain that native foundation beneath it.",
                    "Custom biomeMaterialRules omit automatic floors unless this list defines one. Last matching entry wins.")
            .defineListAllowEmpty("biomeFloorRules", List.of(), () -> "", GreenhouseSettings::validateFloorRule);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private GreenhouseSettings() {
    }

    public static boolean migrateConstructionRules() {
        if (!SPEC.isLoaded() || CONSTRUCTION_RULES_VERSION.get() >= 4) return false;
        if (CONSTRUCTION_RULES_VERSION.get() < 3) {
            boolean originalDefaults = CONSTRUCTION_RULES_VERSION.get() == 0
                    && MIN_ROOF_GLASS_RATIO.get() == 0.5D && MIN_WALL_GLASS_RATIO.get() == 0.1D;
            boolean fullGlassDefaults = MIN_ROOF_GLASS_RATIO.get() == 1.0D && MIN_WALL_GLASS_RATIO.get() == 1.0D;
            boolean decoratedGlassDefaults = MIN_ROOF_GLASS_RATIO.get() == 0.6D && MIN_WALL_GLASS_RATIO.get() == 0.6D;
            if (originalDefaults || fullGlassDefaults || decoratedGlassDefaults) MIN_ROOF_GLASS_RATIO.set(1.0D);
            MIN_WALL_GLASS_RATIO.set(0.0D);
        }
        if (MAX_ROOM_BLOCKS.get() == 262144) MAX_ROOM_BLOCKS.set(1048576);
        CONSTRUCTION_RULES_VERSION.set(4);
        return true;
    }

    public static int maxRoomBlocks() { return SPEC.isLoaded() ? MAX_ROOM_BLOCKS.get() : MAX_ROOM_BLOCKS.getDefault(); }
    public static int scanBudget() { return SPEC.isLoaded() ? SCAN_BLOCKS_PER_TICK.get() : SCAN_BLOCKS_PER_TICK.getDefault(); }
    public static int materialReferenceVolume() {
        return SPEC.isLoaded() ? MATERIAL_REFERENCE_VOLUME.get() : MATERIAL_REFERENCE_VOLUME.getDefault();
    }
    public static double roofGlassRatio() { return SPEC.isLoaded() ? MIN_ROOF_GLASS_RATIO.get() : MIN_ROOF_GLASS_RATIO.getDefault(); }
    /** Retained for old callers; wall glazing no longer affects enclosure qualification. */
    public static double wallGlassRatio() { return 0.0D; }

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

    static boolean validateFloorRule(Object value) {
        if (!(value instanceof String raw)) return false;
        String[] pair = raw.split("=", -1);
        if (pair.length != 2 || !validId(pair[0])) return false;
        try { parseFloor(pair[1]); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    static GreenhouseRecipes.FloorRequirement parseFloor(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Missing native floor percentage");
        int percent = Integer.parseInt(parts[0].trim());
        String[] tokens = parts[1].split(",", -1);
        List<ResourceLocation> blocks = new ArrayList<>();
        for (String token : tokens) {
            if (!validId(token)) throw new IllegalArgumentException("Invalid native floor block");
            ResourceLocation id = ResourceLocation.parse(token.trim());
            if (blocks.contains(id)) throw new IllegalArgumentException("Duplicate native floor block");
            blocks.add(id);
        }
        return new GreenhouseRecipes.FloorRequirement(blocks, percent);
    }

    static GreenhouseRecipes.FloorRequirement floorOverride(ResourceLocation biome) {
        GreenhouseRecipes.FloorRequirement selected = null;
        List<? extends String> rules = SPEC.isLoaded() ? BIOME_FLOOR_RULES.get() : BIOME_FLOOR_RULES.getDefault();
        for (String raw : rules) {
            if (!validateFloorRule(raw)) continue;
            String[] pair = raw.split("=", 2);
            if (biome.equals(ResourceLocation.parse(pair[0].trim()))) selected = parseFloor(pair[1]);
        }
        return selected;
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
