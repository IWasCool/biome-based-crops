package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class AirConditionerAirflowGameTests {
    @GameTest(template = "empty", skyAccess = true, batch = "outdoor_climate_wind")
    public static void openAirClimateOnlyFollowsTheShortOutletWind(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        var level = helper.getLevel();
        BlockPos machine = helper.absolutePos(new BlockPos(8, 40, 8));
        level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        var unit = (AirConditionerBlockEntity) level.getBlockEntity(machine);
        unit.setClimate(CropClimate.HOT);
        unit.energyStorage().receiveEnergy(3600, false);
        AirConditionerBlockEntity.serverTick(level, machine, unit.getBlockState(), unit);
        for (Direction face : Direction.Plane.HORIZONTAL) {
            level.setBlock(machine, unit.getBlockState().setValue(AirConditionerBlock.FACING, face), Block.UPDATE_ALL);
            Direction side = face.getClockWise();
            for (BlockPos at : new BlockPos[] { machine.relative(face), machine.relative(face, 4),
                    machine.relative(face, 3).relative(side), machine.relative(face, 3).relative(side, -1),
                    machine.relative(face, 3).above(), machine.relative(face, 4).relative(side, 2) }) {
                helper.assertTrue(AirConditionerAirflow.climateAt(level, at) == CropClimate.HOT, "Missing outlet wind at " + at + " facing " + face);
            }
            for (BlockPos at : new BlockPos[] { machine, machine.relative(side), machine.relative(side, -1),
                    machine.relative(face.getOpposite()), machine.above(), machine.relative(face, 5),
                    machine.relative(face).relative(side, 2), machine.relative(face).above(2) })
                helper.assertTrue(AirConditionerAirflow.climateAt(level, at) == null, "Proximity outside the outlet wind changed climate at " + at);
            BlockPos barrier = machine.relative(face, 2);
            level.setBlock(barrier, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(AirConditionerAirflow.climateAt(level, machine.relative(face, 3)) == null, "Climate passed through a solid glass wall");
            helper.assertTrue(AirConditionerAirflow.climateAt(level, machine.relative(face).relative(side)) == CropClimate.HOT,
                    "Wall blocked an unrelated clear part of the outlet wind");
            level.removeBlock(barrier, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "sealed_room_outlet_direction", timeoutTicks = 100)
    public static void wallMountedUnitOnlySharesClimateWithTheRoomItsOutletFaces(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 100, 8));
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 3, 4))) {
            boolean floor = at.getY() == center.getY() - 1;
            boolean roof = at.getY() == center.getY() + 3;
            boolean wall = Math.abs(at.getX() - center.getX()) == 4 || Math.abs(at.getZ() - center.getZ()) == 4;
            level.setBlock(at, floor ? Blocks.STONE.defaultBlockState() : roof ? Blocks.GLASS.defaultBlockState()
                    : wall ? Blocks.BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos machine = center.north(4);
        level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                .setValue(AirConditionerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        var unit = (AirConditionerBlockEntity) level.getBlockEntity(machine);
        unit.setClimate(CropClimate.HOT);
        while (unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0) { }
        GreenhouseEnvironment.clearCache();
        AirConditionerBlockEntity.serverTick(level, machine, unit.getBlockState(), unit);
        helper.assertTrue(unit.hasPaidAirflow(), "Outward outlet did not produce local wind");
        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == CropClimate.NEUTRAL,
                "Unit conditioned the sealed room behind its outlet");
        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, machine.north()) == CropClimate.HOT,
                "Outward-facing unit lost its direct wind");
        level.setBlock(machine, unit.getBlockState().setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center.south(2)) == CropClimate.HOT,
                    "Inward outlet did not share climate around the sealed room");
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center.east(5)) == CropClimate.NEUTRAL,
                    "Room climate leaked through its sealed wall");
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, machine.north()) == CropClimate.NEUTRAL,
                    "A unit facing into a room changed climate behind itself");
            level.setBlock(machine, unit.getBlockState().setValue(AirConditionerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == CropClimate.NEUTRAL,
                    "Rotating the outlet away retained the old room drive");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "outdoor_climate_shutdown")
    public static void emptyBatteryAndBlockedOutletStopLocalClimateControl(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(6, 40, 6));
        level.setBlock(at, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        var unit = (AirConditionerBlockEntity) level.getBlockEntity(at);
        unit.setClimate(CropClimate.HOT);
        unit.energyStorage().receiveEnergy(360, false);
        AirConditionerBlockEntity.serverTick(level, at, unit.getBlockState(), unit);
        helper.assertValueEqual(unit.energyStored(), 0, "One paid climate tick");
        helper.assertTrue(AirConditionerAirflow.climateAt(level, at.north()) == CropClimate.HOT, "Paid tick did not apply HOT");
        helper.runAfterDelay(3L, () -> {
            helper.assertFalse(unit.hasPaidAirflow(), "Empty battery retained climate control");
            helper.assertTrue(AirConditionerAirflow.climateAt(level, at.north()) == null, "Old climate survived without an active controller");
            unit.energyStorage().receiveEnergy(360, false);
            level.setBlock(at.north(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.runAfterDelay(3L, () -> {
                helper.assertFalse(unit.hasPaidAirflow(), "Blocked outlet kept running");
                helper.assertValueEqual(unit.energyStored(), 360, "Blocked outlet spent FE");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "outdoor_climate_sustained", timeoutTicks = 240)
    public static void selectedClimateKeepsRunningAndChangesAsCategories(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(6, 40, 6));
        level.setBlock(at, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        var unit = (AirConditionerBlockEntity) level.getBlockEntity(at);
        unit.setClimate(CropClimate.HOT);
        while (unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0) { }
        helper.runAfterDelay(80L, () -> {
            helper.assertTrue(unit.hasPaidAirflow(), "Paid categorical controller stopped");
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, at.north(3)) == CropClimate.HOT, "Outlet wind did not read HOT");
            helper.assertTrue(unit.energyStored() > 0 && unit.energyStored() < 100_000 - 360, "Climate control did not spend real FE");
            unit.setClimate(CropClimate.ICY);
            helper.runAfterDelay(3L, () -> {
                helper.assertTrue(GreenhouseEnvironment.currentClimate(level, at.north(3)) == CropClimate.ICY, "Changing mode did not select ICY");
                level.setBlock(at.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                helper.runAfterDelay(3L, () -> {
                    helper.assertFalse(unit.hasPaidAirflow(), "Redstone did not stop climate control");
                    helper.assertTrue(GreenhouseEnvironment.currentClimate(level, at.north()) == CropClimate.NEUTRAL,
                            "Disabled controller did not return to natural climate");
                    helper.succeed();
                });
            });
        });
    }
}
