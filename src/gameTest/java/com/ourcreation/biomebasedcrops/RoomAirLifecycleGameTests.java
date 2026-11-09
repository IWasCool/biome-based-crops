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
public final class RoomAirLifecycleGameTests {
    private RoomAirLifecycleGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "room_air_lifecycle")
    public static void memoryEvictionPreservesAirButAnEditedRoofCannotRestoreIt(GameTestHelper helper) {
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            helper.setBiome(Biomes.PLAINS);
            var level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
            for (Direction face : Direction.values()) {
                level.setBlock(center.relative(face), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(center, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos controller = center.west();
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            RoomClimateState air = GreenhouseEnvironment.controllerAirState(level, controller, true);
            helper.assertTrue(air != null, "Sealed one-cell room was not discovered");
            long now = level.getGameTime();
            // Isolate cache lifecycle from electrical timing, which the AC GameTests exercise.
            for (int offset = 0; offset < 100; offset++) {
                air.addPaidDrive(controller.asLong(), now + offset, 45, true);
                air.advance(now + offset + 1, 20, true, true);
            }
            air.advance(now, 20, true, true);
            float produced = air.currentTemperature();
            helper.assertTrue(produced > 40, "Fixture failed to produce stored warm air");
            var geometry = GreenhouseRoomScanner.completedAroundController(level, controller);
            GreenhouseRoomScanner.evictForMemoryPressure(level, geometry);
            helper.assertFalse(geometry.live, "Memory eviction retained the large geometry");
            helper.assertTrue(GreenhouseEnvironment.hasRetainedAir(level), "Memory eviction discarded the small air snapshot");
            RoomClimateState restored = GreenhouseEnvironment.controllerAirState(level, controller, true);
            helper.assertTrue(restored != null && restored != air, "Rescan did not reconstruct the shared room state");
            helper.assertTrue(Math.abs(restored.currentTemperature() - produced) < 0.001,
                    "Unchanged geometry lost its previously produced heat");

            GreenhouseRoomScanner.evictForMemoryPressure(level,
                    GreenhouseRoomScanner.completedAroundController(level, controller));
            level.setBlock(center.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.hasRetainedAir(level), "Opening an evicted room did not discard its snapshot");
            level.setBlock(center.above(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            RoomClimateState edited = GreenhouseEnvironment.controllerAirState(level, controller, true);
            helper.assertTrue(edited != null && Math.abs(edited.currentTemperature() - 20) < 0.001,
                    "Resealing edited geometry resurrected stale paid heat");
            helper.succeed();
        } finally {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseEnvironment.clearCache();
        }
    }
}
