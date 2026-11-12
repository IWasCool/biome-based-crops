package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real crop growth and loaded controller checks with greenhouse support switched off. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class LocalCropControllerGameTests {
    private static final ResourceLocation TAN_REGULATOR = ResourceLocation.parse("toughasnails:thermoregulator");

    private LocalCropControllerGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "local_crop_controllers")
    public static void electricalHeatingAndCoolingAllowTicksAndBonemealWithoutGlassOrGreenhouse(GameTestHelper helper) {
        withSettings(helper, () -> verifyActualGrowth(helper, false));
    }

    @GameTest(template = "empty", skyAccess = true, batch = "local_crop_controllers")
    public static void fueledTanHeatingAndCoolingAllowTicksAndBonemealWithoutGlassOrGreenhouse(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        withSettings(helper, () -> verifyActualGrowth(helper, true));
    }

    @GameTest(template = "empty", skyAccess = true, batch = "local_crop_controllers")
    public static void electricalCropCoverageIsAFrontHalfCircleAndWallsStopIt(GameTestHelper helper) {
        withSettings(helper, () -> verifyCoverage(helper, false));
    }

    @GameTest(template = "empty", skyAccess = true, batch = "local_crop_controllers")
    public static void fueledTanCropCoverageIsAFrontHalfCircleAndWallsStopIt(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        withSettings(helper, () -> verifyCoverage(helper, true));
    }

    private static void verifyActualGrowth(GameTestHelper helper, boolean tan) {
        ServerLevel level = helper.getLevel();
        BlockPos machine = helper.absolutePos(new BlockPos(8, 60, 8));
        BlockPos crop = machine.north(2);
        for (CropClimate climate : new CropClimate[] { CropClimate.HOT, CropClimate.COLD }) {
            Config.CROP_CLIMATE_RULES.set(List.of("wheat=" + climate.name().toLowerCase(java.util.Locale.ROOT)));
            Config.CROP_BIOME_RULES.set(List.of());
            CropClimateRules.clearCache();
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            placeController(level, machine, tan);
            helper.assertFalse(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)),
                    "An unpowered controller allowed a wrong-climate crop to grow");
            activateController(level, machine, tan, climate);
            helper.assertFalse(GreenhouseEnvironment.hasSkyGlassAbove(level, crop), "Outdoor fixture unexpectedly has sky glass");
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)),
                    "Active " + climate + " controller still required a greenhouse");
            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 1, "controller-assisted natural crop age");
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, crop, null), "Controller-assisted bonemeal was blocked");
            helper.assertValueEqual(meal.getCount(), 2, "bonemeal consumed after controller-assisted growth");
            helper.assertTrue(level.getBlockState(crop).getValue(CropBlock.AGE) > 1, "Allowed bonemeal did not grow the crop");

            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:desert"));
            CropClimateRules.clearCache();
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)),
                    "Thermal control replaced an explicit biome requirement");
            helper.assertFalse(BoneMealItem.applyBonemeal(meal, level, crop, null), "Thermal control bypassed an exact biome rule for bonemeal");
            helper.assertValueEqual(meal.getCount(), 2, "blocked exact-biome bonemeal consumed an item");

            Config.CROP_BIOME_RULES.set(List.of());
            CropClimateRules.clearCache();
            BlockState stopped = level.getBlockState(machine).setValue(tan ? BlockStateProperties.ENABLED : AirConditionerBlock.ACTIVE, false);
            level.setBlock(machine, stopped, Block.UPDATE_CLIENTS);
            helper.assertFalse(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)),
                    "A stopped controller retained crop support");
            level.removeBlock(machine, false);
        }
    }

    private static void verifyCoverage(GameTestHelper helper, boolean tan) {
        ServerLevel level = helper.getLevel();
        BlockPos machine = helper.absolutePos(new BlockPos(8, 60, 8));
        CropProfile hot = new CropProfile(Set.of(CropClimate.HOT));
        placeController(level, machine, tan);
        activateController(level, machine, tan, CropClimate.HOT);
        for (Direction face : Direction.Plane.HORIZONTAL) {
            level.setBlock(machine, level.getBlockState(machine).setValue(BlockStateProperties.HORIZONTAL_FACING, face), Block.UPDATE_CLIENTS);
            Direction side = face.getClockWise();
            for (BlockPos target : new BlockPos[] { machine.relative(face, 4), machine.relative(face).relative(side, 3),
                    machine.relative(face, 2).relative(side, 3), machine.relative(face, 3).relative(side, 2),
                    machine.relative(face, 2).above(), machine.relative(face, 2).below() }) {
                helper.assertTrue(CommonEvents.canGrow(level, target, hot), "Missing front crop coverage at " + target + " facing " + face);
            }
            for (BlockPos target : new BlockPos[] { machine, machine.relative(side, 2), machine.relative(face.getOpposite()),
                    machine.relative(face, 5), machine.relative(face, 4).relative(side), machine.relative(face, 3).relative(side, 3),
                    machine.relative(face).relative(side, 4), machine.relative(face, 2).above(2) }) {
                helper.assertFalse(CommonEvents.canGrow(level, target, hot), "Controller supported crop outside its front circle at " + target);
            }
            BlockPos barrier = machine.relative(face, 2);
            level.setBlock(barrier, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(CommonEvents.canGrow(level, machine.relative(face, 3), hot), "Controller climate passed through a glass wall");
            helper.assertTrue(CommonEvents.canGrow(level, machine.relative(face).relative(side, 2), hot), "Wall blocked an unrelated clear ray");
            level.removeBlock(barrier, false);

            BlockPos outlet = machine.relative(face);
            level.setBlock(outlet, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(CommonEvents.canGrow(level, machine.relative(face).relative(side, 3), hot), "A blocked outlet still supplied crops to its side");
            level.removeBlock(outlet, false);
            level.setBlock(outlet, Blocks.BAMBOO.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.assertTrue(CommonEvents.canGrow(level, outlet, hot), "The target crop's own collision shape blocked its climate support");
            level.removeBlock(outlet, false);
        }
    }

    private static void placeController(ServerLevel level, BlockPos machine, boolean tan) {
        Block block = tan ? BuiltInRegistries.BLOCK.get(TAN_REGULATOR) : GreenhouseContent.AIR_CONDITIONER.get();
        BlockState state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        if (tan) state = state.setValue(BlockStateProperties.ENABLED, true);
        level.setBlock(machine, state, Block.UPDATE_ALL);
    }

    private static void activateController(ServerLevel level, BlockPos machine, boolean tan, CropClimate climate) {
        if (tan) {
            Container inventory = (Container) level.getBlockEntity(machine);
            boolean heating = climate == CropClimate.HOT;
            inventory.setItem(heating ? 1 : 0, new ItemStack(heating ? Items.COAL : Items.ICE));
            tickRegulator(level.getBlockEntity(machine));
        } else {
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(machine);
            unit.setClimate(climate);
            unit.energyStorage().receiveEnergy(3600, false);
            AirConditionerBlockEntity.serverTick(level, machine, unit.getBlockState(), unit);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickRegulator(T entity) {
        var ticker = entity.getBlockState().getTicker(entity.getLevel(), (BlockEntityType<T>) entity.getType());
        if (ticker == null) throw new IllegalStateException("TAN controller has no server ticker");
        ticker.tick(entity.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity);
    }

    private static void withSettings(GameTestHelper helper, Runnable test) {
        boolean houses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        boolean natural = Config.BLOCK_NATURAL_GROWTH.get(), fertilizer = Config.BLOCK_BONEMEAL_GROWTH.get();
        boolean crops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get(), saplings = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        List<? extends String> climates = List.copyOf(Config.CROP_CLIMATE_RULES.get());
        List<? extends String> biomes = List.copyOf(Config.CROP_BIOME_RULES.get());
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(true);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Config.CROP_BIOME_RULES.set(List.of());
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
            helper.setDayTime(1000);
            helper.setBiome(Biomes.PLAINS);
            test.run();
        } finally {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(houses);
            Config.BLOCK_NATURAL_GROWTH.set(natural);
            Config.BLOCK_BONEMEAL_GROWTH.set(fertilizer);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(crops);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(saplings);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            Config.CROP_CLIMATE_RULES.set(climates);
            Config.CROP_BIOME_RULES.set(biomes);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
        }
        helper.succeed();
    }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        private AlwaysGrowRandom() { super(0L); }
        @Override public int nextInt(int bound) { return 0; }
    }
}
