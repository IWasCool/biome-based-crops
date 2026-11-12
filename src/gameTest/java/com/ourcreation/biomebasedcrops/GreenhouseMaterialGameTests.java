package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseMaterialGameTests {
    private GreenhouseMaterialGameTests() { }

    @GameTest(template = "empty", batch = "survival_material_audit")
    public static void everyRequiredMaterialCanBePlacedAndCollectedInSurvival(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var catalog = GreenhouseRecipes.serverCatalog(level.registryAccess().registryOrThrow(Registries.BIOME));
        Set<ResourceLocation> blocks = new TreeSet<>();
        catalog.values().forEach(recipe -> { blocks.addAll(recipe.specimens()); blocks.addAll(recipe.floor().blocks()); });
        var silk = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
        List<ItemStack> tools = new ArrayList<>();
        tools.add(new ItemStack(Items.SHEARS));
        for (var item : List.of(Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_AXE, Items.NETHERITE_HOE)) {
            var tool = new ItemStack(item);
            tool.enchant(silk, 1);
            tools.add(tool);
        }
        var craftable = server.getRecipeManager().getRecipes().stream()
                .map(holder -> holder.value().getResultItem(level.registryAccess()).getItem()).collect(Collectors.toSet());
        List<String> invalid = new ArrayList<>();
        for (var id : blocks) {
            var block = BuiltInRegistries.BLOCK.get(id);
            var item = block.asItem();
            if (!(item instanceof BlockItem placeable) || placeable.getBlock() != block) {
                invalid.add(id + " has no item that places that block");
                continue;
            }
            boolean harvestable = block.getStateDefinition().getPossibleStates().stream().anyMatch(state -> tools.stream().anyMatch(tool ->
                    Block.getDrops(state, level, helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)),
                            null, player, tool).stream().anyMatch(stack -> stack.is(item))));
            if (!harvestable && !craftable.contains(item)) invalid.add(id + " has no collectible drop or crafting recipe");
        }
        helper.assertTrue(invalid.isEmpty(), "Unusable survival requirements: " + invalid);
        helper.succeed();
    }
}
