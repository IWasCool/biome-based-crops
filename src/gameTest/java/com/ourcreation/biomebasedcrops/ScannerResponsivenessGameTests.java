package com.ourcreation.biomebasedcrops;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class ScannerResponsivenessGameTests {
    private ScannerResponsivenessGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "scanner_inspection_response")
    public static void inspectingTinyRoomDoesNotWaitForAnEnclosingBackgroundSurvey(GameTestHelper helper) {
        int maximum = GreenhouseSettings.MAX_ROOM_BLOCKS.get();
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        double roofRatio = GreenhouseSettings.MIN_ROOF_GLASS_RATIO.get();
        List<? extends String> materials = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(1_048_576);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(256);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(1D);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(missingHabitatRules(helper));
            GreenhouseEnvironment.clearCache();
            var level = helper.getLevel();
            BlockPos large = helper.absolutePos(new BlockPos(512, 140, 512));
            box(helper, large, 32, 4, 32);
            BlockPos tiny = large.offset(10, 0, 7);
            box(helper, tiny, 3, 2, 4);
            BlockPos controller = tiny.offset(1, 0, -1);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(controller);
            var background = GreenhouseRoomScanner.find(level, large.offset(8, 1, 8));
            helper.assertTrue(background.status.equals("scanning"), "The background fixture unexpectedly finished its large survey");
            // Opening the menu is the real priority request, not a testing-only scanner call.
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new AirConditionerMenu(0, player.getInventory(), unit);
            var room = GreenhouseEnvironment.controllerRoomInfo(level, controller);
            helper.assertTrue(room.inspection().enclosed(), "A 3x4 room waited behind the surrounding survey: " + room);
            helper.assertValueEqual(room.inspection().floorArea(), 12, "tiny inspected floor area");
            helper.assertValueEqual(room.inspection().roomVolume(), 24, "tiny inspected room volume");
            helper.assertTrue(room.inspection().status().endsWith("missing_specimens"),
                    "The detected stone-roof room did not report its missing biome habitat: " + room);
            helper.assertValueEqual(room.inspection().glazing().roofFound(), 0, "stone-roof glass count");
            helper.assertValueEqual(room.inspection().glazing().roofRequired(), 12, "stone-roof required glass");
            helper.assertTrue(menu.roomStatus() == AirConditionerMenu.RoomStatus.MISSING_SPECIMENS,
                    "The menu did not replace scanning with the room's missing-habitat reason");
            helper.assertTrue(menu.roomEnclosed(), "The menu lost its detected sealed room");
            helper.assertValueEqual(menu.roofGlassFound(), 0, "menu stone-roof glass count");
            helper.assertValueEqual(menu.roofGlassRequired(), 12, "menu stone-roof glass requirement");
            helper.assertFalse(menu.roofSkyExposed(), "The stone roof was reported as sun-exposed glass");
            helper.assertValueEqual(menu.roomInteriorBlocks(), 24, "menu tiny interior size");
            helper.succeed();
        } finally {
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(maximum);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(roofRatio);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            GreenhouseEnvironment.clearCache();
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "scanner_inspection_response")
    public static void inspectingAnOpenTinyRoomReturnsNoRoomInsteadOfAnUnqueuedScan(GameTestHelper helper) {
        int maximum = GreenhouseSettings.MAX_ROOM_BLOCKS.get();
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(1_048_576);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(256);
            GreenhouseEnvironment.clearCache();
            var level = helper.getLevel();
            BlockPos large = helper.absolutePos(new BlockPos(512, 140, 512));
            BlockPos tiny = large.east(64);
            box(helper, large, 32, 4, 32);
            box(helper, tiny, 3, 2, 4);
            BlockPos controller = tiny.offset(1, 0, -1);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            level.setBlock(tiny.offset(1, 0, 4), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            var background = GreenhouseRoomScanner.find(level, large.offset(8, 1, 8));
            helper.assertTrue(background.status.equals("scanning"), "The background fixture unexpectedly finished its survey");
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new AirConditionerMenu(0, player.getInventory(), (AirConditionerBlockEntity) level.getBlockEntity(controller));
            var room = GreenhouseEnvironment.controllerRoomInfo(level, controller);
            helper.assertFalse(room.inspection().enclosed(), "An open tiny room was reported as sealed: " + room);
            helper.assertTrue(room.inspection().status().endsWith("open"),
                    "An open 3x4 room kept an indefinite scanning status: " + room);
            helper.assertTrue(menu.roomStatus() == AirConditionerMenu.RoomStatus.OPEN,
                    "The menu did not give the completed no-room result");
            helper.assertFalse(menu.roomEnclosed(), "The menu called the open fixture a sealed room");
            helper.succeed();
        } finally {
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(maximum);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseEnvironment.clearCache();
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "scanner_inspection_response")
    public static void aGlazedTinyRoomReportsHabitatRequirementsInsteadOfScanningOrMissingGlass(GameTestHelper helper) {
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        double roofRatio = GreenhouseSettings.MIN_ROOF_GLASS_RATIO.get();
        List<? extends String> materials = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(256);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(1D);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(missingHabitatRules(helper));
            GreenhouseEnvironment.clearCache();
            var level = helper.getLevel();
            BlockPos tiny = helper.absolutePos(new BlockPos(512, 180, 512));
            box(helper, tiny, 3, 2, 4);
            for (int x = 0; x < 3; x++) for (int z = 0; z < 4; z++)
                level.setBlock(tiny.offset(x, 2, z), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos controller = tiny.offset(1, 0, -1);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new AirConditionerMenu(0, player.getInventory(), (AirConditionerBlockEntity) level.getBlockEntity(controller));
            var room = GreenhouseEnvironment.controllerRoomInfo(level, controller);
            helper.assertTrue(room.inspection().enclosed(), "A glazed 3x4 room did not complete its inspection: " + room);
            helper.assertValueEqual(room.inspection().glazing().roofFound(), 12, "glazed tiny roof count");
            helper.assertValueEqual(room.inspection().glazing().roofRequired(), 12, "glazed tiny roof requirement");
            helper.assertTrue(room.inspection().status().endsWith("missing_specimens"),
                    "A fully glazed room without its habitat did not explain the actual missing requirement: " + room);
            helper.assertTrue(menu.roomStatus() == AirConditionerMenu.RoomStatus.MISSING_SPECIMENS,
                    "The menu kept a scanning or missing-glass result after the completed survey");
            helper.assertTrue(menu.roomEnclosed(), "The menu lost its glazed sealed room");
            helper.assertValueEqual(menu.roofGlassFound(), 12, "menu glazed roof count");
            helper.assertValueEqual(menu.roofGlassRequired(), 12, "menu glazed roof requirement");
            helper.assertTrue(menu.roofSkyExposed(), "The glazed roof did not report its sky exposure");
            helper.succeed();
        } finally {
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(roofRatio);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            GreenhouseEnvironment.clearCache();
        }
    }

    private static List<String> missingHabitatRules(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
                .map(id -> id + "=minecraft:emerald_block*1").toList();
    }

    private static void box(GameTestHelper helper, BlockPos min, int width, int height, int depth) {
        var level = helper.getLevel();
        for (int x = (min.getX() - 2) >> 4; x <= (min.getX() + width + 2) >> 4; x++)
            for (int z = (min.getZ() - 2) >> 4; z <= (min.getZ() + depth + 2) >> 4; z++) level.getChunk(x, z);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos at : BlockPos.betweenClosed(min.offset(-1, -1, -1), min.offset(width, height, depth))) {
            boolean shell = at.getX() == min.getX() - 1 || at.getX() == min.getX() + width
                    || at.getY() == min.getY() - 1 || at.getY() == min.getY() + height
                    || at.getZ() == min.getZ() - 1 || at.getZ() == min.getZ() + depth;
            level.setBlock(at, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), flags);
        }
    }
}
