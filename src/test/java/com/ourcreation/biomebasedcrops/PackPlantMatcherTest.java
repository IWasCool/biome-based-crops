package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class PackPlantMatcherTest {
    @Test
    void northstarCustomPlantFamiliesAreClassifiedWithoutMatchingBuildingBlocks(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("northstar"), "Optional Northstar integration is not installed");
        for (String path : List.of("martian_strawberry_bush", "mars_palm", "mars_sprout", "mars_sprout_big",
                "mars_tulip", "venus_vines", "glowing_venus_vines")) {
            assertCategory("northstar", path, PlantCategory.CROP);
        }
        for (String path : List.of("bloom_fungus", "plate_fungus", "spike_fungus", "tower_fungus")) {
            assertCategory("northstar", path, PlantCategory.SAPLING);
        }
        for (String path : List.of("bloom_fungus_block", "bloom_fungus_stem_block", "plate_fungus_cap_block",
                "plate_fungus_stem_block", "spike_fungus_block", "tower_fungus_cap_block", "tower_fungus_stem_block")) {
            Block block = block("northstar", path);
            assertTrue(CropMatcher.categoryForState(block.defaultBlockState()).isEmpty(), path);
            assertTrue(CropMatcher.profileForItem(new ItemStack(block)).isEmpty(), path);
        }
    }

    @Test
    void spawnAndSupplementariesCustomPlantsAreClassified(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("spawn") || ModList.get().isLoaded("supplementaries"),
                "Optional Spawn/Supplementaries integrations are not installed");
        if (ModList.get().isLoaded("spawn")) {
            assertCategory("spawn", "date_sapling", PlantCategory.SAPLING);
            assertCategory("spawn", "sunflower_plant", PlantCategory.CROP);
            assertTrue(CropMatcher.categoryForState(block("spawn", "date_planks").defaultBlockState()).isEmpty());
            assertTrue(CropMatcher.categoryForState(block("spawn", "potted_date_sapling").defaultBlockState()).isEmpty());
        }
        if (ModList.get().isLoaded("supplementaries")) {
            assertCategory("supplementaries", "flax", PlantCategory.CROP);
            assertCategory("supplementaries", "wild_flax", PlantCategory.CROP);
        }
    }

    @Test
    void realCustomCropSeedsAndSaplingsRespectTheirIndependentCategoryToggles(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("northstar") || ModList.get().isLoaded("spawn"),
                "Optional Northstar/Spawn integrations are not installed");
        if (ModList.get().isLoaded("northstar")) {
            assertItemToggle("northstar", "mars_palm_seeds", Config.ENABLE_CROP_BIOME_DEPENDENCIES);
            assertItemToggle("northstar", "mars_tulip_seeds", Config.ENABLE_CROP_BIOME_DEPENDENCIES);
            assertItemToggle("northstar", "mars_sprout_seeds", Config.ENABLE_CROP_BIOME_DEPENDENCIES);
            assertItemToggle("northstar", "martian_strawberry", Config.ENABLE_CROP_BIOME_DEPENDENCIES);
        }
        if (ModList.get().isLoaded("spawn")) {
            assertItemToggle("spawn", "date_seed", Config.ENABLE_SAPLING_BIOME_DEPENDENCIES);
            assertItemToggle("spawn", "sunflower_seeds", Config.ENABLE_CROP_BIOME_DEPENDENCIES);
            var roasted = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("spawn", "roasted_sunflower_seeds"));
            assertTrue(CropMatcher.profileForItem(new ItemStack(roasted)).isEmpty(), "Prepared seeds received a crop profile");
        }
    }

    private static void assertItemToggle(String namespace, String path, ModConfigSpec.BooleanValue toggle) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Missing actual pack item " + id);
        var item = BuiltInRegistries.ITEM.get(id);
        assertTrue(item instanceof BlockItem, "Expected a real plant BlockItem for " + id);
        boolean previous = toggle.get();
        try {
            toggle.set(true);
            assertTrue(CropMatcher.profileForItem(new ItemStack(item)).isPresent(), "Missing tooltip for " + id);
            toggle.set(false);
            assertFalse(CropMatcher.profileForItem(new ItemStack(item)).isPresent(), "Disabled category still has tooltip for " + id);
        } finally {
            toggle.set(previous);
        }
    }

    private static void assertCategory(String namespace, String path, PlantCategory expected) {
        assertEquals(expected, CropMatcher.categoryForState(block(namespace, path).defaultBlockState()).orElseThrow(),
                namespace + ":" + path);
    }

    private static Block block(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Missing actual pack block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }
}
