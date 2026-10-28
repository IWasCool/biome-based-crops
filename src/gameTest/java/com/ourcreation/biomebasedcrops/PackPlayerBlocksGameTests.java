package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses Player Blocks' registered fluid and the normal Minecraft growth/bonemeal paths. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class PackPlayerBlocksGameTests {
    private static final ResourceLocation PLAYER_LIQUID = ResourceLocation.fromNamespaceAndPath("player_blocks", "player_liquid");
    private static final BlockPos LIQUID = new BlockPos(6, 1, 6);

    private PackPlayerBlocksGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_player_blocks")
    public static void playerLiquidHydratesVanillaFarmland(GameTestHelper helper) {
        withConfig(helper, () -> {
            // Desert precipitation cannot supply a false positive for water hydration.
            helper.setBiome(Biomes.DESERT);
            ServerLevel level = helper.getLevel();
            BlockPos crop = plantBesidePlayerLiquid(helper);
            BlockPos farmland = crop.below();
            helper.assertFalse(level.isRainingAt(crop), "Farmland hydration control requires a rainless biome");
            helper.assertValueEqual(level.getBlockState(farmland).getValue(FarmBlock.MOISTURE), 0, "initial farmland moisture");

            // FarmBlock hydrates in randomTick; its scheduled tick only checks survival.
            level.getBlockState(farmland).randomTick(level, farmland, RandomSource.create(4096L));
            helper.assertTrue(level.getBlockState(farmland).is(Blocks.FARMLAND), "Farmland disappeared beside player liquid");
            helper.assertValueEqual(level.getBlockState(farmland).getValue(FarmBlock.MOISTURE), 7,
                    "farmland moisture after ticking beside player liquid");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_player_blocks")
    public static void irrigatedWheatStillUsesBiomeGuardAndNormalBonemealEvent(GameTestHelper helper) {
        withConfig(helper, () -> {
            helper.setBiome(Biomes.SNOWY_PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos crop = plantBesidePlayerLiquid(helper);
            BlockPos farmland = crop.below();
            level.getBlockState(farmland).randomTick(level, farmland, RandomSource.create(4096L));
            helper.assertValueEqual(level.getBlockState(farmland).getValue(FarmBlock.MOISTURE), 7, "cold-biome irrigated farmland moisture");
            helper.assertFalse(CropMatcher.canGrowAt(level, crop, level.getBlockState(crop)), "Cold biome unexpectedly allows test wheat");

            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 0,
                    "wheat age after blocked natural tick beside player liquid");
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
            helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, crop, null), "Player liquid bypassed the normal bonemeal climate guard");
            helper.assertValueEqual(meal.getCount(), 3, "bonemeal remaining after rejected cold-biome use");
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 0, "wheat age after rejected bonemeal");

            helper.setBiome(Biomes.PLAINS);
            helper.assertTrue(CropMatcher.canGrowAt(level, crop, level.getBlockState(crop)), "Plains does not allow the positive-control wheat");
            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 1,
                    "wheat age after matching-biome natural tick beside player liquid");
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, crop, null), "Matching-biome bonemeal failed beside player liquid");
            helper.assertValueEqual(meal.getCount(), 2, "bonemeal remaining after matching-biome use");
            helper.assertTrue(level.getBlockState(crop).getValue(CropBlock.AGE) > 1,
                    "Matching-biome bonemeal did not grow irrigated wheat");
        });
    }

    private static BlockPos plantBesidePlayerLiquid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(PLAYER_LIQUID), "Installed Player Blocks has no player_liquid block");
        Block liquid = BuiltInRegistries.BLOCK.get(PLAYER_LIQUID);
        BlockPos liquidPos = helper.absolutePos(LIQUID);
        BlockPos farmland = liquidPos.east();
        level.setBlock(liquidPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(farmland.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(liquidPos, liquid.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(farmland, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), Block.UPDATE_ALL);
        level.setBlock(farmland.above(), Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(level.getFluidState(liquidPos).is(FluidTags.WATER), "Registered player liquid is absent from the water fluid tag");
        return farmland.above();
    }

    private static void withConfig(GameTestHelper helper, Runnable test) {
        if (!ModList.get().isLoaded("player_blocks")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional Player Blocks irrigation test: mod not installed");
            helper.succeed();
            return;
        }
        helper.assertTrue(Config.SPEC.isLoaded(), "Crop server config is unavailable for the Player Blocks integration test");
        boolean natural = Config.BLOCK_NATURAL_GROWTH.get();
        boolean bonemeal = Config.BLOCK_BONEMEAL_GROWTH.get();
        boolean crops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        try {
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(true);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            helper.setDayTime(1000);
            test.run();
        } finally {
            Config.BLOCK_NATURAL_GROWTH.set(natural);
            Config.BLOCK_BONEMEAL_GROWTH.set(bonemeal);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(crops);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
        }
        helper.succeed();
    }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        private AlwaysGrowRandom() {
            super(4096L);
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
