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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class AutoGreenhouseGameTests {
    private static final BlockPos CENTER = new BlockPos(8, 2, 8);

    private AutoGreenhouseGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void detectsBiomeWithoutCoreAndGrowsExactBiomeCrop(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            level.setBlock(center.north(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos crop = center.south();
            plant(level, crop);
            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:desert"));
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
            var survey = GreenhouseEnvironment.inspect(level, crop);
            helper.assertTrue(survey.valid(), "Automatic greenhouse invalid: " + survey);
            helper.assertTrue(id("minecraft:desert").equals(survey.targetBiome()), "Wrong automatic biome");
            helper.assertTrue(survey.baseClimate() == CropClimate.NEUTRAL, "Lost native baseline");
            helper.assertValueEqual(survey.requirements().size(), 3, "distinct greenhouse requirements");
            var profile = CropMatcher.profileForState(level.getBlockState(crop));
            helper.assertTrue(profile.isPresent() && profile.get().hasBiomeRule(),
                    "Exact desert crop override was not resolved: state=" + level.getBlockState(crop)
                            + "; profile=" + profile + "; rules=" + Config.CROP_BIOME_RULES.get());
            helper.assertTrue(GreenhouseEnvironment.canSupport(level, crop, profile.orElseThrow()),
                    "Valid automatic house rejected exact crop profile: " + profile + "; survey=" + survey);
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)),
                    "Automatic biome did not support crop: profile=" + profile + "; survey=" + survey);
            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 1, "automatic greenhouse crop age");
            ItemStack bonemeal = new ItemStack(Items.BONE_MEAL, 2);
            helper.assertTrue(BoneMealItem.applyBonemeal(bonemeal, level, crop, null), "Automatic greenhouse rejected bonemeal");
            helper.assertValueEqual(bonemeal.getCount(), 1, "bonemeal after automatic greenhouse growth");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void duplicateCampfiresCannotReplaceAStrongerHeatSource(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            level.setBlock(center.west(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var first = GreenhouseEnvironment.inspect(level, center);
            helper.assertValueEqual(first.heat(), 8, "single campfire strength");
            helper.assertTrue(first.climate() == CropClimate.WARM && !first.valid(), "Single campfire incorrectly emulated hot desert");
            level.setBlock(center.north(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var doubled = GreenhouseEnvironment.inspect(level, center);
            helper.assertValueEqual(doubled.heat(), 8, "duplicate campfire strength must not stack");
            helper.assertTrue(doubled.climate() == CropClimate.WARM && !doubled.valid(), "Duplicate campfires upgraded temperature");
            level.setBlock(center.west(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var lava = GreenhouseEnvironment.inspect(level, center);
            helper.assertValueEqual(lava.heat(), 32, "lava strength");
            helper.assertTrue(lava.valid() && lava.climate() == CropClimate.HOT, "Stronger lava failed to reach hot");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void duplicateIceCannotStackAndStrongestHeatAndCoolingCancel(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            level.setBlock(center.west(), Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertValueEqual(GreenhouseEnvironment.inspect(level, center).cooling(), 16, "single ice strength");
            level.setBlock(center.north(), Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertValueEqual(GreenhouseEnvironment.inspect(level, center).cooling(), 16, "duplicate ice strength");
            level.setBlock(center.north(), Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.east(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var balanced = GreenhouseEnvironment.inspect(level, center);
            helper.assertValueEqual(balanced.heat(), 32, "strongest heat");
            helper.assertValueEqual(balanced.cooling(), 32, "strongest cooling");
            helper.assertTrue(balanced.climate() == CropClimate.NEUTRAL, "Equal strong sources did not cancel");
            level.setBlock(center.east(), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertValueEqual(GreenhouseEnvironment.inspect(level, center).heat(), 0, "unlit campfire strength");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void naturallyColdHouseNeedsNoIceOrBinding(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.TAIGA);
            setRecipe("minecraft:taiga");
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            GreenhouseEnvironment.clearCache();
            var survey = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(survey.valid(), "Natural cold baseline did not satisfy cold greenhouse: " + survey.status());
            helper.assertTrue(survey.baseClimate() == CropClimate.COLD && survey.climate() == CropClimate.COLD, "Natural cold baseline was changed");
            helper.assertValueEqual(survey.heat(), 0, "natural cold house heaters");
            helper.assertValueEqual(survey.cooling(), 0, "natural cold house coolers");
            helper.assertTrue(id("minecraft:taiga").equals(survey.targetBiome()), "Cold house needs an unnecessary binding block");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void spammingOneMaterialCannotReplaceOtherRequirements(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            level.setBlock(center.north(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = -4; x <= 4; x++) {
                level.setBlock(center.offset(x, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            GreenhouseEnvironment.clearCache();
            var incomplete = GreenhouseEnvironment.inspect(level, center);
            helper.assertFalse(incomplete.valid(), "One repeated material satisfied unrelated requirements");
            helper.assertTrue(incomplete.requirements().stream().anyMatch(progress -> progress.found() < progress.required()), "Missing requirement not reported");
            materials(level, center);
            GreenhouseEnvironment.clearCache();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Completing exact requirements did not validate");
            level.setBlock(center.offset(2, -1, 0), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Removing mandatory lapis preserved biome emulation");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void oppositeRoomEdgesHaveConsistentCountsClimateAndBiome(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            level.setBlock(center.north(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var west = GreenhouseEnvironment.inspect(level, center.west(4));
            GreenhouseEnvironment.clearCache();
            var east = GreenhouseEnvironment.inspect(level, center.east(4));
            helper.assertTrue(west.valid() && east.valid(), "Crop at room edge did not detect whole bounded room: west=" + west + "; east=" + east);
            helper.assertTrue(west.targetBiome().equals(east.targetBiome()) && west.climate() == east.climate(), "Room edge changed detected environment");
            helper.assertTrue(west.requirements().equals(east.requirements()), "Room edges counted different native specimens");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center.above(4)).enclosed(), "Five-high bounded room failed at upper edge");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void roofOpeningOutsideCropsAndExteriorSourcesCannotBorrowRoom(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            level.setBlock(center.north(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            CropProfile desert = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            helper.assertTrue(GreenhouseEnvironment.canSupport(level, center, desert), "Complete house did not support desert: " + GreenhouseEnvironment.inspect(level, center));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center.east(6), desert), "Outdoor crop borrowed enclosed biome");
            level.setBlock(center.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.east(6), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Exterior source heated through wall");
            level.setBlock(center.north(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.above(5), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).enclosed(), "Roof opening remained sealed");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void neighboringRoomsDoNotCombineTheirRequiredMaterials(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            // Divide the interior into disconnected rooms with a full-height glass wall.
            for (int z = -4; z <= 4; z++) for (int y = 0; y < 5; y++) {
                level.setBlock(center.offset(0, y, z), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(center.offset(-2, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(-3, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(2, -1, 0), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(3, -1, 0), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.west(2), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.east(2), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center.west()).valid(), "Left room borrowed right-hand specimens");
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center.east()).valid(), "Right room borrowed left-hand specimens");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void automaticInspectionDoesNotLoadRemoteChunks(GameTestHelper helper) {
        withSettings(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos remote = new BlockPos(1_000_000, 70, 1_000_000);
            helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Remote chunk was already loaded");
            helper.assertFalse(GreenhouseEnvironment.inspect(level, remote).valid(), "Unloaded room became valid");
            GreenhouseEnvironment.effectiveClimate(level, remote);
            GreenhouseEnvironment.canSupport(level, remote, new CropProfile(Set.of(CropClimate.HOT)));
            helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Inspection forced a chunk load");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void identicalBiomePalettesRemainValidAndSupportExactAliases(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            String materials = "=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1";
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert" + materials, "minecraft:badlands" + materials));
            GreenhouseRecipes.clearCache();
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            level.setBlock(center.north(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var survey = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(survey.valid(), "Identical material palettes made a fully matched house invalid: " + survey);
            for (String biome : List.of("minecraft:desert", "minecraft:badlands")) {
                CropProfile profile = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse(biome).orElseThrow()));
                helper.assertTrue(GreenhouseEnvironment.canSupport(level, center, profile), "Identical palette did not support alias " + biome);
            }
            CropProfile unrelated = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:jungle").orElseThrow()));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, unrelated), "Identical palette aliases included unrelated jungle");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void realCuratedForestPaletteWorksWithoutOverridesHeatersOrCore(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of());
            GreenhouseRecipes.clearCache();
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            for (int x = -4; x <= 3; x++) {
                level.setBlock(center.offset(x, -1, 0), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (int x = -2; x <= 1; x++) {
                level.setBlock(center.offset(x, -1, 2), Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(BlockStateProperties.PERSISTENT, true), Block.UPDATE_ALL);
            }
            for (int x = -2; x <= -1; x++) {
                level.setBlock(center.offset(x, -1, -2), Blocks.BIRCH_LOG.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockPos crop = center.south();
            plant(level, crop);
            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:forest"));
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
            var survey = GreenhouseEnvironment.inspect(level, crop);
            helper.assertTrue(survey.valid(), "Curated forest palette was invalid: " + survey);
            helper.assertTrue(id("minecraft:forest").equals(survey.targetBiome()), "Curated forest recognized as " + survey.targetBiome());
            helper.assertValueEqual(survey.requirements().size(), 3, "curated forest material types");
            helper.assertValueEqual(survey.specimens(), 14, "curated forest matched material count");
            helper.assertValueEqual(survey.heat(), 0, "curated forest heat sources");
            helper.assertValueEqual(survey.cooling(), 0, "curated forest cooling sources");
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)), "Curated forest could not satisfy exact crop biome");
            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 1, "curated forest crop age");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void activeThermoregulatorSupportsAutomaticHouseButRedstoneDisabledDoesNot(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional automatic greenhouse thermoregulator test: Tough As Nails not installed");
            helper.succeed();
            return;
        }
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Block regulator = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("toughasnails:thermoregulator"));
            var heating = (net.minecraft.world.level.block.state.properties.BooleanProperty) regulator.getStateDefinition().getProperty("heating");
            var cooling = (net.minecraft.world.level.block.state.properties.BooleanProperty) regulator.getStateDefinition().getProperty("cooling");
            BlockPos at = center.north();
            level.setBlock(at, regulator.defaultBlockState().setValue(BlockStateProperties.ENABLED, true)
                    .setValue(heating, true).setValue(cooling, false), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var supported = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(supported.valid() && supported.climate() == CropClimate.HOT,
                    "Active thermoregulator did not support automatic desert house: " + supported);
            helper.assertTrue(supported.baseClimate() == CropClimate.NEUTRAL, "Thermoregulator rewrote the natural biome baseline");
            helper.assertValueEqual(supported.heat(), 0, "thermoregulator should not count as a placed thermal source");
            level.setBlock(at, level.getBlockState(at).setValue(BlockStateProperties.ENABLED, false), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            var disabled = GreenhouseEnvironment.inspect(level, center);
            helper.assertFalse(disabled.valid(), "Redstone-disabled regulator kept automatic house valid");
            helper.assertTrue(disabled.status().endsWith("wrong_temperature"), "Disabled regulator failure was not a temperature failure: " + disabled);
        });
    }

    private static BlockPos house(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(CENTER);
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-5, -1, -5), center.offset(5, 5, 5))) {
            int dx = Math.abs(at.getX() - center.getX()), dz = Math.abs(at.getZ() - center.getZ());
            var state = at.getY() == center.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState()
                    : dx == 5 || dz == 5 || at.getY() == center.getY() + 5 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(at, state, Block.UPDATE_ALL);
        }
        GreenhouseEnvironment.clearCache();
        return center;
    }

    private static void materials(ServerLevel level, BlockPos center) {
        level.setBlock(center.offset(-2, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(-1, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(1, -1, 0), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(2, -1, 0), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void plant(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void setRecipe(String biome) {
        GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(biome + "=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1"));
        GreenhouseRecipes.clearCache();
        GreenhouseEnvironment.clearCache();
    }

    private static void withSettings(GameTestHelper helper, Runnable test) {
        boolean houses = GreenhouseSettings.ENABLE_GREENHOUSES.get(), ambient = GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.get();
        boolean thermo = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        int radius = GreenhouseSettings.RADIUS.get(), count = GreenhouseSettings.REQUIRED_SPECIMENS.get();
        List<? extends String> rules = Config.CROP_BIOME_RULES.get(), materials = GreenhouseSettings.BIOME_MATERIAL_RULES.get();
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.set(true);
            GreenhouseSettings.RADIUS.set(4);
            GreenhouseSettings.REQUIRED_SPECIMENS.set(4);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            Config.CROP_BIOME_RULES.set(List.of());
            CropClimateRules.clearCache();
            setRecipe("minecraft:desert");
            helper.setDayTime(1000);
            test.run();
        } finally {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(houses);
            GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.set(ambient);
            GreenhouseSettings.RADIUS.set(radius);
            GreenhouseSettings.REQUIRED_SPECIMENS.set(count);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermo);
            Config.CROP_BIOME_RULES.set(rules);
            CropClimateRules.clearCache();
            GreenhouseRecipes.clearCache();
            GreenhouseEnvironment.clearCache();
        }
        helper.succeed();
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        AlwaysGrowRandom() { super(0L); }
        @Override public int nextInt(int bound) { return 0; }
        @Override public float nextFloat() { return 0.0F; }
    }
}
