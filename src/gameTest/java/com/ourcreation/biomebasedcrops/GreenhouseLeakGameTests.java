package com.ourcreation.biomebasedcrops;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseLeakGameTests {
    private GreenhouseLeakGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_leak_expiry", timeoutTicks = 2200)
    public static void aPersistentLeakDriftsToAmbientThenAChangedRoomCanBeSurveyedAgain(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        List<? extends String> rules = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        boolean[] finished = {false};
        Runnable restore = () -> {
            finished[0] = true;
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(rules);
            GreenhouseEnvironment.clearCache();
        };
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(16384);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert=minecraft:diamond_block*1"));
            GreenhouseEnvironment.clearCache();
            BlockPos center = helper.absolutePos(new BlockPos(8, 110, 8));
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            for (BlockPos at : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 3, 4))) {
                boolean wall = Math.abs(at.getX() - center.getX()) == 4 || Math.abs(at.getZ() - center.getZ()) == 4;
                level.setBlock(at, at.getY() == center.getY() + 3 ? Blocks.GLASS.defaultBlockState()
                        : at.getY() == center.getY() - 1 || wall ? Blocks.BRICKS.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), flags);
            }
            level.setBlock(center.west(), Blocks.DIAMOND_BLOCK.defaultBlockState(), flags);
            BlockPos controller = center.north(4), far = center.south(2), hole = center.east(4);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.SOUTH), flags);
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(controller);
            unit.setClimate(CropClimate.HOT);
            unit.energyStorage().receiveEnergy(10000, false);
            AirConditionerBlockEntity.serverTick(level, controller, unit.getBlockState(), unit);
            var original = GreenhouseRoomScanner.completedAt(level, far);
            helper.assertTrue(original != null && GreenhouseEnvironment.inspect(level, far).valid(),
                    "Initial conditioned room did not register: " + GreenhouseEnvironment.inspect(level, far));
            helper.onEachTick(() -> {
                if (!finished[0]) unit.energyStorage().receiveEnergy(10000, false);
            });
            level.setBlock(hole, Blocks.AIR.defaultBlockState(), flags);
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.HOT,
                    "The opening discarded the initial room climate");
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.HOT,
                            "A short opening did not retain the conditioned climate");
                    helper.assertTrue(level.getBiome(far).is(Biomes.DESERT), "A short opening lost the registered biome");
                } catch (RuntimeException | Error error) { restore.run(); throw error; }
            });
            helper.runAfterDelay(1405, () -> {
                try {
                    helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.WARM,
                            "The open room did not begin drifting toward natural climate after its grace period");
                    helper.assertTrue(original.live, "The retained geometry expired before all its climate dissipated");
                } catch (RuntimeException | Error error) { restore.run(); throw error; }
            });
            helper.runAfterDelay(1625, () -> {
                try {
                    helper.assertTrue(unit.hasPaidAirflow(), "The leak test did not keep the AC genuinely powered");
                    helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.NEUTRAL,
                            "Continued AC power prevented the open room returning to its natural climate");
                    helper.assertFalse(original.live, "A fully dissipated leak retained stale indexed geometry");
                    helper.assertFalse(level.getBiome(far).is(Biomes.DESERT), "A long-open room retained its former biome");
                    helper.assertFalse(GreenhouseEnvironment.inspect(level, hole.east()).valid(),
                            "Unknown outdoor air invented a greenhouse");
                    // Expand the east wall instead of repairing the old hole. An expired
                    // interior must no longer hide this newly sealed room from discovery.
                    for (int y = 0; y < 3; y++) {
                        level.setBlock(center.offset(4, y, 0), Blocks.AIR.defaultBlockState(), flags);
                        level.setBlock(center.offset(5, y, 0), Blocks.BRICKS.defaultBlockState(), flags);
                        level.setBlock(center.offset(4, y, -1), Blocks.BRICKS.defaultBlockState(), flags);
                        level.setBlock(center.offset(4, y, 1), Blocks.BRICKS.defaultBlockState(), flags);
                    }
                    level.setBlock(center.offset(4, -1, 0), Blocks.BRICKS.defaultBlockState(), flags);
                    level.setBlock(center.offset(4, 3, 0), Blocks.GLASS.defaultBlockState(), flags);
                    GreenhouseRoomScanner.prioritize(level, controller.south());
                    GreenhouseEnvironment.controllerAirState(level, controller, true);
                    helper.runAfterDelay(10, () -> {
                        try {
                            var repaired = GreenhouseRoomScanner.completedAt(level, far);
                            helper.assertTrue(repaired != null && repaired != original && repaired.live,
                                    "A resealed changed room did not obtain new geometry");
                            helper.assertTrue(repaired.interior.contains(center.east(4).asLong()),
                                    "The old expired interior concealed the expanded room");
                            helper.assertTrue(GreenhouseEnvironment.inspect(level, far).valid()
                                            && level.getBiome(far).is(Biomes.DESERT),
                                    "Resealing after dissipation did not restore the powered greenhouse");
                            helper.succeed();
                        } finally { restore.run(); }
                    });
                } catch (RuntimeException | Error error) { restore.run(); throw error; }
            });
        } catch (RuntimeException | Error error) { restore.run(); throw error; }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_leak_retention", timeoutTicks = 100)
    public static void aDoorAndRoofHoleRetainOnlyTheKnownRoomAndResealWithoutRescanning(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        List<? extends String> rules = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(16384);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert=minecraft:diamond_block*1"));
            GreenhouseEnvironment.clearCache();
            BlockPos center = helper.absolutePos(new BlockPos(8, 100, 8));
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 3, 2))) {
                boolean wall = Math.abs(at.getX() - center.getX()) == 2 || Math.abs(at.getZ() - center.getZ()) == 2;
                level.setBlock(at, at.getY() == center.getY() + 3 ? Blocks.GLASS.defaultBlockState()
                        : at.getY() == center.getY() - 1 || wall ? Blocks.BRICKS.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), flags);
            }
            level.setBlock(center.west(), Blocks.DIAMOND_BLOCK.defaultBlockState(), flags);
            BlockPos door = center.south(2), roof = center.above(3), controller = center.north(2);
            var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
            var upper = lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            level.setBlock(door, lower, flags);
            level.setBlock(door.above(), upper, flags);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.SOUTH), flags);
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(controller);
            unit.setClimate(CropClimate.HOT);
            unit.energyStorage().receiveEnergy(10000, false);
            AirConditionerBlockEntity.serverTick(level, controller, unit.getBlockState(), unit);
            var geometry = GreenhouseRoomScanner.completedAt(level, center);
            helper.assertTrue(geometry != null && GreenhouseEnvironment.inspect(level, center).valid(),
                    "The sealed heated fixture did not become a greenhouse: " + GreenhouseEnvironment.inspect(level, center));
            int volume = geometry.interior.size();

            level.setBlock(door, lower.setValue(BlockStateProperties.OPEN, true), flags);
            level.setBlock(door.above(), upper.setValue(BlockStateProperties.OPEN, true), flags);
            helper.assertTrue(geometry.leaking(), "An open door did not start the room leak");
            long openedAt = geometry.leakSince();
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == CropClimate.HOT,
                    "Opening a door discarded the room climate immediately");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "A brief door opening immediately removed the greenhouse biome");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, door.south()) == null,
                    "A retained room expanded its biome through the open door");

            level.setBlock(roof, Blocks.AIR.defaultBlockState(), flags);
            helper.assertTrue(geometry.leakSince() == openedAt, "Additional openings must keep the original deadline");
            helper.assertTrue(geometry.hasRoofOpeningAbove(center), "Known glass roof opening was not remembered");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "A brief glass-roof hole discarded the known greenhouse");
            helper.assertTrue(GreenhouseRoomScanner.completedAt(level, center) == geometry && geometry.interior.size() == volume,
                    "A leak rescanned or expanded the retained interior");

            level.setBlock(door, lower, flags);
            level.setBlock(door.above(), upper, flags);
            helper.assertTrue(geometry.leaking(), "Closing the door cleared an unrelated roof opening");
            level.setBlock(roof, Blocks.GLASS.defaultBlockState(), flags);
            helper.assertFalse(geometry.leaking(), "Resealing all openings did not stop the leak");
            helper.assertTrue(GreenhouseRoomScanner.completedAt(level, center) == geometry,
                    "Resealing the same boundary needlessly replaced the room geometry");
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == CropClimate.HOT,
                    "Resealing before the deadline lost the actively maintained room climate");
            helper.succeed();
        } finally {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(rules);
            GreenhouseEnvironment.clearCache();
        }
    }
}
