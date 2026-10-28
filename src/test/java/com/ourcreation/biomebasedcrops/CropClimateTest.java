package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.Tags;
import org.junit.jupiter.api.Test;

class CropClimateTest {
    @Test
    void fallbackCoversEveryTemperatureWithExactlyOneClimate() {
        for (int hundredths = -200; hundredths <= 300; hundredths++) {
            float temperature = hundredths / 100.0F;
            CropClimate expected = temperature < 0.15F ? CropClimate.ICY
                    : temperature < 0.5F ? CropClimate.COLD
                    : temperature <= 0.8F ? CropClimate.NEUTRAL
                    : temperature <= 1.0F ? CropClimate.WARM : CropClimate.HOT;
            assertClimate(expected, holder(temperature, Set.of()));
        }
        assertClimate(CropClimate.ICY, holder(Math.nextDown(0.15F), Set.of()));
        assertClimate(CropClimate.COLD, holder(Math.nextDown(0.5F), Set.of()));
        assertClimate(CropClimate.WARM, holder(Math.nextUp(0.8F), Set.of()));
        assertClimate(CropClimate.HOT, holder(Math.nextUp(1.0F), Set.of()));
    }

    @Test
    void moistureDoesNotImplyHeatAndGenericHotTagStillAllowsWarmBiomes() {
        assertClimate(CropClimate.NEUTRAL, holder(0.7F, Set.of(Tags.Biomes.IS_DRY)));
        assertClimate(CropClimate.COLD, holder(0.25F, Set.of(Tags.Biomes.IS_DRY, Tags.Biomes.IS_DESERT)));
        assertClimate(CropClimate.WARM, holder(0.95F, Set.of(Tags.Biomes.IS_HOT)));
        assertClimate(CropClimate.ICY, holder(0.25F, Set.of(Tags.Biomes.IS_SNOWY)));
        assertClimate(CropClimate.ICY, holder(0.25F, Set.of(Tags.Biomes.IS_ICY)));
        assertClimate(CropClimate.COLD, holder(0.6F, Set.of(Tags.Biomes.IS_COLD)));
    }

    @Test
    void exactCompatibilityTagsOverrideTemperatureAndConflictsUseStablePriority() {
        for (CropClimate climate : CropClimate.values()) {
            assertClimate(climate, holder(2.0F, Set.of(tag(climate), Tags.Biomes.IS_SNOWY)));
        }
        List<CropClimate> priority = List.of(CropClimate.ICY, CropClimate.COLD, CropClimate.HOT,
                CropClimate.WARM, CropClimate.NEUTRAL);
        for (int first = 0; first < priority.size(); first++) {
            Set<TagKey<Biome>> tags = priority.subList(first, priority.size()).stream()
                    .map(CropClimateTest::tag).collect(java.util.stream.Collectors.toSet());
            assertClimate(priority.get(first), holder(0.7F, tags));
        }
    }

    @Test
    void configRejectsUnknownEmptyAndMalformedClimateValues() {
        for (String invalid : List.of("", " ", "temperate", "warm,typo", "warm,", ",warm", "warm,,hot")) {
            assertFalse(Config.validateClimates(invalid), invalid);
            assertFalse(Config.validateRule("wheat=" + invalid), invalid);
        }
        for (Object invalid : List.of(123, List.of("warm"))) {
            assertFalse(Config.validateClimates(invalid));
        }
        assertFalse(Config.validateClimates(null));
        assertFalse(Config.validateRule("=warm"));
        assertFalse(Config.validateRule("wheat"));
        assertFalse(Config.validateRule("wheat=warm=hot"));
        assertTrue(Config.validateClimates(" ICY, Cold,neutral,WARM,hot "));
        assertTrue(Config.validateRule("example:crop=warm,hot"));
    }

    @Test
    void defaultRulesHaveValidClimatesAndNoDuplicateTokens() {
        Set<String> tokens = new java.util.HashSet<>();
        for (String rule : Config.CROP_CLIMATE_RULES.getDefault()) {
            assertTrue(Config.validateRule(rule), rule);
            assertTrue(tokens.add(rule.split("=", 2)[0]), "Duplicate default token: " + rule);
        }
        assertTrue(Config.validateClimates(Config.DEFAULT_CROP_CLIMATES.getDefault()));
    }

    @Test
    void everyBopSaplingAllowsTheClimatesOfItsNativeTrees() {
        // Derived from the bundled biome -> placed feature -> configured tree resources.
        Map<String, Set<CropClimate>> nativeClimates = Map.ofEntries(
                Map.entry("rainbow_birch_sapling", EnumSet.of(CropClimate.ICY)),
                Map.entry("snowblossom_sapling", EnumSet.of(CropClimate.ICY, CropClimate.NEUTRAL)),
                Map.entry("red_maple_sapling", EnumSet.of(CropClimate.ICY, CropClimate.COLD)),
                Map.entry("orange_maple_sapling", EnumSet.of(CropClimate.COLD)),
                Map.entry("yellow_maple_sapling", EnumSet.of(CropClimate.COLD)),
                Map.entry("pine_sapling", EnumSet.of(CropClimate.COLD, CropClimate.NEUTRAL, CropClimate.HOT)),
                Map.entry("fir_sapling", EnumSet.of(CropClimate.ICY, CropClimate.COLD)),
                Map.entry("redwood_sapling", EnumSet.of(CropClimate.NEUTRAL)),
                Map.entry("willow_sapling", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM)),
                Map.entry("flowering_oak_sapling", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM)),
                Map.entry("jacaranda_sapling", EnumSet.of(CropClimate.NEUTRAL)),
                Map.entry("magic_sapling", EnumSet.of(CropClimate.NEUTRAL)),
                Map.entry("origin_sapling", EnumSet.of(CropClimate.ICY, CropClimate.NEUTRAL)),
                Map.entry("umbran_sapling", EnumSet.of(CropClimate.COLD)),
                Map.entry("dead_sapling", EnumSet.of(CropClimate.ICY, CropClimate.COLD, CropClimate.HOT)),
                Map.entry("cypress_sapling", EnumSet.of(CropClimate.COLD, CropClimate.WARM)),
                Map.entry("palm_sapling", EnumSet.of(CropClimate.WARM, CropClimate.HOT)),
                Map.entry("mahogany_sapling", EnumSet.of(CropClimate.WARM)),
                Map.entry("hellbark_sapling", EnumSet.of(CropClimate.HOT)),
                Map.entry("empyreal_sapling", EnumSet.of(CropClimate.COLD)));
        assertDefaultClimates(nativeClimates, "biomesoplenty:");
    }

    @Test
    void caveJungleMushroomAndRiverPlantsCanGrowInTheirNativeClimates() {
        assertDefaultClimates(Map.of(
                "glow_berry", EnumSet.of(CropClimate.WARM),
                "glow_berries", EnumSet.of(CropClimate.WARM),
                "sugar_cane", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT),
                "brown_mushroom", EnumSet.of(CropClimate.COLD, CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT),
                "red_mushroom", EnumSet.of(CropClimate.COLD, CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT),
                "mangrove_propagule", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM),
                "mangrove", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM),
                "jungle_sapling", EnumSet.of(CropClimate.WARM, CropClimate.HOT),
                "cocoa", EnumSet.of(CropClimate.WARM, CropClimate.HOT)), "");
    }

    @Test
    void additionalSaplingsCoverTheirVerifiedGenerationBiomes() {
        assertDefaultClimates(Map.of(
                "blue_blossom_sapling", EnumSet.of(CropClimate.ICY, CropClimate.COLD),
                "lavender_blossom_sapling", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM),
                "orange_blossom_sapling", EnumSet.of(CropClimate.WARM, CropClimate.HOT),
                "yellow_blossom_sapling", EnumSet.of(CropClimate.NEUTRAL),
                "red_blossom_sapling", EnumSet.of(CropClimate.WARM, CropClimate.HOT),
                "ancient_sapling", EnumSet.of(CropClimate.NEUTRAL)), "quark:");
        assertDefaultClimates(Map.of("date_sapling", EnumSet.of(CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT)),
                "spawn:");
        assertDefaultClimates(Map.of(
                "pine_sapling", EnumSet.of(CropClimate.WARM),
                "dead_sapling", EnumSet.of(CropClimate.WARM),
                "hellbark_sapling", EnumSet.of(CropClimate.WARM)), "biomesoplenty:");
        assertTrue(Config.SUPPORTED_CROP_NAMESPACES.getDefault().containsAll(List.of("northstar", "spawn", "supplementaries")));
    }

    private static void assertDefaultClimates(Map<String, Set<CropClimate>> nativeClimates, String prefix) {
        CropClimateRules.CompiledRules rules = new CropClimateRules.CompiledRules(
                Config.CROP_CLIMATE_RULES.getDefault(), Config.DEFAULT_CROP_CLIMATES.getDefault());
        nativeClimates.forEach((plant, climates) -> {
            ResourceLocation id = ResourceLocation.parse((prefix.isEmpty() ? "minecraft:" : prefix) + plant);
            assertTrue(rules.profileFor(id, id).climates().containsAll(climates), "Cannot grow in native climate: " + id);
        });
    }

    @Test
    void everyBundledBopBiomeHasExactlyOneCompatibilityClimate() throws IOException {
        Path bop = optionalJar(projectDirectory().resolve("libs"), "BiomesOPlenty-");
        try (ZipFile source = new ZipFile(bop.toFile())) {
            verifyBiomeCoverage(source, source, "biomesoplenty");
        }
    }

    @Test
    void toughAsNailsCoversEveryVanillaBiome() throws IOException {
        Path tan = optionalJar(projectDirectory().resolve("libs"), "ToughAsNails-");
        Path vanilla = optionalJar(projectDirectory().resolve("build/moddev/artifacts"), "neoforge-", "client-extra");
        try (ZipFile tags = new ZipFile(tan.toFile()); ZipFile biomes = new ZipFile(vanilla.toFile())) {
            verifyBiomeCoverage(tags, biomes, "minecraft");
        }
    }

    private static void verifyBiomeCoverage(ZipFile tags, ZipFile biomes, String namespace) throws IOException {
        Map<String, EnumSet<CropClimate>> assignments = new HashMap<>();
        for (CropClimate climate : CropClimate.values()) {
            ZipEntry entry = tags.getEntry("data/toughasnails/tags/worldgen/biome/"
                    + climate.name().toLowerCase(java.util.Locale.ROOT) + "_biomes.json");
            assertTrue(entry != null, "Missing tag for " + climate);
            for (JsonElement value : json(tags, entry).getAsJsonArray("values")) {
                String id = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString();
                if (id.startsWith(namespace + ":")) {
                    assignments.computeIfAbsent(id, ignored -> EnumSet.noneOf(CropClimate.class)).add(climate);
                }
            }
        }
        int checked = 0;
        for (ZipEntry entry : java.util.Collections.list(biomes.entries())) {
            String prefix = "data/" + namespace + "/worldgen/biome/";
            if (!entry.getName().startsWith(prefix) || !entry.getName().endsWith(".json")) {
                continue;
            }
            String id = namespace + ":" + entry.getName().substring(prefix.length(), entry.getName().length() - 5);
            Set<CropClimate> assigned = assignments.get(id);
            assertTrue(assigned != null, "Unassigned biome: " + id);
            assertEquals(1, assigned.size(), "Conflicting tags for " + id);
            CropClimate expected = assigned.iterator().next();
            float temperature = json(biomes, entry).get("temperature").getAsFloat();
            assertClimate(expected, holder(temperature, Set.of(tag(expected))));
            // Also require total, mutually exclusive resolution when the optional tags are absent.
            Holder<Biome> untagged = holder(temperature, Set.of());
            assertClimate(CropClimate.at(untagged, BlockPos.ZERO), untagged);
            checked++;
        }
        assertTrue(checked > 0, "No biome resources found");
        assertEquals(assignments.size(), checked, "Climate tag references missing biome resources");
    }

    private static JsonObject json(ZipFile source, ZipEntry entry) throws IOException {
        try (var reader = new InputStreamReader(source.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path optionalJar(Path directory, String prefix, String... contains) throws IOException {
        assumeTrue(Files.isDirectory(directory), "Optional integration jars are absent");
        try (var files = Files.list(directory)) {
            var result = files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.toString().endsWith(".jar"))
                    .filter(path -> Arrays.stream(contains).allMatch(part -> path.getFileName().toString().contains(part)))
                    .findFirst();
            assumeTrue(result.isPresent(), "Optional integration jar is absent: " + prefix);
            return result.orElseThrow();
        }
    }

    private static Path projectDirectory() {
        return Path.of(System.getProperty("biomeBasedCrops.projectDir", "."));
    }

    private static void assertClimate(CropClimate expected, Holder<Biome> biome) {
        assertEquals(expected, CropClimate.at(biome, BlockPos.ZERO));
        assertEquals(expected, CropClimate.at(biome, new BlockPos(0, 320, 0)));
        assertEquals(1L, Arrays.stream(CropClimate.values()).filter(c -> c.matches(biome, BlockPos.ZERO)).count());
        assertTrue(expected.matches(biome, BlockPos.ZERO));
    }

    private static TagKey<Biome> tag(CropClimate climate) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("toughasnails",
                climate.name().toLowerCase(java.util.Locale.ROOT) + "_biomes"));
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome> holder(float temperature, Set<TagKey<Biome>> tags) {
        Biome biome = new Biome.BiomeBuilder().temperature(temperature).downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
        return (Holder<Biome>) Proxy.newProxyInstance(Holder.class.getClassLoader(), new Class<?>[] {Holder.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "value" -> biome;
                    case "is" -> args[0] instanceof TagKey<?> key && tags.contains(key);
                    case "toString" -> "Biome[temperature=" + temperature + ", tags=" + tags + "]";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }
}
