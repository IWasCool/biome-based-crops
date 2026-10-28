package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class SnowPlantGameTests {
    private static final BlockPos PLANT = new BlockPos(6, 2, 6);

    private SnowPlantGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "snow_plants")
    public static void coveredCropBonemealChecksContainedPlantBeforeConsuming(GameTestHelper helper) {
        if (!hasSnowMod(helper)) {
            return;
        }
        withConfig(helper, Biomes.DESERT, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = cover(helper, Blocks.SWEET_BERRY_BUSH.defaultBlockState());
            assertWrongBiome(helper, pos);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
            helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "Snow-covered wrong-biome crop accepted bonemeal");
            helper.assertValueEqual(meal.getCount(), 3, "blocked snow-covered crop bonemeal count");
            helper.assertValueEqual(contained(level, pos).getValue(SweetBerryBushBlock.AGE), 0, "blocked covered berry age");

            Config.BLOCK_BONEMEAL_GROWTH.set(false);
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, pos, null), "Covered crop remained blocked after disabling bonemeal restrictions");
            helper.assertValueEqual(meal.getCount(), 2, "allowed snow-covered crop bonemeal count");
            helper.assertTrue(contained(level, pos).getValue(SweetBerryBushBlock.AGE) > 0, "Allowed covered berry did not grow");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "snow_plants")
    public static void coveredVanillaAndBopSaplingsUseTheirOwnClimateRules(GameTestHelper helper) {
        if (!hasSnowMod(helper)) {
            return;
        }
        withConfig(helper, Biomes.SNOWY_PLAINS, () -> {
            ServerLevel level = helper.getLevel();
            List<Block> saplings = new ArrayList<>(List.of(Blocks.ACACIA_SAPLING));
            if (ModList.get().isLoaded("biomesoplenty")) {
                saplings.add(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("biomesoplenty", "palm_sapling")));
            }
            for (Block sapling : saplings) {
                Config.BLOCK_BONEMEAL_GROWTH.set(true);
                BlockState initial = sapling.defaultBlockState();
                BlockPos pos = cover(helper, initial);
                assertWrongBiome(helper, pos);
                ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
                helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "Covered " + sapling + " bypassed climate restrictions");
                helper.assertValueEqual(meal.getCount(), 3, "blocked covered sapling bonemeal count");
                helper.assertValueEqual(contained(level, pos), initial, "blocked covered sapling state");

                Config.BLOCK_BONEMEAL_GROWTH.set(false);
                level.random.setSeed(4096L);
                helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, pos, null), "Covered sapling stayed blocked after disabling its restriction");
                helper.assertValueEqual(meal.getCount(), 2, "allowed covered sapling bonemeal count");
                helper.assertFalse(contained(level, pos).equals(initial), "Allowed covered sapling did not advance");
            }
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "snow_plants")
    public static void coveredCropNaturalTicksStillCheckTheContainedPlant(GameTestHelper helper) {
        if (!hasSnowMod(helper)) {
            return;
        }
        withConfig(helper, Biomes.DESERT, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = cover(helper, Blocks.SWEET_BERRY_BUSH.defaultBlockState());
            assertWrongBiome(helper, pos);
            level.getBlockState(pos).randomTick(level, pos, new AlwaysGrowRandom());
            helper.assertValueEqual(contained(level, pos).getValue(SweetBerryBushBlock.AGE), 0, "blocked natural covered berry age");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            level.getBlockState(pos).randomTick(level, pos, new AlwaysGrowRandom());
            helper.assertValueEqual(contained(level, pos).getValue(SweetBerryBushBlock.AGE), 1, "allowed natural covered berry age");
        });
    }

    private static BlockPos cover(GameTestHelper helper, BlockState plant) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(PLANT);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, plant, Block.UPDATE_ALL);
        Method convert = Class.forName("snownee.snow.Hooks").getMethod("convert", LevelAccessor.class,
                BlockPos.class, BlockState.class, int.class, int.class, boolean.class);
        helper.assertTrue((Boolean) convert.invoke(null, level, pos, plant, 1, Block.UPDATE_ALL, true),
                "Snow! Real Magic! could not cover the test plant");
        helper.assertFalse(level.getBlockState(pos).is(plant.getBlock()), "Native snow conversion left the original block unwrapped");
        helper.assertValueEqual(contained(level, pos), plant, "native snow wrapper contained state");
        return pos;
    }

    private static BlockState contained(ServerLevel level, BlockPos pos) {
        return SnowCoveredPlantResolver.resolve(level, pos, level.getBlockState(pos));
    }

    private static void assertWrongBiome(GameTestHelper helper, BlockPos pos) {
        helper.assertFalse(CropMatcher.canGrowAt(helper.getLevel(), pos, contained(helper.getLevel(), pos)),
                "The test biome permits this plant, so a snow-wrapper restriction cannot be verified");
    }

    private static boolean hasSnowMod(GameTestHelper helper) {
        if (ModList.get().isLoaded("snowrealmagic")) {
            return true;
        }
        BiomeBasedCrops.LOGGER.info("Skipping optional snow-covered plant GameTest: Snow! Real Magic! not installed");
        helper.succeed();
        return false;
    }

    private static void withConfig(GameTestHelper helper, ResourceKey<Biome> biome, CheckedTest test) {
        boolean natural = Config.BLOCK_NATURAL_GROWTH.get();
        boolean bonemeal = Config.BLOCK_BONEMEAL_GROWTH.get();
        boolean crops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        boolean saplings = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        try {
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(true);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            helper.setDayTime(1000);
            helper.setBiome(biome);
            test.run();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Snow! Real Magic! integration API did not match its installed version", e);
        } finally {
            Config.BLOCK_NATURAL_GROWTH.set(natural);
            Config.BLOCK_BONEMEAL_GROWTH.set(bonemeal);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(crops);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(saplings);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
        }
        helper.succeed();
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run() throws ReflectiveOperationException;
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
