package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class ControllerRoomCapacityGameTests {
    private ControllerRoomCapacityGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "controller_room_capacity", timeoutTicks = 100)
    public static void interiorCapacityStacksAndFacingAwayImmediatelyRevokesRoomClimate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        List<? extends String> materials = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        List<Long> tickets = new ArrayList<>();
        Runnable restore = () -> {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            GreenhouseEnvironment.clearCache();
            for (long ticket : tickets) {
                ChunkPos chunk = new ChunkPos(ticket);
                level.setChunkForced(chunk.x, chunk.z, false);
            }
        };
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(16384);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1",
                    "minecraft:desert=minecraft:emerald_block*1"));
            GreenhouseEnvironment.clearCache();
            BlockPos base = helper.absolutePos(new BlockPos(1024, 140, 1024));
            BlockPos expanded = base.east(48);
            room(helper, base, 32, tickets);
            room(helper, expanded, 17, tickets);
            for (int x = 2; x < 6; x++) level.setBlock(expanded.offset(x, 0, 2), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            // A 16x16 floor plus one column, two blocks high: 514 interior cells.
            for (int z = 1; z < 16; z++) for (int y = 0; y < 2; y++)
                level.setBlock(expanded.offset(16, y, z), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos nativeRoom = base.offset(8, 0, 8), far = expanded.offset(15, 0, 15);
            // Complete the natural-room baseline before installing machines or
            // requesting another room: explicit surveys share a bounded queue.
            afterRoomScan(helper, nativeRoom, 40, restore, natural -> {
                helper.assertTrue(natural.inspection().valid(), "A naturally matching 1024-block interior required a unit: " + natural);
                helper.assertValueEqual(natural.inspection().floorArea(), 512, "base room floor area");
                helper.assertValueEqual(natural.inspection().roomVolume(), 1024, "base room enclosed interior");
                helper.assertValueEqual(natural.installedUnits(), 0, "natural room installed units");
                BlockPos controller = expanded.offset(2, 0, -1), extra = expanded.offset(10, 0, -1);
                BlockPos third = expanded.offset(6, 0, -1);
                level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                        .setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
                level.setBlock(extra, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                        .setValue(AirConditionerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
                level.setBlock(third, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                        .setValue(AirConditionerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
                var machine = (AirConditionerBlockEntity) level.getBlockEntity(controller);
                machine.setClimate(CropClimate.HOT);
                machine.energyStorage().receiveEnergy(10000, false);
                afterRoomScan(helper, controller, 40, restore, scanned -> {
                    try {
                        AirConditionerBlockEntity.serverTick(level, controller, level.getBlockState(controller), machine);
                        var undersized = GreenhouseEnvironment.controllerRoomInfo(level, controller);
                        helper.assertTrue(undersized.inspection().enclosed(), "Expanded capacity fixture did not finish scanning: " + undersized);
                        helper.assertValueEqual(undersized.inspection().floorArea(), 257, "expanded room floor area");
                        helper.assertValueEqual(undersized.inspection().roomVolume(), 514, "height must count toward enclosed interior capacity");
                        helper.assertValueEqual(undersized.installedUnits(), 1, "outward-facing unit must not supply capacity");
                        helper.assertValueEqual(undersized.requiredUnits(), 3, "514-interior required units");
                        helper.assertValueEqual(undersized.interiorCapacity(), 256, "single-unit interior capacity");
                        helper.assertTrue(undersized.inspection().status().endsWith("insufficient_capacity"), "Oversized room registered as greenhouse: " + undersized);
                        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, controller.south()) == CropClimate.HOT,
                                "Insufficient room capacity suppressed the directly-facing outlet wind");
                        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.NEUTRAL,
                                "One unit conditioned the whole 514-block interior");
                        var windRegion = GreenhouseEnvironment.atmosphereRegionAt(level, controller.south());
                        helper.assertTrue(windRegion != null && windRegion.climate() == CropClimate.HOT
                                        && windRegion.coverage().test(controller.south()) && !windRegion.coverage().test(far),
                                "Client room synchronization did not distinguish outlet wind from the unconditioned room");
                        var player = helper.makeMockPlayer(GameType.SURVIVAL);
                        var menu = new AirConditionerMenu(0, player.getInventory(), machine);
                        helper.assertTrue(menu.roomStatus() == AirConditionerMenu.RoomStatus.INSUFFICIENT_CAPACITY,
                                "Room-status menu did not report capacity failure");
                        helper.assertValueEqual(menu.roomInteriorBlocks(), 514, "menu front-room interior");
                        helper.assertValueEqual(menu.roomInteriorCapacity(), 256, "menu front-room capacity");
                        helper.assertValueEqual(menu.installedUnits(), 1, "menu eligible unit count");
                        helper.assertValueEqual(menu.requiredUnits(), 3, "menu required unit count");
                        level.setBlock(extra, level.getBlockState(extra).setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
                        var stillUndersized = GreenhouseEnvironment.controllerRoomInfo(level, controller);
                        helper.assertValueEqual(stillUndersized.installedUnits(), 2, "two inward-facing installed units");
                        helper.assertValueEqual(stillUndersized.interiorCapacity(), 512, "two-unit additive capacity");
                        helper.assertTrue(stillUndersized.inspection().status().endsWith("insufficient_capacity"),
                                "Two units ignored the room height and incorrectly covered 514 enclosed blocks: " + stillUndersized);
                        level.setBlock(third, level.getBlockState(third).setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
                        var enough = GreenhouseEnvironment.controllerRoomInfo(level, controller);
                        helper.assertValueEqual(enough.installedUnits(), 3, "inward-facing stacked units");
                        helper.assertValueEqual(enough.interiorCapacity(), 768, "three-unit additive capacity");
                        helper.assertTrue(enough.inspection().status().endsWith("wrong_climate"),
                                "Installed capacity supplied a hot biome without paid climate output: " + enough);
                        RoomClimateState air = GreenhouseEnvironment.controllerAirState(level, controller, false);
                        helper.assertTrue(air != null, "Sufficient-capacity room supplied no shared air state");
                        // Isolate same-tick capacity invalidation from the electrical tick order.
                        air.addPaidDrive(controller.asLong(), level.getGameTime(), CropClimate.HOT, true);
                        helper.assertTrue(GreenhouseEnvironment.controllerInspection(level, controller).valid(),
                                "Paid Hot climate and sufficient capacity did not register the desert habitat");
                        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.HOT,
                                "Paid air did not reach the far end of its connected room");
                        level.setBlock(extra, level.getBlockState(extra).setValue(AirConditionerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
                        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.NEUTRAL,
                                "Rotating away one unit retained already-paid room climate");
                        helper.assertTrue(GreenhouseEnvironment.controllerInspection(level, controller).status().endsWith("insufficient_capacity"),
                                "Rotating away capacity kept a greenhouse biome");
                        level.setBlock(extra, level.getBlockState(extra).setValue(AirConditionerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
                        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.NEUTRAL,
                                "Restoring capacity resurrected an old unpaid climate drive");
                        air.addPaidDrive(controller.asLong(), level.getGameTime(), CropClimate.HOT, true);
                        level.setBlock(extra, Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, far) == CropClimate.NEUTRAL,
                                "Removing one unit retained already-paid room climate");
                        helper.succeed();
                    } finally { restore.run(); }
                });
            });
        } catch (RuntimeException | Error error) {
            restore.run();
            throw error;
        }
    }

    private static void afterRoomScan(GameTestHelper helper, BlockPos pos, int remainingTicks, Runnable restore,
                                      Consumer<GreenhouseEnvironment.ControllerRoomInfo> assertions) {
        try {
            var room = GreenhouseEnvironment.controllerRoomInfo(helper.getLevel(), pos);
            if (room.inspection().status().endsWith("scanning")) {
                helper.assertTrue(remainingTicks > 0, "Capacity fixture did not finish its ordered room survey: " + room);
                helper.runAfterDelay(1, () -> afterRoomScan(helper, pos, remainingTicks - 1, restore, assertions));
            } else {
                assertions.accept(room);
            }
        } catch (RuntimeException | Error error) {
            restore.run();
            throw error;
        }
    }

    private static void room(GameTestHelper helper, BlockPos min, int width, List<Long> tickets) {
        ServerLevel level = helper.getLevel();
        var plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        for (int cx = (min.getX() - 1) >> 4; cx <= (min.getX() + width) >> 4; cx++)
            for (int cz = (min.getZ() - 1) >> 4; cz <= (min.getZ() + 16) >> 4; cz++) {
                long ticket = ChunkPos.asLong(cx, cz);
                if (!level.getForcedChunks().contains(ticket)) {
                    level.setChunkForced(cx, cz, true);
                    tickets.add(ticket);
                }
                level.getChunk(cx, cz);
                var filled = FillBiomeCommand.fill(level, new BlockPos(cx * 16, min.getY() - 4, cz * 16),
                        new BlockPos(cx * 16 + 15, min.getY() + 6, cz * 16 + 15), plains);
                helper.assertTrue(filled.right().isEmpty(), "Could not prepare native capacity biome fixture");
            }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int x = -1; x <= width; x++) for (int z = -1; z <= 16; z++) {
            level.setBlock(min.offset(x, -1, z), Blocks.BRICKS.defaultBlockState(), flags);
            level.setBlock(min.offset(x, 2, z), Blocks.GLASS.defaultBlockState(), flags);
            for (int y = 0; y < 2; y++) {
                boolean wall = x == -1 || x == width || z == -1 || z == 16;
                level.setBlock(min.offset(x, y, z), (wall ? Blocks.BRICKS : Blocks.AIR).defaultBlockState(), flags);
            }
        }
        for (int x = 2; x < 6; x++) level.setBlock(min.offset(x, 0, 2), Blocks.DIAMOND_BLOCK.defaultBlockState(), flags);
    }
}
