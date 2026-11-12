package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class AirConditionerGameTests {
    private static final BlockPos UNIT = new BlockPos(6, 2, 6);
    private AirConditionerGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "crafts_additions_power")
    public static void craftsAndAdditionsEnergySourceChargesAndRunsController(GameTestHelper helper) {
        if (!ModList.get().isLoaded("createaddition")) { helper.succeed(); return; }
        AirConditionerBlockEntity unit = placeInRoom(helper);
        var source = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createaddition:creative_energy"));
        helper.assertTrue(source != Blocks.AIR, "Crafts & Additions energy source is missing");
        helper.getLevel().setBlock(unit.getBlockPos().east(), source.defaultBlockState(), Block.UPDATE_ALL);
        unit.setClimate(CropClimate.WARM);
        helper.runAfterDelay(30L, () -> {
            helper.assertTrue(unit.energyStored() > 0, "External source did not deliver FE");
            helper.assertTrue(unit.hasPaidAirflow(), "Externally powered controller did not run");
            helper.assertTrue(unit.currentClimate() == CropClimate.WARM, "Paid controller did not supply its selected climate");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void frontIndicatorFollowsSelectedCategoryWithoutPower(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        for (boolean active : new boolean[] { false, true }) for (Direction facing : Direction.Plane.HORIZONTAL) {
            helper.getLevel().setBlock(unit.getBlockPos(), unit.getBlockState().setValue(AirConditionerBlock.FACING, facing)
                    .setValue(AirConditionerBlock.ACTIVE, active), Block.UPDATE_ALL);
            for (CropClimate climate : CropClimate.values()) {
                unit.setClimate(climate);
                helper.assertTrue(unit.getBlockState().getValue(AirConditionerBlock.CLIMATE) == climate, "Wrong indicator for " + climate);
                helper.assertTrue(unit.getBlockState().getValue(AirConditionerBlock.ACTIVE) == active, "Selecting climate changed running state");
                helper.assertTrue(unit.getBlockState().getValue(AirConditionerBlock.FACING) == facing, "Selecting climate rotated controller");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void loadedTargetRestoresFrontIndicatorOnFirstTick(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        for (CropClimate climate : CropClimate.values()) {
            // Old chunks have no categorical indicator property, so load with its default state.
            helper.getLevel().setBlock(unit.getBlockPos(), unit.getBlockState()
                    .setValue(AirConditionerBlock.CLIMATE, CropClimate.NEUTRAL)
                    .setValue(AirConditionerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            var saved = unit.saveWithoutMetadata(helper.getLevel().registryAccess());
            saved.putString("Climate", climate.name());
            unit.loadWithComponents(saved, helper.getLevel().registryAccess());
            AirConditionerBlockEntity.serverTick(helper.getLevel(), unit.getBlockPos(), unit.getBlockState(), unit);
            helper.assertTrue(unit.getBlockState().getValue(AirConditionerBlock.CLIMATE) == climate,
                    "First tick did not restore saved indicator for " + climate);
            helper.assertTrue(unit.getBlockState().getValue(AirConditionerBlock.FACING) == Direction.EAST,
                    "Restoring indicator rotated controller");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void energyCapabilityAcceptsEveryFaceAndCannotExtract(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        ServerLevel level = helper.getLevel();
        for (Direction face : Direction.values()) {
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, unit.getBlockPos(), face);
            helper.assertTrue(energy != null && energy.canReceive() && !energy.canExtract(), "Invalid FE capability on " + face);
            int before = unit.energyStored();
            helper.assertValueEqual(energy.receiveEnergy(20, true), 20, "simulated input");
            helper.assertValueEqual(unit.energyStored(), before, "simulation changed battery");
            helper.assertValueEqual(energy.receiveEnergy(20, false), 20, "real input");
            helper.assertValueEqual(unit.energyStored(), before + 20, "input did not share battery");
            helper.assertValueEqual(energy.extractEnergy(20, false), 0, "controller allowed extraction");
        }
        helper.assertValueEqual(unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, true), 10_000, "input limit");
        while (unit.energyStored() < AirConditionerBlockEntity.CAPACITY)
            helper.assertTrue(unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0, "Battery stopped before full");
        helper.assertValueEqual(unit.energyStored(), 100_000, "storage capacity");
        helper.assertValueEqual(unit.energyStorage().receiveEnergy(1, false), 0, "full battery accepted more");
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void fiveSettingsUsePowerOnlyToChangeNativeClimate(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        helper.assertTrue(unit.localClimate() == CropClimate.NEUTRAL, "Plains fixture was not neutral");
        for (CropClimate climate : CropClimate.values()) {
            unit.setClimate(climate);
            helper.assertValueEqual(unit.currentEnergyCost(), climate == CropClimate.NEUTRAL ? 0 : 360, "FE cost for " + climate);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "categorical_controller", timeoutTicks = 100)
    public static void controllerRunsAllFiveModesAndStopsWhenDisabledOrEmpty(GameTestHelper helper) {
        AirConditionerBlockEntity unit = placeInRoom(helper);
        while (unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0) { }
        checkMode(helper, unit, 0);
    }

    private static void checkMode(GameTestHelper helper, AirConditionerBlockEntity unit, int index) {
        if (index == CropClimate.values().length) {
            unit.setClimate(CropClimate.HOT);
            helper.getLevel().setBlock(unit.getBlockPos().below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.runAfterDelay(3L, () -> {
                helper.assertFalse(unit.hasPaidAirflow(), "Redstone-disabled controller remained active");
                helper.assertTrue(unit.currentClimate() == CropClimate.NEUTRAL, "Inactive controller retained a nonnative climate");
                helper.succeed();
            });
            return;
        }
        CropClimate climate = CropClimate.values()[index];
        unit.setClimate(climate);
        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(unit.currentClimate() == climate, "Selected climate was not supplied: " + climate);
            helper.assertTrue(unit.hasPaidAirflow() == (climate != CropClimate.NEUTRAL), "Wrong active state for " + climate);
            checkMode(helper, unit, index + 1);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void categoricalTargetsAndEnergySurviveNbtAndOldSavesMigrate(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        unit.setClimate(CropClimate.COLD);
        unit.energyStorage().receiveEnergy(1234, false);
        var saved = unit.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertFalse(saved.contains("TargetTemperature"), "New saves still write numeric targets");
        var restored = new AirConditionerBlockEntity(unit.getBlockPos(), unit.getBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.selectedClimate() == CropClimate.COLD, "Saved categorical target changed");
        helper.assertValueEqual(restored.energyStored(), 1234, "Saved battery changed");
        saved.remove("Climate");
        saved.putInt("TargetTemperature", 50);
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.selectedClimate() == CropClimate.HOT, "Old numeric target did not migrate");
        saved.remove("TargetTemperature");
        saved.putString("Climate", "invalid");
        saved.putInt("Energy", -1);
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.selectedClimate() == CropClimate.NEUTRAL, "Malformed climate lacked safe fallback");
        helper.assertValueEqual(restored.energyStored(), 0, "Malformed battery was not bounded");
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void menuHasExactlyFiveSettingsAndRejectsDistantChanges(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(unit.getBlockPos().getX() + 0.5, unit.getBlockPos().getY(), unit.getBlockPos().getZ() + 1.5);
        var menu = new AirConditionerMenu(1, player.getInventory(), unit);
        helper.assertFalse(menu.clickMenuButton(player, -1), "Menu accepted negative setting");
        helper.assertFalse(menu.clickMenuButton(player, 5), "Menu accepted a sixth setting");
        for (int index = 0; index < 5; index++) {
            helper.assertTrue(menu.clickMenuButton(player, index), "Menu rejected setting " + index);
            helper.assertTrue(unit.selectedClimate() == CropClimate.values()[index], "Menu sent wrong category");
            helper.assertTrue(menu.targetClimate() == unit.selectedClimate(), "Menu did not track selected category");
        }
        player.setPos(unit.getBlockPos().getX() + 20, unit.getBlockPos().getY(), unit.getBlockPos().getZ());
        helper.assertFalse(menu.clickMenuButton(player, 0), "Distant player changed climate");
        helper.assertTrue(unit.selectedClimate() == CropClimate.HOT, "Rejected click changed target");
        helper.succeed();
    }

    private static AirConditionerBlockEntity placeInRoom(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        BlockPos center = helper.absolutePos(UNIT);
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 3, 2))) {
            boolean floor = at.getY() == center.getY() - 1;
            boolean shell = Math.abs(at.getX() - center.getX()) == 2 || Math.abs(at.getZ() - center.getZ()) == 2 || at.getY() == center.getY() + 3;
            helper.getLevel().setBlock(at, floor ? Blocks.STONE.defaultBlockState() : shell ? Blocks.GLASS.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.getLevel().setBlock(center, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        GreenhouseEnvironment.clearCache();
        return (AirConditionerBlockEntity) helper.getLevel().getBlockEntity(center);
    }
    private static AirConditionerBlockEntity place(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        BlockPos pos = helper.absolutePos(UNIT);
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        return (AirConditionerBlockEntity) helper.getLevel().getBlockEntity(pos);
    }
}
