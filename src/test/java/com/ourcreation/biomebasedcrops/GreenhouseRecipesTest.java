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
            assertTrue(recipe.specimens().size() >= 3, "A biome must require several distinct native materials: " + biome.key().location());
            for (var block : recipe.specimens()) {
                assertTrue(BuiltInRegistries.BLOCK.containsKey(block), "Missing specimen " + block);
                assertNotEquals(Items.AIR, BuiltInRegistries.BLOCK.get(block).asItem(), "Unplaceable specimen " + block);
            }
        }
        var cherry = GreenhouseRecipes.forServerBiome(biomes.getHolder(id("minecraft:cherry_grove")).orElseThrow());
        assertEquals(id("minecraft:cherry_log"), cherry.specimens().getFirst());
        var mangrove = GreenhouseRecipes.forServerBiome(biomes.getHolder(id("minecraft:mangrove_swamp")).orElseThrow());
        assertEquals(id("minecraft:mangrove_log"), mangrove.specimens().getFirst());
    }

    @Test
    void all64VanillaBiomesHaveDistinctMultiMaterialSignatures(MinecraftServer server) {
        Map<Set<ResourceLocation>, ResourceLocation> palettes = new HashMap<>();
        int count = 0;
        for (var biome : server.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
            if (!biome.key().location().getNamespace().equals("minecraft")) continue;
            var recipe = GreenhouseRecipes.forServerBiome(biome);
            ResourceLocation previous = palettes.put(Set.copyOf(recipe.specimens()), biome.key().location());
            assertEquals(null, previous, "Identical vanilla material palettes: " + previous + " and " + biome.key().location());
            assertTrue(recipe.requirements().stream().allMatch(material -> material.count() >= 1 && material.count() <= 64));
            count++;
        }
        assertEquals(64, count);
    }

    @Test
    void overridesRequireEveryTypeAndPreserveExplicitAndLegacyCounts(MinecraftServer server) {
        List<? extends String> oldRules = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        int oldDefault = GreenhouseSettings.REQUIRED_SPECIMENS.get();
        try {
            GreenhouseSettings.REQUIRED_SPECIMENS.set(4);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(
                    "minecraft:desert=minecraft:stone*2,minecraft:gravel*3,minecraft:dirt*4",
                    "minecraft:desert=minecraft:sand*8,minecraft:sandstone*4,minecraft:cactus*2"));
            GreenhouseRecipes.clearCache();
            var desert = server.registryAccess().registryOrThrow(Registries.BIOME).getHolder(id("minecraft:desert")).orElseThrow();
            var recipe = GreenhouseRecipes.forServerBiome(desert);
            assertEquals(14, recipe.count());
            assertEquals(List.of(8, 4, 2), recipe.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
            assertEquals(List.of(id("minecraft:sand"), id("minecraft:sandstone"), id("minecraft:cactus")), recipe.specimens());

            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert=minecraft:sand,minecraft:sandstone,minecraft:cactus"));
            GreenhouseRecipes.clearCache();
            var legacy = GreenhouseRecipes.forServerBiome(desert);
            assertEquals(12, legacy.count());
            assertEquals(List.of(4, 4, 4), legacy.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
        } finally {
            GreenhouseSettings.REQUIRED_SPECIMENS.set(oldDefault);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(oldRules);
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
    void ominousWoodsRequiresItsSpecificSurvivalMaterialsWhenInstalled(MinecraftServer server) {
        var holder = server.registryAccess().registryOrThrow(Registries.BIOME).getHolder(id("biomesoplenty:ominous_woods"));
        if (holder.isEmpty()) return;
        var recipe = GreenhouseRecipes.forServerBiome(holder.get());
        assertEquals(List.of(id("biomesoplenty:umbran_log"), id("biomesoplenty:umbran_leaves"),
                id("biomesoplenty:dead_leaves"), id("biomesoplenty:umbran_wood")), recipe.specimens());
        assertEquals(List.of(8, 8, 4, 4), recipe.requirements().stream().map(GreenhouseRecipes.MaterialRequirement::count).toList());
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
}
