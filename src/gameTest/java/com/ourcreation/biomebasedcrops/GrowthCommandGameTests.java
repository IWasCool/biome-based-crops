package com.ourcreation.biomebasedcrops;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies command changes reach actual tick and fertilizer guards immediately. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GrowthCommandGameTests {
    private GrowthCommandGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void commandsIndependentlyDisableAndRestoreGrowthRestrictions(GameTestHelper helper)
            throws CommandSyntaxException {
        boolean natural = Config.BLOCK_NATURAL_GROWTH.get();
        boolean bonemeal = Config.BLOCK_BONEMEAL_GROWTH.get();
        boolean crops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        boolean trees = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        boolean thermoregulators = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        try {
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(true);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            helper.setDayTime(1000);
            helper.setBiome(Biomes.SNOWY_PLAINS);

            ServerLevel level = helper.getLevel();
            BlockPos wheat = helper.absolutePos(new BlockPos(4, 2, 4));
            BlockPos tree = helper.absolutePos(new BlockPos(9, 2, 9));
            level.setBlock(wheat.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7),
                    Block.UPDATE_ALL);
            level.setBlock(tree.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            resetPlants(level, wheat, tree);
            helper.assertFalse(CropMatcher.canGrowAt(level, wheat, level.getBlockState(wheat)),
                    "Wheat must start in an incompatible biome");
            helper.assertFalse(CropMatcher.canGrowAt(level, tree, level.getBlockState(tree)),
                    "Acacia must start in an incompatible biome");
            randomTick(level, wheat);
            randomTick(level, tree);
            assertAges(helper, wheat, tree, 0, 0);
            helper.assertFalse(CommonEvents.canGrowWithFertilizer(level, wheat, level.getBlockState(wheat)),
                    "Wheat fertilizer must initially be blocked");
            helper.assertFalse(CommonEvents.canGrowWithFertilizer(level, tree, level.getBlockState(tree)),
                    "Tree fertilizer must initially be blocked");

            execute(level.getServer(), "biomebasedcrops crops disable");
            randomTick(level, wheat);
            randomTick(level, tree);
            assertAges(helper, wheat, tree, 1, 0);
            helper.assertTrue(CommonEvents.canGrowWithFertilizer(level, wheat, level.getBlockState(wheat)),
                    "Crop command must allow fertilizer immediately");
            helper.assertFalse(CommonEvents.canGrowWithFertilizer(level, tree, level.getBlockState(tree)),
                    "Crop command must preserve tree fertilizer restrictions");

            execute(level.getServer(), "biomebasedcrops trees disable");
            randomTick(level, tree);
            assertAges(helper, wheat, tree, 1, 1);
            helper.assertTrue(CommonEvents.canGrowWithFertilizer(level, tree, level.getBlockState(tree)),
                    "Tree command must allow fertilizer immediately");

            execute(level.getServer(), "biomebasedcrops enable");
            resetPlants(level, wheat, tree);
            randomTick(level, wheat);
            randomTick(level, tree);
            assertAges(helper, wheat, tree, 0, 0);
            helper.assertFalse(CommonEvents.canGrowWithFertilizer(level, wheat, level.getBlockState(wheat)),
                    "Enable command must restore crop fertilizer restrictions");
            helper.assertFalse(CommonEvents.canGrowWithFertilizer(level, tree, level.getBlockState(tree)),
                    "Enable command must restore tree fertilizer restrictions");
        } finally {
            Config.BLOCK_NATURAL_GROWTH.set(natural);
            Config.BLOCK_BONEMEAL_GROWTH.set(bonemeal);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(crops);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(trees);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermoregulators);
            // Commands save to disk; preserve the fixture's original choices too.
            Config.SPEC.save();
        }
        helper.succeed();
    }

    private static void resetPlants(ServerLevel level, BlockPos wheat, BlockPos tree) {
        level.setBlock(wheat, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(tree, Blocks.ACACIA_SAPLING.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void randomTick(ServerLevel level, BlockPos pos) {
        level.getBlockState(pos).randomTick(level, pos, new AlwaysGrowRandom());
    }

    private static void assertAges(GameTestHelper helper, BlockPos wheat, BlockPos tree, int wheatAge, int treeStage) {
        ServerLevel level = helper.getLevel();
        helper.assertValueEqual(level.getBlockState(wheat).getValue(CropBlock.AGE), wheatAge, "wheat age");
        helper.assertValueEqual(level.getBlockState(tree).getValue(SaplingBlock.STAGE), treeStage, "sapling stage");
    }

    private static void execute(MinecraftServer server, String command) throws CommandSyntaxException {
        server.getCommands().getDispatcher().execute(command,
                server.createCommandSourceStack().withPermission(2).withSuppressedOutput());
    }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        private AlwaysGrowRandom() {
            super(0L);
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
