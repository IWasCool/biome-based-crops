package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class CropMatcherTest {
    private static boolean originalCrops;
    private static boolean originalSaplings;
    private static List<? extends String> originalRules;
    private static List<? extends String> originalNamespaces;

    @BeforeAll
    static void loadConfig(MinecraftServer server) {
        originalCrops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        originalSaplings = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        originalRules = List.copyOf(Config.CROP_CLIMATE_RULES.get());
        originalNamespaces = List.copyOf(Config.SUPPORTED_CROP_NAMESPACES.get());
    }

    @AfterEach
    void resetConfig() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(originalCrops);
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(originalSaplings);
        Config.CROP_CLIMATE_RULES.set(originalRules);
        Config.SUPPORTED_CROP_NAMESPACES.set(originalNamespaces);
        CropMatcher.clearCaches();
        CropClimateRules.clearCache();
    }

    @Test
    void recognizesVanillaCropFamiliesIncludingNetherWartAndBothCaveVineParts() {
        for (Block block : List.of(Blocks.WHEAT, Blocks.BEETROOTS, Blocks.CARROTS, Blocks.POTATOES,
                Blocks.PUMPKIN_STEM, Blocks.MELON_STEM, Blocks.SWEET_BERRY_BUSH, Blocks.COCOA,
                Blocks.NETHER_WART, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.TORCHFLOWER_CROP,
                Blocks.PITCHER_CROP)) {
            assertEquals(PlantCategory.CROP, CropMatcher.categoryForState(block.defaultBlockState()).orElseThrow(),
                    () -> "Missing crop classification for " + block);
        }
    }

    @Test
    void recognizesVanillaSaplingFamilies() {
        for (Block block : List.of(Blocks.OAK_SAPLING, Blocks.MANGROVE_PROPAGULE, Blocks.BAMBOO,
                Blocks.BAMBOO_SAPLING, Blocks.CACTUS, Blocks.SUGAR_CANE, Blocks.RED_MUSHROOM,
                Blocks.BROWN_MUSHROOM, Blocks.CRIMSON_FUNGUS, Blocks.WARPED_FUNGUS,
                Blocks.AZALEA, Blocks.FLOWERING_AZALEA)) {
            assertEquals(PlantCategory.SAPLING, CropMatcher.categoryForState(block.defaultBlockState()).orElseThrow(),
                    () -> "Missing sapling classification for " + block);
        }
    }

    @Test
    void buildingBlocksAndPreparedFoodsNeverReceivePlantProfiles() {
        for (Block block : List.of(Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_BLOCK, Blocks.BAMBOO_MOSAIC,
                Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM,
                Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN)) {
            assertTrue(CropMatcher.categoryForState(block.defaultBlockState()).isEmpty(), () -> "Classified " + block);
            assertTrue(CropMatcher.profileForItem(new ItemStack(block)).isEmpty(), () -> "Tooltip for " + block);
        }
        for (var item : List.of(Items.COOKED_BEEF, Items.OAK_BOAT, Items.PUMPKIN_PIE, Items.BEETROOT_SOUP,
                Items.BAKED_POTATO, Items.POISONOUS_POTATO, Items.HAY_BLOCK, Items.MUSHROOM_STEW)) {
            assertTrue(CropMatcher.profileForItem(new ItemStack(item)).isEmpty(), () -> "Tooltip for " + item);
        }
    }

    @Test
    void categoryTogglesApplyIndependentlyToItemsAndPlacedPlants() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(false);
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT_SEEDS)).isPresent());
        assertTrue(CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).isPresent());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.OAK_SAPLING)).isEmpty());
        assertTrue(CropMatcher.profileForState(Blocks.OAK_SAPLING.defaultBlockState()).isEmpty());

        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(false);
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT_SEEDS)).isEmpty());
        assertTrue(CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).isEmpty());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.OAK_SAPLING)).isPresent());
        assertTrue(CropMatcher.profileForState(Blocks.OAK_SAPLING.defaultBlockState()).isPresent());
    }

    @Test
    void caveVineHeadBodyAndGlowBerriesUseTheSameClimateProfile() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
        var berries = CropMatcher.profileForItem(new ItemStack(Items.GLOW_BERRIES)).orElseThrow();
        assertEquals(Set.of(CropClimate.NEUTRAL, CropClimate.COLD, CropClimate.WARM), berries.climates());
        assertEquals(berries, CropMatcher.profileForState(Blocks.CAVE_VINES.defaultBlockState()).orElseThrow());
        assertEquals(berries, CropMatcher.profileForState(Blocks.CAVE_VINES_PLANT.defaultBlockState()).orElseThrow());
    }

    @Test
    void rawProduceRecognitionRejectsSubstringAndPreparedFoodMatches() {
        for (String name : List.of("rice", "cabbage", "cabbage_leaf", "cabbage_seeds", "tomato_seeds",
                "tea_leaves", "coffee_beans", "sweet_berries", "glow_berries", "blueberries", "melon_slice",
                "tomatoes", "potatoes")) {
            assertTrue(CropMatcher.looksRawCropItemLike(name), name);
        }
        for (String name : List.of("rice_bag", "rice_bale", "rice_roll_medley", "fried_rice", "hot_cocoa",
                "cornbread", "oak_boat", "steak", "tomato_sauce", "glow_berry_custard",
                "cabbage_rolls", "sweet_berry_cookie", "bamboo_cabinet", "pumpkin_pie", "tree_seeds")) {
            assertFalse(CropMatcher.looksRawCropItemLike(name), name);
        }
        assertTrue(CropMatcher.profileForItem(ItemStack.EMPTY).isEmpty());
    }

    @Test
    void clearingRuleCacheAppliesNewServerRules() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
        Config.CROP_CLIMATE_RULES.set(List.of("wheat=cold"));
        CropClimateRules.clearCache();
        assertEquals(Set.of(CropClimate.COLD),
                CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).orElseThrow().climates());

        Config.CROP_CLIMATE_RULES.set(List.of("wheat=hot"));
        CropClimateRules.clearCache();
        assertEquals(Set.of(CropClimate.HOT),
                CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).orElseThrow().climates());
    }

    @Test
    void clearingDetectionCachesAppliesNewNamespacesWithoutDisablingTypedCrops() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
        Config.SUPPORTED_CROP_NAMESPACES.set(List.of("minecraft"));
        CropMatcher.clearCaches();
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT)).isPresent());

        Config.SUPPORTED_CROP_NAMESPACES.set(List.of());
        CropMatcher.clearCaches();
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT)).isEmpty());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT_SEEDS)).isPresent());
        assertTrue(CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).isPresent());
    }

    @Test
    void farmersDelightCustomAndWildCropsAndMushroomColoniesMatchWhenInstalled() {
        for (String name : List.of("rice", "rice_panicles", "budding_tomatoes", "tomatoes", "tomatoes_on_rope",
                "wild_rice", "wild_beetroots", "wild_cabbages", "wild_carrots", "wild_onions",
                "wild_potatoes", "wild_tomatoes", "brown_mushroom_colony", "red_mushroom_colony")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("farmersdelight", name);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                continue;
            }
            PlantCategory expected = name.contains("mushroom") ? PlantCategory.SAPLING : PlantCategory.CROP;
            assertEquals(expected, CropMatcher.categoryForState(BuiltInRegistries.BLOCK.get(id).defaultBlockState()).orElseThrow(), name);
        }
    }
}
