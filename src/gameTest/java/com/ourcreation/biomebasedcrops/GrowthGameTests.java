package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the real random-tick dispatch, event bus, and loaded chunk lookup. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GrowthGameTests {
    private static final BlockPos PLANT = new BlockPos(6, 2, 6);

    private GrowthGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void biomeOverridesControlTicksAndBonemealAndReloadCleanly(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.WHEAT.defaultBlockState(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);

            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:snowy_plains"));
            CropClimateRules.clearCache();
            helper.assertTrue(CropMatcher.canGrowAt(level, pos, level.getBlockState(pos)), "Explicit biome did not override wheat's climate");
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 1, "allowed wheat age");
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, pos, null), "Explicit biome rejected bonemeal");
            helper.assertValueEqual(meal.getCount(), 2, "allowed bonemeal count");

            level.setBlock(pos, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            helper.setBiome(Biomes.PLAINS);
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 0, "excluded biome grew wheat");
            helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "Excluded biome accepted bonemeal");
            helper.assertValueEqual(meal.getCount(), 2, "blocked bonemeal count");

            Config.CROP_BIOME_RULES.set(List.of("wheat=*"));
            CropClimateRules.clearCache();
            helper.setBiome(Biomes.SNOWY_PLAINS);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 1, "wildcard wheat age");
            Config.CROP_BIOME_RULES.set(List.of());
            CropClimateRules.clearCache();
            assertWrongBiome(helper, pos);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void biomeTagOverridesAlsoApplyToSaplingsAndCategoryToggles(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.OAK_SAPLING.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            Config.CROP_BIOME_RULES.set(List.of("oak_sapling=#minecraft:is_jungle"));
            CropClimateRules.clearCache();
            helper.setBiome(Biomes.JUNGLE);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(SaplingBlock.STAGE), 1, "allowed sapling stage");

            helper.setBiome(Biomes.PLAINS);
            assertWrongBiome(helper, pos);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(false);
            helper.assertTrue(CommonEvents.canGrowNaturally(level, pos, level.getBlockState(pos)), "Disabled category kept biome override");
            helper.assertTrue(CommonEvents.canGrowWithFertilizer(level, pos, level.getBlockState(pos)), "Disabled category blocked fertilizer");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void wheatRandomTickUsesBiomeAndNaturalToggle(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.WHEAT.defaultBlockState(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 0, "blocked wheat age");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 1, "unrestricted wheat age");

            Config.BLOCK_NATURAL_GROWTH.set(true);
            helper.setBiome(Biomes.PLAINS);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 2, "matching-biome wheat age");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void cactusCannotPlaceItsNextSegmentInWrongBiome(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.CACTUS.defaultBlockState().setValue(CactusBlock.AGE, 15),
                    Blocks.SAND.defaultBlockState());
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertTrue(level.isEmptyBlock(pos.above()), "Blocked cactus placed a new segment");
            helper.assertValueEqual(level.getBlockState(pos).getValue(CactusBlock.AGE), 15, "blocked cactus age");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            randomTick(level, pos);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.CACTUS), "Unrestricted cactus failed to grow");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void sugarCaneCannotPlaceItsNextSegmentInWrongBiome(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(PLANT);
            level.setBlock(pos.below().east(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            plant(helper, Blocks.SUGAR_CANE.defaultBlockState().setValue(SugarCaneBlock.AGE, 15),
                    Blocks.SAND.defaultBlockState());
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertTrue(level.isEmptyBlock(pos.above()), "Blocked sugar cane placed a new segment");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            randomTick(level, pos);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.SUGAR_CANE), "Unrestricted sugar cane failed to grow");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void netherWartRandomTickChecksClimate(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.NETHER_WART.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState());
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(NetherWartBlock.AGE), 0, "blocked nether wart age");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(NetherWartBlock.AGE), 1, "unrestricted nether wart age");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void saplingRandomTickHonorsItsDependencySwitch(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.ACACIA_SAPLING.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(SaplingBlock.STAGE), 0, "blocked sapling stage");

            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(false);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(SaplingBlock.STAGE), 1, "unrestricted sapling stage");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void bambooRandomTickChecksClimateBeforeExtending(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.BAMBOO.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            assertWrongBiome(helper, pos);
            randomTick(level, pos);
            helper.assertTrue(level.isEmptyBlock(pos.above()), "Blocked bamboo placed a new segment");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            randomTick(level, pos);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.BAMBOO), "Unrestricted bamboo failed to grow");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void mushroomRandomTickCannotSpreadInWrongBiome(GameTestHelper helper) {
        withConfig(helper, () -> {
            // The pack remaps snowy plains to cold, which mushrooms normally allow.
            // A specific test rule keeps the negative control independent of datapacks.
            List<String> rules = new ArrayList<>(Config.CROP_CLIMATE_RULES.get());
            rules.addFirst("minecraft:brown_mushroom=hot");
            Config.CROP_CLIMATE_RULES.set(rules);
            CropClimateRules.clearCache();
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(PLANT);
            // Mycelium permits spreading regardless of sky light. The deterministic
            // walk advances five blocks diagonally across this continuous floor.
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    level.setBlock(pos.offset(x, -1, z), Blocks.MYCELIUM.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.setBlock(pos, Blocks.BROWN_MUSHROOM.defaultBlockState(), Block.UPDATE_ALL);
            assertWrongBiome(helper, pos);
            BlockPos destination = pos.offset(5, 0, 5);
            level.getBlockState(pos).randomTick(level, pos, new MushroomSpreadRandom());
            helper.assertTrue(level.isEmptyBlock(destination), "Blocked mushroom spread to a new block");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            level.getBlockState(pos).randomTick(level, pos, new MushroomSpreadRandom());
            helper.assertTrue(level.getBlockState(destination).is(Blocks.BROWN_MUSHROOM),
                    "Unrestricted mushroom did not spread along the deterministic path");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void cropDependencySwitchAllowsNaturalGrowth(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.WHEAT.defaultBlockState(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            assertWrongBiome(helper, pos);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(false);
            randomTick(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 1, "wheat age with crop dependencies disabled");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void farmersDelightRichSoilBoostHonorsNaturalGrowthToggle(GameTestHelper helper) {
        if (!ModList.get().isLoaded("farmersdelight")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional Farmer's Delight rich-soil GameTest: mod not installed");
            helper.succeed();
            return;
        }
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos crop = helper.absolutePos(PLANT);
            BlockPos soil = crop.below();
            BlockPos water = soil.east();
            BlockState previousCrop = level.getBlockState(crop);
            BlockState previousSoil = level.getBlockState(soil);
            BlockState previousWater = level.getBlockState(water);
            try {
                Block richFarmland = BuiltInRegistries.BLOCK.get(
                        ResourceLocation.fromNamespaceAndPath("farmersdelight", "rich_soil_farmland"));
                helper.assertFalse(richFarmland == Blocks.AIR, "Installed Farmer's Delight lacks rich soil farmland");
                level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                plant(helper, Blocks.WHEAT.defaultBlockState(),
                        richFarmland.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                assertWrongBiome(helper, crop);
                // Tick the soil, not the crop: FD directly applies a growth boost
                // after CropGrowEvent.Pre, bypassing the plant tick and BonemealEvent.
                randomTick(level, soil);
                helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 0,
                        "Rich-soil boost bypassed the wrong-biome natural-growth restriction");

                Config.BLOCK_NATURAL_GROWTH.set(false);
                randomTick(level, soil);
                helper.assertTrue(level.getBlockState(crop).getValue(CropBlock.AGE) > 0,
                        "Rich soil remained blocked when natural-growth restrictions were disabled");
            } finally {
                level.setBlock(crop, previousCrop, Block.UPDATE_ALL);
                level.setBlock(soil, previousSoil, Block.UPDATE_ALL);
                level.setBlock(water, previousWater, Block.UPDATE_ALL);
            }
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void bonemealToggleIsIndependentAndBlockedUseConsumesNothing(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.WHEAT.defaultBlockState(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            assertWrongBiome(helper, pos);
            Config.BLOCK_NATURAL_GROWTH.set(false);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
            helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "Wrong-biome bonemeal was accepted");
            helper.assertValueEqual(meal.getCount(), 3, "bonemeal count after blocked use");
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 0, "wheat age after blocked bonemeal");

            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(false);
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, pos, null), "Bonemeal remained blocked after its toggle was disabled");
            helper.assertValueEqual(meal.getCount(), 2, "bonemeal count after successful use");
            helper.assertTrue(level.getBlockState(pos).getValue(CropBlock.AGE) > 0, "Allowed bonemeal did not grow wheat");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void bonemealCanGenerateTreesWhenNaturalGrowthIsBlocked(GameTestHelper helper) {
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.ACACIA_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1),
                    Blocks.DIRT.defaultBlockState());
            assertWrongBiome(helper, pos);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
            helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "Wrong-biome tree bonemeal was accepted");
            helper.assertValueEqual(meal.getCount(), 3, "bonemeal count after blocked tree use");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.ACACIA_SAPLING), "Blocked tree bonemeal replaced the sapling");
            Config.BLOCK_BONEMEAL_GROWTH.set(false);
            // The first nextFloat for this seed is 0.09789, below the sapling's 0.45 threshold.
            level.random.setSeed(4096L);
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, pos, null), "Tree bonemeal was rejected");
            helper.assertFalse(level.getBlockState(pos).is(Blocks.ACACIA_SAPLING),
                    "Natural-growth restrictions incorrectly blocked a bonemeal tree feature");
            helper.assertValueEqual(meal.getCount(), 2, "bonemeal count after growing a tree");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void regulatorsCheckAllCandidatesAndCurrentPowerModes(GameTestHelper helper) {
        if (!hasToughAsNails(helper)) {
            return;
        }
        withConfig(helper, () -> {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            ServerLevel level = helper.getLevel();
            BlockPos crop = helper.absolutePos(PLANT);
            BlockPos first = crop.west();
            BlockPos second = crop.east();
            Block regulator = regulatorBlock();
            CropProfile cold = new CropProfile(Set.of(CropClimate.COLD));
            CropProfile neutral = new CropProfile(Set.of(CropClimate.NEUTRAL));
            // Use real registered TAN properties, exercising identity-keyed state
            // access as well as the pure mode mapping covered by JUnit.
            assertRegulatorClimates(helper, regulatorState(regulator, true, false, false), Set.of());
            assertRegulatorClimates(helper, regulatorState(regulator, false, true, true), Set.of());
            assertRegulatorClimates(helper, regulatorState(regulator, true, true, false),
                    Set.of(CropClimate.ICY, CropClimate.COLD, CropClimate.NEUTRAL));
            assertRegulatorClimates(helper, regulatorState(regulator, true, false, true),
                    Set.of(CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT));
            assertRegulatorClimates(helper, regulatorState(regulator, true, true, true), Set.of(CropClimate.NEUTRAL));
            level.setBlock(first, regulatorState(regulator, true, false, false), Block.UPDATE_ALL);
            level.setBlock(second, regulatorState(regulator, true, false, true), Block.UPDATE_ALL);
            helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop, cold), "Heating or inactive regulator supported cold crops");

            level.setBlock(second, regulatorState(regulator, true, true, false), Block.UPDATE_ALL);
            helper.assertTrue(ThermoregulatorDetector.canSupportProfile(level, crop, cold), "Inactive regulator hid a later active cooling regulator");
            CropProfile biomeRestricted = new CropProfile(Set.of(CropClimate.COLD),
                    List.of(BiomeSelector.parse("minecraft:jungle").orElseThrow()));
            helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop, biomeRestricted),
                    "Cooling regulator bypassed an explicit biome requirement");
            level.setBlock(second, regulatorState(regulator, false, true, false), Block.UPDATE_ALL);
            helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop, cold), "Disabled regulator remained active");
            level.setBlock(second, regulatorState(regulator, true, true, true), Block.UPDATE_ALL);
            helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop, cold), "Both fuels incorrectly provided a cold climate");
            helper.assertTrue(ThermoregulatorDetector.canSupportProfile(level, crop, neutral), "Both fuels did not provide a neutral climate");

            level.removeBlock(second, false);
            helper.assertFalse(ThermoregulatorDetector.hasThermoregulatorNearby(level, crop), "Removed or inactive regulator remained cached");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "crop_growth")
    public static void regulatorRangeIncludesLoadedChunkEdgesWithoutLoadingRemoteChunks(GameTestHelper helper) {
        if (!hasToughAsNails(helper)) {
            return;
        }
        withConfig(helper, () -> {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int boundaryX = ((origin.getX() >> 4) + 1) << 4;
            BlockPos crop = new BlockPos(boundaryX - 1, origin.getY() + 2, origin.getZ() + 6);
            BlockPos machine = crop.east();
            BlockState previous = level.getBlockState(machine);
            CropProfile cold = new CropProfile(Set.of(CropClimate.COLD));
            try {
                level.setBlock(machine, regulatorState(regulatorBlock(), true, true, false), Block.UPDATE_ALL);
                helper.assertTrue(ThermoregulatorDetector.canSupportProfile(level, crop, cold), "Loaded regulator across a chunk boundary was missed");
                helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop.west(4), cold), "Regulator outside the horizontal radius was counted");
                helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop.below(4), cold), "Regulator above the vertical range was counted");
            } finally {
                level.setBlock(machine, previous, Block.UPDATE_ALL);
            }

            BlockPos remote = new BlockPos(1_000_000, crop.getY(), 1_000_000);
            helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                    "Remote test chunk was unexpectedly loaded before lookup");
            helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, remote, cold), "An unloaded area supplied an artificial climate");
            for (int x = (remote.getX() - 4) >> 4; x <= (remote.getX() + 4) >> 4; x++) {
                for (int z = (remote.getZ() - 4) >> 4; z <= (remote.getZ() + 4) >> 4; z++) {
                    helper.assertTrue(level.getChunkSource().getChunkNow(x, z) == null, "Regulator lookup loaded a remote chunk");
                }
            }
        });
    }

    private static BlockPos plant(GameTestHelper helper, BlockState state, BlockState soil) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(PLANT);
        level.setBlock(pos.below(), soil, Block.UPDATE_ALL);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return pos;
    }

    private static void randomTick(ServerLevel level, BlockPos pos) {
        // Go through BlockStateBase.randomTick so the production mixin is exercised.
        level.getBlockState(pos).randomTick(level, pos, new AlwaysGrowRandom());
    }

    private static void assertWrongBiome(GameTestHelper helper, BlockPos pos) {
        helper.assertFalse(CropMatcher.canGrowAt(helper.getLevel(), pos, helper.getLevel().getBlockState(pos)),
                "Test plant does not have an incompatible biome; the test cannot verify a growth restriction");
    }

    private static boolean hasToughAsNails(GameTestHelper helper) {
        if (ModList.get().isLoaded("toughasnails")) {
            return true;
        }
        BiomeBasedCrops.LOGGER.info("Skipping optional Tough As Nails GameTest: mod not installed");
        helper.succeed();
        return false;
    }

    private static Block regulatorBlock() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("toughasnails", "thermoregulator"));
    }

    private static BlockState regulatorState(Block block, boolean enabled, boolean cooling, boolean heating) {
        return block.defaultBlockState().setValue(BlockStateProperties.ENABLED, enabled)
                .setValue(modeProperty(block, "cooling"), cooling).setValue(modeProperty(block, "heating"), heating);
    }

    private static BooleanProperty modeProperty(Block block, String name) {
        if (block.getStateDefinition().getProperty(name) instanceof BooleanProperty property) {
            return property;
        }
        throw new IllegalStateException("Tough As Nails thermoregulator lacks boolean state " + name);
    }

    private static void assertRegulatorClimates(GameTestHelper helper, BlockState state, Set<CropClimate> expected) {
        for (CropClimate climate : CropClimate.values()) {
            helper.assertTrue(ThermoregulatorDetector.supportsClimates(state, Set.of(climate)) == expected.contains(climate),
                    "Actual TAN state " + state + " incorrectly supports " + climate + "; expected " + expected);
        }
    }

    private static void withConfig(GameTestHelper helper, Runnable test) {
        boolean natural = Config.BLOCK_NATURAL_GROWTH.get();
        boolean bonemeal = Config.BLOCK_BONEMEAL_GROWTH.get();
        boolean crops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        boolean saplings = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        int radius = Config.THERMOREGULATOR_RADIUS.get();
        List<? extends String> rules = List.copyOf(Config.CROP_CLIMATE_RULES.get());
        List<? extends String> biomeRules = List.copyOf(Config.CROP_BIOME_RULES.get());
        try {
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(true);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            Config.THERMOREGULATOR_RADIUS.set(4);
            Config.CROP_BIOME_RULES.set(List.of());
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            helper.setDayTime(1000);
            helper.setBiome(Biomes.SNOWY_PLAINS);
            test.run();
        } finally {
            Config.BLOCK_NATURAL_GROWTH.set(natural);
            Config.BLOCK_BONEMEAL_GROWTH.set(bonemeal);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(crops);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(saplings);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            Config.THERMOREGULATOR_RADIUS.set(radius);
            Config.CROP_CLIMATE_RULES.set(rules);
            Config.CROP_BIOME_RULES.set(biomeRules);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
        }
        helper.succeed();
    }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        private AlwaysGrowRandom() {
            super(0L);
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }

    private static final class MushroomSpreadRandom extends LegacyRandomSource {
        private MushroomSpreadRandom() {
            super(0L);
        }

        @Override
        public int nextInt(int bound) {
            return bound == 3 ? 2 : 0;
        }
    }
}
