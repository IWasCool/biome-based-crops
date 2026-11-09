package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class GreenhouseRecipesTest {
    @Test
    void everyLoadedBiomeHasAUsableRecipeAndCorrectNativeExamples(MinecraftServer server) {
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var biome : biomes.holders().toList()) {
            var recipe = GreenhouseRecipes.forServerBiome(biome);
            assertFalse(recipe.specimens().isEmpty(), biome.key().location().toString());
            assertTrue(recipe.specimens().size() <= 32);
            assertTrue(recipe.count() > 0);
            assertEquals(recipe.count(), recipe.requirements().stream().mapToInt(GreenhouseRecipes.MaterialRequirement::count).sum());
            assertEquals(recipe.specimens(), recipe.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::block).toList());
            assertEquals(recipe.specimens().size(), new HashSet<>(recipe.specimens()).size());
            assertTrue(recipe.floor().coveragePercent() > 0 && !recipe.floor().blocks().isEmpty(),
                    "Missing native floor for " + biome.key().location());
            assertTrue(recipe.specimens().stream().noneMatch(recipe.floor().blocks()::contains),
                    "Native ground duplicated as a habitat feature: " + biome.key().location());
            for (var block : recipe.specimens()) {
                assertTrue(BuiltInRegistries.BLOCK.containsKey(block), "Missing specimen " + block);
                assertNotEquals(Items.AIR, BuiltInRegistries.BLOCK.get(block).asItem(), "Unplaceable specimen " + block);
            }
            for (var block : recipe.floor().blocks()) {
                assertTrue(BuiltInRegistries.BLOCK.containsKey(block), "Missing native ground " + block);
                assertNotEquals(Items.AIR, BuiltInRegistries.BLOCK.get(block).asItem(), "Unplaceable native ground " + block);
            }
        }
        var cherry = GreenhouseRecipes.forServerBiome(biomes.getHolder(id("minecraft:cherry_grove")).orElseThrow());
        assertEquals(id("minecraft:cherry_log"), cherry.specimens().getFirst());
        var mangrove = GreenhouseRecipes.forServerBiome(biomes.getHolder(id("minecraft:mangrove_swamp")).orElseThrow());
        assertEquals(id("minecraft:mangrove_log"), mangrove.specimens().getFirst());
    }

    @Test
    void all64VanillaBiomesHaveDistinctFullHabitatsOrCompatibleOceanAliases(MinecraftServer server) {
        Map<HabitatSignature, Set<ResourceLocation>> palettes = new HashMap<>();
        Set<ResourceLocation> oceanAliases = Set.of(id("minecraft:ocean"), id("minecraft:deep_ocean"),
                id("minecraft:lukewarm_ocean"), id("minecraft:deep_lukewarm_ocean"),
                id("minecraft:cold_ocean"), id("minecraft:deep_cold_ocean"));
        int count = 0;
        for (var biome : server.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
            if (!biome.key().location().getNamespace().equals("minecraft")) continue;
            var recipe = GreenhouseRecipes.forServerBiome(biome);
            var signature = habitat(recipe);
            palettes.computeIfAbsent(signature, ignored -> new HashSet<>()).add(biome.key().location());
            assertTrue(recipe.requirements().stream().allMatch(material -> material.count() >= 1 && material.count() <= 64));
            count++;
        }
        assertEquals(64, count);
        for (var aliases : palettes.values()) {
            assertTrue(aliases.size() == 1 || oceanAliases.containsAll(aliases),
                    "Unexpected identical full vanilla habitats: " + aliases);
        }
        assertEquals(habitat(server, "minecraft:ocean"), habitat(server, "minecraft:deep_ocean"),
                "Matching shallow/deep ocean habitats should remain usable aliases");
    }

    @Test
    void forestAndDesertRequireSubstantialVegetationAndRealNativeGround(MinecraftServer server) {
        var forest = recipe(server, "minecraft:forest");
        assertEquals(90, forest.floor().coveragePercent());
        assertEquals(58, forest.floor().required(64));
        assertEquals(Set.of(id("minecraft:grass_block"), id("minecraft:dirt"), id("minecraft:rooted_dirt"),
                id("minecraft:coarse_dirt")), Set.copyOf(forest.floor().blocks()));
        assertEquals(List.of(id("minecraft:oak_log"), id("minecraft:oak_leaves"), id("minecraft:birch_log")), forest.specimens());
        assertEquals(List.of(16, 64, 8), forest.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
        assertEquals(List.of(1, 2, 1), forest.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::minHeight).toList());
        assertTrue(forest.requirements().stream().allMatch(GreenhouseRecipes::isPercentageBased));

        var desert = recipe(server, "minecraft:desert");
        assertEquals(new GreenhouseRecipes.FloorRequirement(List.of(id("minecraft:sand")), 100), desert.floor());
        assertEquals(64, desert.floor().required(64));
        assertEquals(List.of(id("minecraft:sandstone"), id("minecraft:cactus"), id("minecraft:dead_bush")), desert.specimens());
        assertEquals(List.of(16, 3, 8), desert.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
        assertEquals(List.of(0, 1, 1), desert.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::minHeight).toList());
        assertTrue(desert.requirements().stream().allMatch(GreenhouseRecipes::isPercentageBased));
        assertFalse(desert.floor().blocks().contains(id("minecraft:farmland")), "Farmland needs an actual sand underlay");
    }

    @Test
    void overridesRequireEveryTypeAndPreserveExplicitAndLegacyCounts(MinecraftServer server) {
        List<? extends String> oldRules = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        List<? extends String> oldFloors = List.copyOf(GreenhouseSettings.BIOME_FLOOR_RULES.get());
        int oldDefault = GreenhouseSettings.REQUIRED_SPECIMENS.get();
        try {
            GreenhouseSettings.REQUIRED_SPECIMENS.set(4);
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of());
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(
                    "minecraft:desert=minecraft:stone*2,minecraft:gravel*3,minecraft:dirt*4",
                    "minecraft:desert=minecraft:sand*8,minecraft:sandstone*4,minecraft:cactus*2"));
            GreenhouseRecipes.clearCache();
            var desert = server.registryAccess().registryOrThrow(Registries.BIOME).getHolder(id("minecraft:desert")).orElseThrow();
            var recipe = GreenhouseRecipes.forServerBiome(desert);
            assertEquals(14, recipe.count());
            assertEquals(List.of(8, 4, 2), recipe.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
            assertEquals(List.of(id("minecraft:sand"), id("minecraft:sandstone"), id("minecraft:cactus")), recipe.specimens());
            assertEquals(GreenhouseRecipes.FloorRequirement.none(), recipe.floor(), "Custom materials should replace the automatic floor plan");
            assertTrue(recipe.requirements().stream().allMatch(material -> !GreenhouseRecipes.isPercentageBased(material) && material.minHeight() == 0),
                    "An explicit override inherited default vegetation placement/density constraints");

            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert=minecraft:sand,minecraft:sandstone,minecraft:cactus"));
            GreenhouseRecipes.clearCache();
            var legacy = GreenhouseRecipes.forServerBiome(desert);
            assertEquals(12, legacy.count());
            assertEquals(List.of(4, 4, 4), legacy.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
            assertEquals(GreenhouseRecipes.FloorRequirement.none(), legacy.floor());
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of("minecraft:desert=90:minecraft:dirt", "minecraft:desert=100:minecraft:sand"));
            GreenhouseRecipes.clearCache();
            assertEquals(new GreenhouseRecipes.FloorRequirement(List.of(id("minecraft:sand")), 100),
                    GreenhouseRecipes.forServerBiome(desert).floor(), "Explicit floor rule did not apply to a custom material recipe");
        } finally {
            GreenhouseSettings.REQUIRED_SPECIMENS.set(oldDefault);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(oldRules);
            GreenhouseSettings.BIOME_FLOOR_RULES.set(oldFloors);
            GreenhouseRecipes.clearCache();
        }
    }

    @Test
    void invalidAmountsDuplicateTypesAndUnboundedRecipesAreRejected() {
        assertTrue(GreenhouseSettings.validateMaterialRule("minecraft:desert=minecraft:sand*8,minecraft:sandstone*4,minecraft:cactus*2"));
        assertTrue(GreenhouseSettings.validateMaterialRule(" example:biome = minecraft:stone * 2, minecraft:dirt "));
        for (String invalid : List.of("minecraft:desert=minecraft:sand*0", "minecraft:desert=minecraft:sand*-1",
                "minecraft:desert=minecraft:sand*65", "minecraft:desert=minecraft:sand*1.5", "minecraft:desert=minecraft:sand*",
                "minecraft:desert=minecraft:sand*2*3", "minecraft:desert=minecraft:sand,minecraft:sand*2",
                "minecraft:desert=minecraft:sand*99999999999999999999999999999",
                "minecraft:desert=minecraft:sand*2,", "minecraft:desert=#minecraft:sand*2")) {
            assertFalse(GreenhouseSettings.validateMaterialRule(invalid), invalid);
        }
        String tooMany = "example:biome=" + java.util.stream.IntStream.range(0, 33)
                .mapToObj(index -> "example:block_" + index + "*1").collect(java.util.stream.Collectors.joining(","));
        assertFalse(GreenhouseSettings.validateMaterialRule(tooMany));
        var same = new GreenhouseRecipes.MaterialRequirement(id("minecraft:sand"), 8);
        assertThrows(IllegalArgumentException.class, () -> new GreenhouseRecipes.Recipe(CropClimate.HOT, List.of(same, same)));
        assertThrows(IllegalArgumentException.class, () -> new GreenhouseRecipes.MaterialRequirement(id("minecraft:sand"), 0));
    }

    @Test
    void floorRulesValidateAlternativeGroundCoverageAndBoundPlacementMetadata() {
        assertTrue(GreenhouseSettings.validateFloorRule("minecraft:desert=100:minecraft:sand"));
        assertTrue(GreenhouseSettings.validateFloorRule(" example:forest = 90:minecraft:dirt, minecraft:grass_block "));
        assertEquals(new GreenhouseRecipes.FloorRequirement(List.of(id("minecraft:dirt"), id("minecraft:grass_block")), 90),
                GreenhouseSettings.parseFloor("90:minecraft:dirt,minecraft:grass_block"));
        assertEquals(58, GreenhouseSettings.parseFloor("90:minecraft:dirt").required(64));
        for (String invalid : List.of("minecraft:desert=101:minecraft:sand", "minecraft:desert=-1:minecraft:sand",
                "minecraft:desert=10.5:minecraft:sand", "minecraft:desert=minecraft:sand", "minecraft:desert=90:",
                "minecraft:desert=90:minecraft:sand,minecraft:sand", "minecraft:desert=90:#minecraft:sand",
                "minecraft:desert=90:minecraft:sand,")) {
            assertFalse(GreenhouseSettings.validateFloorRule(invalid), invalid);
        }
        String tooMany = "example:biome=90:" + java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> "example:block_" + index).collect(java.util.stream.Collectors.joining(","));
        assertFalse(GreenhouseSettings.validateFloorRule(tooMany));
        assertThrows(IllegalArgumentException.class, () -> new GreenhouseRecipes.MaterialRequirement(id("minecraft:oak_log"), 16, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new GreenhouseRecipes.MaterialRequirement(id("minecraft:oak_leaves"), 64, 2, 3));
    }

    @Test
    void ominousWoodsRequiresItsSpecificSurvivalMaterialsWhenInstalled(MinecraftServer server) {
        var holder = server.registryAccess().registryOrThrow(Registries.BIOME).getHolder(id("biomesoplenty:ominous_woods"));
        if (holder.isEmpty()) return;
        var recipe = GreenhouseRecipes.forServerBiome(holder.get());
        assertEquals(List.of(id("biomesoplenty:umbran_log"), id("biomesoplenty:umbran_leaves"),
                id("biomesoplenty:dead_leaves"), id("biomesoplenty:umbran_wood")), recipe.specimens());
        assertEquals(List.of(16, 64, 8, 8), recipe.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
        assertEquals(90, recipe.floor().coveragePercent());
    }

    @Test
    void installedNorthstarHabitatsUseNativePlanetGroundAndRockInsteadOfEarthFillers(MinecraftServer server) {
        Map<String, String> planets = Map.of("lunar_", "moon", "martian_", "mars", "mercury_", "mercury",
                "venusian_", "venus", "venus_", "venus");
        Set<ResourceLocation> earthFillers = Set.of(id("minecraft:dirt"), id("minecraft:moss_block"),
                id("minecraft:grass_block"), id("minecraft:stone"));
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var biome : biomes.holders().toList()) {
            ResourceLocation biomeId = biome.key().location();
            if (!biomeId.getNamespace().equals("northstar")) continue;
            String planet = planets.entrySet().stream().filter(entry -> biomeId.getPath().startsWith(entry.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (planet == null) continue;
            var habitat = GreenhouseRecipes.forServerBiome(biome);
            assertTrue(habitat.specimens().stream().noneMatch(earthFillers::contains), "Earth fillers in " + biomeId);
            assertTrue(habitat.specimens().stream().allMatch(block -> block.getNamespace().equals("northstar")
                    && block.getPath().startsWith(planet + "_")), "Wrong planet's features in " + biomeId);
            assertTrue(habitat.specimens().contains(id("northstar:" + planet + "_stone"))
                    || habitat.specimens().contains(id("northstar:" + planet + "_deep_stone")), "Missing native rock in " + biomeId);
            assertTrue(habitat.floor().blocks().stream().noneMatch(earthFillers::contains), "Earth ground in " + biomeId);
        }
        biomes.getHolder(id("northstar:lunar_plains")).ifPresent(biome -> assertEquals(
                List.of(id("northstar:moon_sand")), GreenhouseRecipes.forServerBiome(biome).floor().blocks()));
        biomes.getHolder(id("northstar:venusian_wastes")).ifPresent(biome -> assertEquals(
                List.of(id("northstar:volcanic_ash")), GreenhouseRecipes.forServerBiome(biome).floor().blocks()));
    }

    @Test
    void synchronizedClientCatalogCannotAlterIntegratedServerRecipes(MinecraftServer server) {
        var biome = server.registryAccess().registryOrThrow(Registries.BIOME).getHolder(id("minecraft:plains")).orElseThrow();
        var original = GreenhouseRecipes.forServerBiome(biome);
        var fakeClient = new GreenhouseRecipes.Recipe(CropClimate.HOT, List.of(id("minecraft:diamond_block")), 64);
        try {
            GreenhouseRecipes.acceptServerRecipes(Map.of(id("minecraft:plains"), fakeClient));
            assertEquals(fakeClient, GreenhouseRecipes.forBiome(biome));
            assertNotEquals(fakeClient, GreenhouseRecipes.forServerBiome(biome));
            assertEquals(original, GreenhouseRecipes.forServerBiome(biome));
        } finally {
            GreenhouseRecipes.clearClientRecipes();
        }
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static GreenhouseRecipes.Recipe recipe(MinecraftServer server, String biome) {
        return GreenhouseRecipes.forServerBiome(server.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(id(biome)).orElseThrow());
    }

    private static HabitatSignature habitat(MinecraftServer server, String biome) {
        return habitat(recipe(server, biome));
    }

    private static HabitatSignature habitat(GreenhouseRecipes.Recipe recipe) {
        Set<FeatureSignature> features = recipe.requirements().stream()
                .map(material -> new FeatureSignature(material.block(), material.count(),
                        GreenhouseRecipes.isPercentageBased(material), material.minHeight()))
                .collect(java.util.stream.Collectors.toSet());
        return new HabitatSignature(recipe.climate(), features,
                Set.copyOf(recipe.floor().blocks()), recipe.floor().coveragePercent());
    }

    private record FeatureSignature(ResourceLocation block, int rate, boolean proportional, int minHeight) {}
    private record HabitatSignature(CropClimate climate, Set<FeatureSignature> features,
                                    Set<ResourceLocation> nativeGround, int coveragePercent) {}
}
