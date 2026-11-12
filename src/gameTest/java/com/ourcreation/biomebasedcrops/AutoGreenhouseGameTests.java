package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
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

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_three_growth_goals")
    public static void outsideNativeBiomeNeedsSkyGlassMatchingHabitatAndMatchingClimate(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            BlockPos crop = center.south();
            BlockPos controller = center.north();
            capacitySupport(level, center, controller.getX());
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            AirConditionerBlockEntity machine = (AirConditionerBlockEntity) level.getBlockEntity(controller);
            machine.setClimate(CropClimate.HOT);
            CropProfile hot = new CropProfile(Set.of(CropClimate.HOT),
                    List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            helper.assertTrue(GreenhouseEnvironment.hasSkyGlassAbove(level, crop), "Fixture lacks sky-exposed glass");
            helper.assertFalse(CommonEvents.canGrow(level, crop, hot), "A greenhouse shell supplied the wrong climate");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, crop).status().endsWith("wrong_climate"),
                    "Unpowered fixture did not finish its habitat scan: " + GreenhouseEnvironment.inspect(level, crop));
            charge(machine);
            AirConditionerBlockEntity.serverTick(level, controller, level.getBlockState(controller), machine);
            conditionFixtureAir(level, controller, CropClimate.HOT);
            helper.assertTrue(CommonEvents.canGrow(level, crop, hot),
                    "All three greenhouse requirements did not allow growth: " + GreenhouseEnvironment.inspect(level, crop));

            BlockPos cover = crop.above(7);
            level.setBlock(cover, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.hasSkyGlassAbove(level, crop), "Glass below an opaque roof counted as sky exposure");
            helper.assertFalse(CommonEvents.canGrow(level, crop, hot), "Opaque cover above the glass allowed growth");
            level.setBlock(cover, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(CommonEvents.canGrow(level, crop, hot), "Stacked transparent glass blocked sky exposure");
            level.setBlock(cover, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            BlockPos marker = center.offset(2, 1, 0);
            level.setBlock(marker, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(CommonEvents.canGrow(level, crop, hot), "Correct climate replaced a missing biome habitat");
            level.setBlock(marker, Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            conditionFixtureAir(level, controller, CropClimate.COLD);
            helper.assertFalse(CommonEvents.canGrow(level, crop, hot), "A cold room retained its hot biome");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, crop) == null, "Wrong-climate habitat still existed as a biome");
            conditionFixtureAir(level, controller, CropClimate.HOT);
            helper.assertTrue(CommonEvents.canGrow(level, crop, hot), "Restoring the required climate did not recover growth");
            level.setBlock(center.south(5), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            var leaking = GreenhouseEnvironment.inspect(level, crop);
            helper.assertTrue(leaking.valid() && leaking.status().endsWith("leaking") && leaking.climate() == CropClimate.HOT,
                    "A brief wall opening did not retain the known greenhouse climate: " + leaking);
            helper.assertTrue(CommonEvents.canGrow(level, crop, hot), "Leak grace removed growth from the known greenhouse");
            helper.assertFalse(CommonEvents.canGrow(level, center.east(7), hot), "An outdoor crop borrowed the leaking greenhouse");
            level.setBlock(center.south(5), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, crop).status().endsWith("valid"),
                    "Resealing the wall did not recover the greenhouse");
            CropProfile nativeCrop = new CropProfile(Set.of(CropClimate.NEUTRAL));
            helper.assertTrue(CommonEvents.canGrow(level, center.east(7), nativeCrop), "A native-biome crop incorrectly required a greenhouse");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void detectsBiomeWithoutCoreAndGrowsExactBiomeCrop(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            conditioner(level, center, center.north(), CropClimate.HOT);
            BlockPos crop = center.south();
            plant(level, crop);
            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:desert"));
            CropClimateRules.clearCache();
            GreenhouseEnvironment.invalidateClimate();
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

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses_power", timeoutTicks = 100)
    public static void unpoweredMachineCannotConditionAndRedstoneStopsFurtherPower(GameTestHelper helper) {
        Runnable restore = configureSettings(helper);
        try {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            BlockPos at = center.north();
            capacitySupport(level, center, at.getX());
            level.setBlock(at, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            AirConditionerBlockEntity machine = (AirConditionerBlockEntity) level.getBlockEntity(at);
            machine.setClimate(CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Unpowered AC provided a free artificial climate");
            charge(machine);
            int before = machine.energyStored();
            AirConditionerBlockEntity.serverTick(level, at, level.getBlockState(at), machine);
            helper.assertValueEqual(machine.energyStored(), before - machine.currentEnergyCost(), "paid AC climate tick");
            GreenhouseEnvironment.invalidateClimate();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Paid categorical setting did not apply within controller coverage");
            conditionFixtureAir(level, at, CropClimate.HOT);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Conditioned air did not validate the house");
            // Replacing an existing solid floor block preserves the already conditioned air volume.
            level.setBlock(at.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            int poweredEnergy = machine.energyStored();
            helper.runAfterDelay(2L, () -> {
                try {
                    AirConditionerBlockEntity.serverTick(level, at, level.getBlockState(at), machine);
                    GreenhouseEnvironment.invalidateClimate();
                    helper.assertFalse(machine.getBlockState().getValue(AirConditionerBlock.ACTIVE), "Redstone-disabled AC kept running");
                    helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Unpowered controller retained a non-native greenhouse climate");
                    helper.assertValueEqual(machine.energyStored(), poweredEnergy, "disabled AC energy conservation");
                } finally {
                    restore.run();
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error error) {
            restore.run();
            throw error;
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void decorativeFireAndIceNeverReplacePoweredClimateMachines(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            level.setBlock(center.west(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.north(), Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.east(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.south(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.invalidateClimate();
            var unchanged = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(unchanged.climate() == unchanged.baseClimate(), "Ambient blocks changed greenhouse climate without an AC");
            helper.assertFalse(unchanged.valid(), "Decorative thermal blocks emulated a desert");
            BlockPos outside = center.east(6);
            level.setBlock(outside.north(), Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.invalidateClimate();
            helper.assertTrue(GreenhouseEnvironment.effectiveClimate(level, outside)
                    == CropClimate.at(GreenhouseEnvironment.nativeBiome(level, outside), outside), "Outdoor ice retained the removed ambient cooling mechanic");
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
            GreenhouseEnvironment.invalidateClimate();
            var survey = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(survey.valid(), "Natural cold baseline did not satisfy cold greenhouse: " + survey.status());
            helper.assertTrue(survey.baseClimate() == CropClimate.COLD && survey.climate() == CropClimate.COLD, "Natural cold baseline was changed");
            helper.assertTrue(id("minecraft:taiga").equals(survey.targetBiome()), "Cold house needs an unnecessary binding block");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void customMaterialsMustBeInsideInsteadOfInTheFloorOrWalls(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.TAIGA);
            setRecipe("minecraft:taiga");
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            List<Block> markers = List.of(Blocks.DIAMOND_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK);
            for (int i = 0; i < markers.size(); i++) {
                level.setBlock(center.offset(i - 2, -1, 0), markers.get(i).defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(center.offset(i - 2, 1, 5), markers.get(i).defaultBlockState(), Block.UPDATE_ALL);
            }
            // Reserve the interior specimen positions before scanning, so only material identity changes later.
            for (int x : List.of(-2, -1, 1, 2)) {
                level.setBlock(center.offset(x, 1, 0), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            }
            var boundaryOnly = GreenhouseEnvironment.inspectForBiome(level, center, id("minecraft:taiga"));
            helper.assertFalse(boundaryOnly.valid(), "Floor and wall blocks supplied an inside-only custom habitat");
            helper.assertValueEqual(boundaryOnly.requirements().size(), 3, "completed custom habitat requirement scan");
            helper.assertTrue(boundaryOnly.requirements().stream().allMatch(progress -> progress.found() == 0),
                    "Boundary blocks counted as interior specimens: " + boundaryOnly.requirements());
            materials(level, center);
            GreenhouseEnvironment.invalidateClimate();
            var inside = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(inside.valid(), "Matching natural climate and interior specimens did not validate: " + inside);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void spammingOneMaterialCannotReplaceOtherRequirements(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            for (int x = -4; x <= 4; x++) {
                level.setBlock(center.offset(x, 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            conditioner(level, center, center.north(), CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
            var incomplete = GreenhouseEnvironment.inspect(level, center);
            helper.assertFalse(incomplete.valid(), "One repeated material satisfied unrelated requirements");
            helper.assertTrue(incomplete.requirements().stream().anyMatch(progress -> progress.found() < progress.required()), "Missing requirement not reported");
            materials(level, center);
            GreenhouseEnvironment.invalidateClimate();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Completing exact requirements did not validate");
            level.setBlock(center.offset(2, 1, 0), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.invalidateClimate();
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
            conditioner(level, center, center.north(), CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
            var west = GreenhouseEnvironment.inspect(level, center.west(4));
            GreenhouseEnvironment.invalidateClimate();
            var east = GreenhouseEnvironment.inspect(level, center.east(4));
            helper.assertTrue(west.valid() && east.valid(), "Crop at room edge did not detect whole bounded room: west=" + west + "; east=" + east);
            helper.assertTrue(west.targetBiome().equals(east.targetBiome()) && west.climate() == east.climate(), "Room edge changed detected environment");
            helper.assertTrue(west.requirements().equals(east.requirements()), "Room edges counted different native specimens");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center.above(4)).enclosed(), "Five-high bounded room failed at upper edge");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void roofOpeningsPreserveKnownRoomsWithoutSharingWithOutsideCrops(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            conditioner(level, center, center.north(), CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
            CropProfile desert = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            helper.assertTrue(GreenhouseEnvironment.canSupport(level, center, desert), "Complete house did not support desert: " + GreenhouseEnvironment.inspect(level, center));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center.east(6), desert), "Outdoor crop borrowed enclosed biome");
            level.setBlock(center.above(5), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.invalidateClimate();
            var leaking = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(leaking.valid() && leaking.status().endsWith("leaking") && leaking.climate() == CropClimate.HOT,
                    "A brief roof opening discarded the known greenhouse: " + leaking);
            helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Roof leak grace lost the existing biome overlay");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center.east(6), desert),
                    "Exterior crop borrowed the retained leaking room");
            level.setBlock(center.above(5), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).status().endsWith("valid"),
                    "Resealing the roof did not restore the known greenhouse");
            level.setBlock(center.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            conditioner(level, center, center.east(6), CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Exterior air conditioner reached through wall");
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
            level.setBlock(center.offset(-2, 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(-3, 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(2, 1, 0), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(3, 1, 0), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            conditioner(level, center, center.west(2), CropClimate.HOT);
            conditioner(level, center, center.east(2), CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
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
    public static void identicalCustomBiomePalettesAreAmbiguousAndNeverSupportMultipleBiomes(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            String materials = "=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1";
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert" + materials, "minecraft:badlands" + materials));
            GreenhouseRecipes.clearCache();
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            conditioner(level, center, center.north(), CropClimate.HOT);
            GreenhouseEnvironment.invalidateClimate();
            var survey = GreenhouseEnvironment.inspect(level, center);
            helper.assertFalse(survey.valid(), "Identical custom recipes silently selected one biome: " + survey);
            helper.assertTrue(survey.status().endsWith(".ambiguous"), "Duplicate custom plans were not reported as ambiguous: " + survey);
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null, "Ambiguous room advertised a biome overlay");
            for (String biome : List.of("minecraft:desert", "minecraft:badlands")) {
                CropProfile profile = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse(biome).orElseThrow()));
                helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, profile), "Ambiguous room supported " + biome);
            }
            CropProfile unrelated = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:jungle").orElseThrow()));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, unrelated), "Ambiguous room supported unrelated jungle");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void distinctFlowersIdentifyOneBiomeAndMixedMarkersDisableTheOverlay(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            String common = "=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1";
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(
                    "minecraft:desert" + common + ",minecraft:poppy*1",
                    "minecraft:badlands" + common + ",minecraft:dandelion*1"));
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            BlockPos poppy = center.offset(-2, 0, 2), dandelion = center.offset(2, 0, 2);
            level.setBlock(poppy.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(dandelion.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(poppy, Blocks.POPPY.defaultBlockState(), Block.UPDATE_ALL);
            conditioner(level, center, center.north(), CropClimate.HOT);
            var desert = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            var badlands = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:badlands").orElseThrow()));
            var first = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(first.valid() && id("minecraft:desert").equals(first.targetBiome()), "Wrong flower-marked biome: " + first);
            helper.assertTrue(GreenhouseEnvironment.canSupport(level, center, desert), "Poppy-marked desert crop could not grow");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, badlands), "Desert layout also supported badlands");

            level.setBlock(dandelion, Blocks.DANDELION.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.invalidateClimate();
            var mixed = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(mixed.status().endsWith(".ambiguous"), "Mixed complete palettes were accepted: " + mixed);
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null, "Mixed palette still provided a biome overlay");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, desert), "Mixed palette supported desert");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, badlands), "Mixed palette supported badlands");

            level.removeBlock(poppy, false);
            GreenhouseEnvironment.invalidateClimate();
            var second = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(second.valid() && id("minecraft:badlands").equals(second.targetBiome()), "Removing extra marker did not select badlands: " + second);
            helper.assertTrue(GreenhouseEnvironment.canSupport(level, center, badlands), "Dandelion-marked badlands crop could not grow");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, desert), "Badlands layout retained desert support");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses")
    public static void extraIdentifyingMaterialsRefineASimplerBiomeWithoutKeepingItsCropSupport(GameTestHelper helper) {
        withSettings(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            String common = "=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1";
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(
                    "minecraft:desert" + common, "minecraft:badlands" + common + ",minecraft:poppy*1"));
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            BlockPos flower = center.offset(2, 0, 2);
            level.setBlock(flower.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(flower, Blocks.POPPY.defaultBlockState(), Block.UPDATE_ALL);
            conditioner(level, center, center.north(), CropClimate.HOT);
            var survey = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(survey.valid() && id("minecraft:badlands").equals(survey.targetBiome()), "More specific marker did not refine the habitat: " + survey);
            var desert = new CropProfile(Set.of(CropClimate.HOT), List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, desert), "Refined layout retained its simpler biome identity");
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
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                level.setBlock(center.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseHabitatGameTests.forestVegetation(level, center);
            BlockPos crop = center.south();
            plant(level, crop);
            level.setBlock(crop.below(2), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:forest"));
            CropClimateRules.clearCache();
            GreenhouseEnvironment.invalidateClimate();
            var survey = GreenhouseEnvironment.inspect(level, crop);
            helper.assertTrue(survey.valid(), "Curated forest palette was invalid: " + survey);
            helper.assertTrue(id("minecraft:forest").equals(survey.targetBiome()), "Curated forest recognized as " + survey.targetBiome());
            helper.assertValueEqual(survey.requirements().size(), 3, "curated forest material types");
            helper.assertValueEqual(survey.specimens(), 75, "curated forest matched whole percentage quotas for405habitat cells");
            helper.assertTrue(CommonEvents.canGrowNaturally(level, crop, level.getBlockState(crop)), "Curated forest could not satisfy exact crop biome");
            level.getBlockState(crop).randomTick(level, crop, new AlwaysGrowRandom());
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 1, "curated forest crop age");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "auto_greenhouses_tan_warmup", timeoutTicks = 120)
    public static void optionalFueledControllerSelectsClimateOnlyInsideMatchingGreenhouse(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional automatic greenhouse thermoregulator test: Tough As Nails not installed");
            helper.succeed();
            return;
        }
        boolean houses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        boolean thermo = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        List<? extends String> materials = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        Runnable restore = () -> {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(houses);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermo);
            GreenhouseRecipes.clearCache();
            GreenhouseEnvironment.clearCache();
        };
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            setRecipe("minecraft:desert");
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = house(helper);
            materials(level, center);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Block regulator = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("toughasnails:thermoregulator"));
            BlockPos at = center.north();
            level.setBlock(at, regulator.defaultBlockState().setValue(BlockStateProperties.ENABLED, true), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockEntity(at) instanceof Container, "TAN regulator did not expose its real fuel inventory");
            Container inventory = (Container) level.getBlockEntity(at);
            ItemStack coal = new ItemStack(Items.COAL, 64);
            helper.assertTrue(inventory.canPlaceItem(1, coal), "TAN did not accept its documented coal heating fuel");
            inventory.setItem(1, coal);
            tickThermoregulator(level, at);
            GreenhouseEnvironment.invalidateClimate();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Active categorical controller did not select the greenhouse climate");
            CropProfile hotOnly = new CropProfile(Set.of(CropClimate.HOT));
            helper.assertTrue(CommonEvents.canGrow(level, center, hotOnly),
                    "Matching categorical greenhouse did not support its crop");
            helper.runAfterDelay(55L, () -> {
                try {
                    var supported = GreenhouseEnvironment.inspect(level, center);
                    helper.assertTrue(supported.valid() && supported.climate() == CropClimate.HOT,
                            "Fueled TAN regulator did not warm the automatic desert house: " + supported);
                    helper.assertTrue(supported.baseClimate() == CropClimate.NEUTRAL, "Thermoregulator rewrote the native biome baseline");
                    level.setBlock(at.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    tickThermoregulator(level, at);
                    helper.assertFalse(level.getBlockState(at).getValue(BlockStateProperties.ENABLED), "Actual redstone did not disable TAN");
                    helper.assertTrue(ThermoregulatorDetector.providedClimateAt(level, at, center).isEmpty(),
                            "Disabled TAN regulator continued to provide a heating drive");
                    GreenhouseEnvironment.invalidateClimate();
                    level.removeBlock(at, false);
                    helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(),
                            "Stopped controller retained a non-native greenhouse climate");
                    helper.succeed();
                } finally {
                    restore.run();
                }
            });
        } catch (RuntimeException | Error error) {
            restore.run();
            throw error;
        }
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
        level.setBlock(center.offset(-2, 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(-1, 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(1, 1, 0), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(2, 1, 0), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static AirConditionerBlockEntity conditioner(ServerLevel level, BlockPos center, BlockPos at, CropClimate target) {
        if (Math.abs(at.getX() - center.getX()) < 5 && Math.abs(at.getZ() - center.getZ()) < 5) {
            capacitySupport(level, center, at.getX());
        }
        level.setBlock(at, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        AirConditionerBlockEntity machine = (AirConditionerBlockEntity) level.getBlockEntity(at);
        machine.setClimate(target);
        charge(machine);
        AirConditionerBlockEntity.serverTick(level, at, level.getBlockState(at), machine);
        conditionFixtureAir(level, at, target);
        return machine;
    }

    /** Two inward-facing units cover the fixture's 405 interior blocks; only the tested unit supplies power. */
    private static void capacitySupport(ServerLevel level, BlockPos center, int x) {
        BlockPos support = new BlockPos(x, center.getY() + 1, center.getZ() - 5);
        level.setBlock(support, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                .setValue(AirConditionerBlock.FACING, net.minecraft.core.Direction.SOUTH), Block.UPDATE_ALL);
    }

    private static void charge(AirConditionerBlockEntity machine) {
        while (machine.energyStored() < AirConditionerBlockEntity.CAPACITY) {
            if (machine.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) <= 0) {
                throw new IllegalStateException("Fixture could not charge the real FE buffer");
            }
        }
    }

    /** These synchronous tests isolate crop/biome rules; real paid climate output is tested separately. */
    static void conditionFixtureAir(ServerLevel level, BlockPos controller, CropClimate target) {
        RoomClimateState air = GreenhouseEnvironment.controllerAirState(level, controller, true);
        if (air == null) return; // An exterior controller has no connected air volume to condition.
        long now = level.getGameTime();
        for (int tick = 0; tick < 240; tick++) {
            air.addPaidDrive(controller.asLong(), now + tick, target, true);
            air.advance(now + tick + 1, air.naturalClimate(), air.naturalBreathable(), true);
        }
        air.advance(now, air.naturalClimate(), air.naturalBreathable(), true);
        air.addPaidDrive(controller.asLong(), now, target, true);
        GreenhouseEnvironment.invalidateClimate();
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickThermoregulator(ServerLevel level, BlockPos pos) {
        T entity = (T) level.getBlockEntity(pos);
        var state = level.getBlockState(pos);
        var ticker = state.getTicker(level, (BlockEntityType<T>) entity.getType());
        if (ticker == null) throw new IllegalStateException("Installed TAN regulator has no server ticker");
        ticker.tick(level, pos, state, entity);
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
        Runnable restore = configureSettings(helper);
        try {
            test.run();
        } finally {
            restore.run();
        }
        helper.succeed();
    }

    private static Runnable configureSettings(GameTestHelper helper) {
        boolean houses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        boolean thermo = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        int count = GreenhouseSettings.REQUIRED_SPECIMENS.get();
        List<? extends String> rules = Config.CROP_BIOME_RULES.get(), materials = GreenhouseSettings.BIOME_MATERIAL_RULES.get();
        Runnable restore = () -> {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(houses);
            GreenhouseSettings.REQUIRED_SPECIMENS.set(count);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermo);
            Config.CROP_BIOME_RULES.set(rules);
            CropClimateRules.clearCache();
            GreenhouseRecipes.clearCache();
            GreenhouseEnvironment.clearCache();
        };
        GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
        GreenhouseSettings.REQUIRED_SPECIMENS.set(4);
        Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
        Config.CROP_BIOME_RULES.set(List.of());
        CropClimateRules.clearCache();
        setRecipe("minecraft:desert");
        helper.setDayTime(1000);
        return restore;
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private static final class AlwaysGrowRandom extends LegacyRandomSource {
        AlwaysGrowRandom() { super(0L); }
        @Override public int nextInt(int bound) { return 0; }
        @Override public float nextFloat() { return 0.0F; }
    }
}
