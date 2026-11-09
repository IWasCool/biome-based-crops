package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
            for (var block : recipe.specimens()) {
                assertTrue(BuiltInRegistries.BLOCK.containsKey(block), "Missing specimen " + block);
            }
        }
        var cherry = GreenhouseRecipes.forServerBiome(biomes.getHolder(id("minecraft:cherry_grove")).orElseThrow());
        assertEquals(id("minecraft:cherry_log"), cherry.specimens().getFirst());
        var mangrove = GreenhouseRecipes.forServerBiome(biomes.getHolder(id("minecraft:mangrove_swamp")).orElseThrow());
        assertEquals(id("minecraft:mangrove_log"), mangrove.specimens().getFirst());
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
