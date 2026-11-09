package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class GreenhouseContentTest {
    @Test
    void sampledCoreTargetSurvivesWorldSaveAndInvalidOldDataIsSafe(MinecraftServer server) {
        BlockPos position = new BlockPos(12, 64, -8);
        var state = GreenhouseContent.CORE.get().defaultBlockState();
        GreenhouseCoreBlockEntity original = new GreenhouseCoreBlockEntity(position, state);
        assertNull(original.targetBiome());
        original.setTargetBiome(id("minecraft:mangrove_swamp"));
        CompoundTag saved = original.saveWithFullMetadata(server.registryAccess());

        GreenhouseCoreBlockEntity loaded = assertInstanceOf(GreenhouseCoreBlockEntity.class,
                BlockEntity.loadStatic(position, state, saved, server.registryAccess()));
        assertEquals(original.targetBiome(), loaded.targetBiome());
        assertEquals(position, loaded.getBlockPos());
        assertEquals("minecraft:mangrove_swamp", loaded.getUpdateTag(server.registryAccess()).getString("TargetBiome"));

        saved.putString("TargetBiome", "Removed Biome!");
        loaded.loadWithComponents(saved, server.registryAccess());
        assertNull(loaded.targetBiome());
        saved.remove("TargetBiome");
        loaded.loadWithComponents(saved, server.registryAccess());
        assertNull(loaded.targetBiome());
    }

    @Test
    void completeLoadedBiomeCatalogueSurvivesNetworkRoundTrip(MinecraftServer server) {
        Map<ResourceLocation, GreenhouseRecipes.Recipe> catalogue = new LinkedHashMap<>();
        server.registryAccess().registryOrThrow(Registries.BIOME).holders().forEach(biome ->
                catalogue.put(biome.key().location(), GreenhouseRecipes.forBiome(biome)));
        assertTrue(catalogue.containsKey(id("minecraft:the_end")));
        assertTrue(catalogue.containsKey(id("minecraft:deep_dark")));
        GreenhouseNetwork.ViewPayload decoded = roundTrip(new GreenhouseNetwork.ViewPayload(catalogue, null));
        assertEquals(catalogue, decoded.recipes());
        assertNull(decoded.inspection());
    }

    @Test
    void surveyNetworkRoundTripPreservesSuccessAndMissingRequirements() {
        var biome = id("minecraft:desert");
        var materials = List.of(id("minecraft:sand"), id("minecraft:cactus"));
        var recipes = Map.of(biome, new GreenhouseRecipes.Recipe(CropClimate.HOT, materials, 4));
        for (String status : List.of("valid", "missing_specimens", "outside", "no_glass")) {
            var report = new GreenhouseEnvironment.Inspection(true, biome, CropClimate.WARM,
                    16, 8, 5, 2, 4, "biome_based_crops.greenhouse.status." + status, materials,
                    List.of(new GreenhouseEnvironment.RequirementProgress(id("minecraft:sand"), 2, 8),
                            new GreenhouseEnvironment.RequirementProgress(id("minecraft:cactus"), 0, 2)), CropClimate.COLD);
            var decoded = roundTrip(new GreenhouseNetwork.ViewPayload(recipes, report));
            assertEquals(report, decoded.inspection());
            assertEquals(recipes, decoded.recipes());
            assertEquals(status.equals("valid"), decoded.inspection().valid());
        }
        var outside = new GreenhouseEnvironment.Inspection(false, null, CropClimate.COLD,
                0, 4, 0, 0, 4, "biome_based_crops.greenhouse.status.no_core", List.of());
        assertEquals(outside, roundTrip(new GreenhouseNetwork.ViewPayload(recipes, outside)).inspection());
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
        assertCrafts(server, "greenhouse_core", List.of(
                new ItemStack(Items.GLASS), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.GLASS),
                new ItemStack(Items.BRICKS), new ItemStack(Items.COMPASS), new ItemStack(Items.BRICKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.OAK_PLANKS)),
                new ItemStack(GreenhouseContent.CORE_ITEM.get()));
        assertCrafts(server, "greenhouse_probe", List.of(
                ItemStack.EMPTY, new ItemStack(Items.GLASS), ItemStack.EMPTY,
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.COPPER_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY),
                new ItemStack(GreenhouseContent.PROBE.get()));
        assertCrafts(server, "farmer_field_guide", List.of(
                new ItemStack(Items.WHEAT), ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.BOOK)),
                new ItemStack(GreenhouseContent.GUIDE.get()));
        for (String recipe : List.of("greenhouse_core", "greenhouse_probe", "farmer_field_guide")) {
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
