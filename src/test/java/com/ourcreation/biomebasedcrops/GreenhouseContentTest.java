package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class GreenhouseContentTest {
    @Test
    void dedicatedCreativeTabContainsEveryModItemExactlyOnce(MinecraftServer server) {
        var tab = GreenhouseContent.CREATIVE_TAB.get();
        assertEquals(id("biome_based_crops:main"), BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
        assertEquals("itemGroup.biome_based_crops",
                assertInstanceOf(TranslatableContents.class, tab.getDisplayName().getContents()).getKey());
        assertTrue(tab.getIconItem().is(GreenhouseContent.AIR_CONDITIONER_ITEM.get()));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, false, server.registryAccess()));
        var displayed = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        var expected = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(BiomeBasedCrops.MODID))
                .collect(Collectors.toSet());
        assertEquals(Set.of(GreenhouseContent.GUIDE.get(), GreenhouseContent.THERMOMETER.get(),
                GreenhouseContent.AIR_CONDITIONER_ITEM.get()), expected);
        assertEquals(expected, new java.util.HashSet<>(displayed));
        assertEquals(expected.size(), displayed.size(), "Creative tab contains duplicate mod items");
        assertTrue(tab.getDisplayItems().stream().allMatch(tab::contains), "Mod items are missing from creative search");
        var orderedTabs = CreativeModeTabRegistry.getSortedCreativeModeTabs();
        int tabIndex = orderedTabs.indexOf(tab);
        int spawnEggIndex = orderedTabs.indexOf(BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.SPAWN_EGGS));
        assertTrue(spawnEggIndex >= 0 && tabIndex > spawnEggIndex,
                "Dedicated mod tab should follow the vanilla tabs without an ordering cycle");
    }

    @Test
    void scannerItemsBlocksAndRecipesAreRemoved(MinecraftServer server) {
        for (String name : List.of("greenhouse_core", "greenhouse_probe")) {
            var removed = id("biome_based_crops:" + name);
            assertFalse(BuiltInRegistries.ITEM.containsKey(removed), "Removed scanner item is still registered");
            assertFalse(BuiltInRegistries.BLOCK.containsKey(removed), "Removed scanner block is still registered");
            assertFalse(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(removed), "Removed scanner entity is still registered");
            assertTrue(server.getRecipeManager().byKey(removed).isEmpty(), "Removed scanner recipe is still craftable");
        }
    }

    @Test
    void guideNetworkPreservesAutomaticRoomSizeAndFloorContext() {
        var forest = id("minecraft:forest");
        var recipe = new GreenhouseRecipes.Recipe(CropClimate.NEUTRAL, List.of(
                new GreenhouseRecipes.MaterialRequirement(id("minecraft:oak_log"), 16, 8, 1),
                new GreenhouseRecipes.MaterialRequirement(id("minecraft:oak_leaves"), 64, 2, 2)),
                new GreenhouseRecipes.FloorRequirement(List.of(id("minecraft:grass_block"), id("minecraft:dirt")), 90));
        var payload = new GreenhouseNetwork.ViewPayload(Map.of(forest, recipe), true, 512, 8192, 256);
        assertEquals(payload, roundTrip(payload));
        var large = new GreenhouseNetwork.ViewPayload(Map.of(), true, 64, 1_048_576, 1_048_576);
        assertEquals(large, roundTrip(large));
        assertThrows(IllegalArgumentException.class,
                () -> new GreenhouseNetwork.ViewPayload(Map.of(), false, 512, 1_048_577, 0));
    }

    @Test
    void completeLoadedBiomeCatalogueSurvivesNetworkRoundTrip(MinecraftServer server) {
        Map<ResourceLocation, GreenhouseRecipes.Recipe> catalogue = GreenhouseRecipes.serverCatalog(
                server.registryAccess().registryOrThrow(Registries.BIOME));
        assertTrue(catalogue.containsKey(id("minecraft:the_end")));
        assertTrue(catalogue.containsKey(id("minecraft:deep_dark")));
        GreenhouseNetwork.ViewPayload decoded = roundTrip(new GreenhouseNetwork.ViewPayload(catalogue));
        assertEquals(catalogue, decoded.recipes());
        assertEquals(0, decoded.contextVolume());
        assertEquals(0, decoded.contextFloorArea());
    }

    @Test
    void networkRejectsUnboundedCatalogueBeforeAllocatingEntries() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1_000_000);
            assertThrows(IllegalArgumentException.class, () -> GreenhouseNetwork.ViewPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void survivalRecipesLoadAndCraftTheirRegisteredItems(MinecraftServer server) {
        for (var plant : List.of(Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS, Items.OAK_SAPLING)) {
            assertCrafts(server, "farmer_field_guide", List.of(
                    new ItemStack(plant), ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.BOOK)),
                    new ItemStack(GreenhouseContent.GUIDE.get()));
        }
        var guideRecipe = (CraftingRecipe) server.getRecipeManager()
                .byKey(id("biome_based_crops:farmer_field_guide")).orElseThrow().value();
        for (var invalid : List.of(List.of(new ItemStack(Items.BOOK)), List.of(new ItemStack(Items.CARROT)),
                List.of(new ItemStack(Items.BOOK), new ItemStack(Items.STONE)))) {
            assertFalse(guideRecipe.matches(CraftingInput.of(invalid.size(), 1, invalid), server.overworld()),
                    "Guide crafting must require a book and a crop, seed, or sapling");
        }
        assertCrafts(server, "air_conditioner", List.of(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.PISTON), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT)),
                new ItemStack(GreenhouseContent.AIR_CONDITIONER_ITEM.get()));
        assertCrafts(server, "thermometer", List.of(
                new ItemStack(Items.GLASS), ItemStack.EMPTY, ItemStack.EMPTY,
                new ItemStack(Items.REDSTONE), ItemStack.EMPTY, ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, ItemStack.EMPTY),
                new ItemStack(GreenhouseContent.THERMOMETER.get()));
        for (String recipe : List.of("farmer_field_guide", "air_conditioner", "thermometer")) {
            assertNotNull(server.getAdvancements().get(id("biome_based_crops:recipes/" + recipe)), "Missing recipe unlock " + recipe);
        }
    }

    private static void assertCrafts(MinecraftServer server, String name, List<ItemStack> grid, ItemStack expected) {
        var holder = server.getRecipeManager().byKey(id("biome_based_crops:" + name)).orElseThrow();
        CraftingRecipe recipe = assertInstanceOf(CraftingRecipe.class, holder.value());
        CraftingInput input = CraftingInput.of(3, 3, grid);
        assertTrue(recipe.matches(input, server.overworld()), name + " cannot be crafted with its documented ingredients");
        assertTrue(ItemStack.isSameItemSameComponents(expected, recipe.assemble(input, server.registryAccess())));
        assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(ItemStack.EMPTY)), server.overworld()));
    }

    private static GreenhouseNetwork.ViewPayload roundTrip(GreenhouseNetwork.ViewPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GreenhouseNetwork.ViewPayload.CODEC.encode(buffer, payload);
            var decoded = GreenhouseNetwork.ViewPayload.CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes(), "Greenhouse packet left unread data");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
