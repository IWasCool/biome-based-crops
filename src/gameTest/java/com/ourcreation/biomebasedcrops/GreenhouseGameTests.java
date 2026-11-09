package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseGameTests {
    private static final BlockPos ANCHOR = new BlockPos(8, 2, 8);

    private GreenhouseGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouses")
    public static void fireIceAndExtinguishingChangeRealOutdoorCropGrowth(GameTestHelper helper) {
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            helper.setBiome(Biomes.PLAINS);
            BlockPos crop = helper.absolutePos(ANCHOR);
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)), "Native plains wheat failed");
            BlockPos source = crop.east(2);
            level.setBlock(source, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.effectiveClimate(level, crop) == CropClimate.COLD, "Blue ice did not cool outdoor crop");
            helper.assertFalse(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)), "Cooling did not restrict wheat");
            level.setBlock(source.west(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.effectiveClimate(level, crop) == CropClimate.NEUTRAL, "Heat and cooling did not cancel");
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)), "Balanced outdoor crop did not grow");
            level.setBlock(source.west(), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.effectiveClimate(level, crop) == CropClimate.COLD, "Extinguished campfire still supplied heat");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouses")
    public static void completeDesertHouseEmulatesExactBiomeForTicksAndBonemeal(GameTestHelper helper) {
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            helper.setBiome(Biomes.PLAINS);
            BlockPos core = buildHouse(helper, "minecraft:desert", Blocks.SAND.defaultBlockState());
            level.setBlock(core.west(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(core.north(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos crop = core.east();
            plant(level, crop);
            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:desert"));
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, crop).valid(), "Complete desert greenhouse failed: " + GreenhouseEnvironment.inspect(level, crop).status());
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)), "Greenhouse did not satisfy exact biome rule");
            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 1, "greenhouse wheat age");
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 2);
            helper.assertTrue(BoneMealItem.applyBonemeal(meal, level, crop, null), "Valid greenhouse rejected bonemeal");
            helper.assertValueEqual(meal.getCount(), 1, "greenhouse bonemeal count");
            CropProfile jungle = new CropProfile(Set.of(CropClimate.WARM), List.of(BiomeSelector.parse("#minecraft:is_jungle").orElseThrow()));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, crop, jungle), "Desert house impersonated a jungle tag");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouses")
    public static void missingSpecimensRoofAndOpenDoorPreventBiomeEmulation(GameTestHelper helper) {
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            helper.setBiome(Biomes.PLAINS);
            BlockPos core = buildHouse(helper, "minecraft:plains", Blocks.STONE.defaultBlockState());
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).status().endsWith("missing_specimens"), "Stone was counted as plains flora");
            for (int x = -2; x <= 2; x++) {
                level.setBlock(core.offset(x, -1, 1), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).valid(), "Completed plains greenhouse was not valid");
            BlockPos door = core.north(3);
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, core).enclosed(), "Open door sealed greenhouse");
            level.setBlock(door, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(core.above(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, core).enclosed(), "Roof opening sealed greenhouse");
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(core.offset(x, 3, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).status().endsWith("no_glass"), "Opaque roof passed as a greenhouse skylight");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouses")
    public static void enclosedHeatCanSpanIcyToHotAndNearbyOtherCoreCannotMaskRoom(GameTestHelper helper) {
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            helper.setBiome(Biomes.SNOWY_PLAINS);
            BlockPos core = buildHouse(helper, "minecraft:desert", Blocks.SAND.defaultBlockState());
            for (BlockPos source : List.of(core.west(), core.east(), core.north(), core.south())) {
                level.setBlock(source, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockPos crop = core.east(2);
            plant(level, crop);
            BlockPos unrelatedCore = core.east(3);
            level.setBlock(unrelatedCore, GreenhouseContent.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            ((GreenhouseCoreBlockEntity) level.getBlockEntity(unrelatedCore)).setTargetBiome(ResourceLocation.withDefaultNamespace("jungle"));
            GreenhouseEnvironment.clearCache();
            GreenhouseEnvironment.Inspection inspection = GreenhouseEnvironment.inspect(level, crop);
            helper.assertTrue(inspection.valid(), "Complete heated room did not span icy to hot: " + inspection.status());
            helper.assertTrue(inspection.climate() == CropClimate.HOT, "Enclosed heat was capped before hot");
            helper.assertTrue(inspection.targetBiome().equals(ResourceLocation.withDefaultNamespace("desert")),
                    "Nearer unrelated core masked the crop's connected greenhouse");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouses")
    public static void activeToughAsNailsRegulatorCanMeetGreenhouseTemperature(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional greenhouse thermoregulator test: Tough As Nails not installed");
            helper.succeed();
            return;
        }
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            helper.setBiome(Biomes.PLAINS);
            BlockPos core = buildHouse(helper, "minecraft:desert", Blocks.SAND.defaultBlockState());
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Block regulator = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toughasnails:thermoregulator"));
            var heating = (net.minecraft.world.level.block.state.properties.BooleanProperty) regulator.getStateDefinition().getProperty("heating");
            var cooling = (net.minecraft.world.level.block.state.properties.BooleanProperty) regulator.getStateDefinition().getProperty("cooling");
            BlockPos at = core.north();
            level.setBlock(at, regulator.defaultBlockState().setValue(BlockStateProperties.ENABLED, true)
                    .setValue(heating, true).setValue(cooling, false), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).valid(), "Active heating regulator did not satisfy hot target");
            helper.assertTrue(GreenhouseEnvironment.effectiveClimate(level, core) == CropClimate.HOT,
                    "Regulated greenhouse reported the native temperature instead of its supported climate");
            level.setBlock(at, level.getBlockState(at).setValue(BlockStateProperties.ENABLED, false), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).status().endsWith("wrong_temperature"),
                    "Disabled regulator continued supporting greenhouse temperature");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouses")
    public static void outsideCropsAndExteriorHeatersCannotUseSealedGreenhouse(GameTestHelper helper) {
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            helper.setBiome(Biomes.PLAINS);
            BlockPos core = buildHouse(helper, "minecraft:desert", Blocks.SAND.defaultBlockState());
            level.setBlock(core.east(4), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(core.west(4), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).status().endsWith("wrong_temperature"), "Exterior heat leaked through glass walls");
            level.setBlock(core.east(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(core.west(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, core).valid(), "Interior heat failed");
            CropProfile desert = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, core.north(4), desert), "Crop outside glass wall borrowed greenhouse biome");
            BlockPos unloaded = new BlockPos(1_000_000, core.getY(), 1_000_000);
            helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null, "Remote chunk already loaded");
            GreenhouseEnvironment.inspect(level, unloaded);
            GreenhouseEnvironment.effectiveClimate(level, unloaded);
            helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null, "Inspection loaded a remote chunk");
        });
    }

    private static BlockPos buildHouse(GameTestHelper helper, String target, BlockState floor) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(ANCHOR);
        for (BlockPos at : BlockPos.betweenClosed(core.offset(-3, -1, -3), core.offset(3, 3, 3))) {
            int dx = Math.abs(at.getX() - core.getX());
            int dz = Math.abs(at.getZ() - core.getZ());
            BlockState state = at.getY() == core.getY() - 1 ? floor
                    : dx == 3 || dz == 3 || at.getY() == core.getY() + 3 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(at, state, Block.UPDATE_ALL);
        }
        level.setBlock(core, GreenhouseContent.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        ((GreenhouseCoreBlockEntity) level.getBlockEntity(core)).setTargetBiome(ResourceLocation.parse(target));
        GreenhouseEnvironment.clearCache();
        return core;
    }

    private static void plant(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void withSettings(GameTestHelper helper, Runnable test) {
        boolean houses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        boolean ambient = GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.get();
        boolean thermo = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        int radius = GreenhouseSettings.RADIUS.get();
        int count = GreenhouseSettings.REQUIRED_SPECIMENS.get();
        List<? extends String> rules = Config.CROP_BIOME_RULES.get();
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.set(true);
            GreenhouseSettings.RADIUS.set(4);
            GreenhouseSettings.REQUIRED_SPECIMENS.set(4);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            Config.CROP_BIOME_RULES.set(List.of());
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
            helper.setDayTime(1000);
            test.run();
        } finally {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(houses);
            GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.set(ambient);
            GreenhouseSettings.RADIUS.set(radius);
            GreenhouseSettings.REQUIRED_SPECIMENS.set(count);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermo);
            Config.CROP_BIOME_RULES.set(rules);
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
        }
        helper.succeed();
    }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        AlwaysGrowRandom() { super(0L); }
        @Override public int nextInt(int bound) { return 0; }
        @Override public float nextFloat() { return 0.0F; }
    }
}
