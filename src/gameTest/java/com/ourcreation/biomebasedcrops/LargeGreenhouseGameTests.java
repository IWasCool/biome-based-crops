package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class LargeGreenhouseGameTests {
    private LargeGreenhouseGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "large_greenhouses", timeoutTicks = 600)
    public static void largeRoomScansIncrementallyAndChecksGlassOnlyAbovePlants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Long> tickets = new ArrayList<>();
        List<? extends String> oldMaterials = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        boolean oldEnabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        int oldMaximum = GreenhouseSettings.MAX_ROOM_BLOCKS.get(), oldBudget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        int oldReference = GreenhouseSettings.MATERIAL_REFERENCE_VOLUME.get();
        double oldRoof = GreenhouseSettings.MIN_ROOF_GLASS_RATIO.get(), oldWalls = GreenhouseSettings.MIN_WALL_GLASS_RATIO.get();
        Runnable restore = () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(oldMaterials);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(oldEnabled);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(oldMaximum);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(oldBudget);
            GreenhouseSettings.MATERIAL_REFERENCE_VOLUME.set(oldReference);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(oldRoof);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(oldWalls);
            GreenhouseEnvironment.clearCache();
            for (long ticket : tickets) {
                ChunkPos pos = new ChunkPos(ticket);
                level.setChunkForced(pos.x, pos.z, false);
            }
        };
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(262144);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(1024);
            GreenhouseSettings.MATERIAL_REFERENCE_VOLUME.set(512);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.5D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.1D);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(
                    "minecraft:forest=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1"));
            GreenhouseEnvironment.clearCache();
            // A dedicated elevated fixture does not overlap the 16-block test grid.
            BlockPos min = helper.absolutePos(new BlockPos(512, 100, 512));
            int width = 64, height = 32, depth = 64;
            int minChunkX = (min.getX() - 1) >> 4, maxChunkX = (min.getX() + width) >> 4;
            int minChunkZ = (min.getZ() - 1) >> 4, maxChunkZ = (min.getZ() + depth) >> 4;
            var plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long ticket = ChunkPos.asLong(cx, cz);
                if (!level.getForcedChunks().contains(ticket)) { level.setChunkForced(cx, cz, true); tickets.add(ticket); }
                level.getChunk(cx, cz);
                var filled = FillBiomeCommand.fill(level, new BlockPos(cx * 16, min.getY() - 4, cz * 16),
                        new BlockPos(cx * 16 + 15, min.getY() + height + 4, cz * 16 + 15), plains);
                helper.assertTrue(filled.right().isEmpty(), "Could not prepare native biome fixture");
            }
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            for (int x = -1; x <= width; x++) for (int z = -1; z <= depth; z++) {
                level.setBlock(min.offset(x, -1, z), Blocks.GLASS.defaultBlockState(), flags);
                Block roof = (x + z) % 3 == 0 ? Blocks.WHITE_STAINED_GLASS : (x + z) % 3 == 1 ? Blocks.GLASS_PANE : Blocks.RED_STAINED_GLASS;
                level.setBlock(min.offset(x, height, z), roof.defaultBlockState(), flags);
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++)
                    level.setBlock(min.offset(x, y, z), Blocks.AIR.defaultBlockState(), flags);
                for (int x = -1; x <= width; x++) {
                    level.setBlock(min.offset(x, y, -1), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), flags);
                    level.setBlock(min.offset(x, y, depth), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), flags);
                }
                for (int z = 0; z < depth; z++) {
                    level.setBlock(min.offset(-1, y, z), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), flags);
                    level.setBlock(min.offset(width, y, z), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), flags);
                }
            }
            // One idle marker permits intentional registration without providing thermal capacity.
            BlockPos marker = min.offset(2, 1, -1);
            level.setBlock(marker, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.SOUTH), flags);
            ((AirConditionerBlockEntity) level.getBlockEntity(marker)).setEnabled(false);
            // Reserve interior scenery so later material swaps preserve the topology.
            int totalMaterials = GreenhouseRecipes.scaledCount(4, width * height * depth);
            for (int i = 0; i < totalMaterials; i++)
                level.setBlock(min.offset(i % width, 0, i / width), Blocks.STONE.defaultBlockState(), flags);
            level.setBlock(min.offset(2, 0, 2), Blocks.DIAMOND_BLOCK.defaultBlockState(), flags);
            level.setBlock(min.offset(3, 0, 2), Blocks.DIAMOND_BLOCK.defaultBlockState(), flags);
            level.setBlock(min.offset(4, 0, 2), Blocks.EMERALD_BLOCK.defaultBlockState(), flags);
            level.setBlock(min.offset(5, 0, 2), Blocks.LAPIS_BLOCK.defaultBlockState(), flags);
            BlockPos inside = min.offset(32, 1, 32);
            var initial = GreenhouseEnvironment.inspect(level, inside);
            helper.assertTrue(initial.status().endsWith("scanning"), "131072-cell room was scanned synchronously");
            helper.assertTrue(level.getBiome(inside).is(Biomes.PLAINS), "Unfinished scan exposed a speculative biome");
            helper.runAfterDelay(240L, () -> {
                try {
                    var complete = GreenhouseEnvironment.inspectForBiome(level, inside, Biomes.FOREST.location());
                    helper.assertTrue(complete.enclosed(), "Large greenhouse did not finish bounded background scanning: " + complete);
                    helper.assertFalse(complete.valid(), "Four specimens validated a 131072-cell greenhouse without volume scaling");
                    helper.assertTrue(complete.status().endsWith("missing_specimens"), "Large sparse room failed for the wrong reason: " + complete);
                    helper.assertValueEqual(complete.roomVolume(), width * height * depth, "volume reported before material requirements are complete");
                    // Swap interior scenery without changing the sealed air cells.
                    int volume = width * height * depth;
                    int diamond = GreenhouseRecipes.scaledCount(2, volume);
                    int emerald = GreenhouseRecipes.scaledCount(1, volume);
                    int lapis = GreenhouseRecipes.scaledCount(1, volume);
                    helper.assertValueEqual(diamond, 512, "scaled diamond requirement");
                    helper.assertValueEqual(emerald, 256, "scaled emerald requirement");
                    for (int i = 0; i < diamond + emerald + lapis; i++) {
                        Block material = i < diamond ? Blocks.DIAMOND_BLOCK
                                : i < diamond + emerald ? Blocks.EMERALD_BLOCK : Blocks.LAPIS_BLOCK;
                        level.setBlock(min.offset(i % width, 0, i / width), material.defaultBlockState(), flags);
                    }
                    var furnished = GreenhouseEnvironment.inspect(level, inside);
                    helper.assertTrue(furnished.valid(), "Proportional interior materials did not validate large room: " + furnished);
                    helper.assertValueEqual(furnished.requiredSpecimens(), diamond + emerald + lapis, "scaled total material count");
                    helper.assertValueEqual(furnished.specimens(), furnished.requiredSpecimens(), "matched proportional material total");
                    var region = GreenhouseEnvironment.regionAt(level, inside);
                    helper.assertTrue(region != null && region.interior() instanceof LongSet, "Large interior was boxed or omitted");
                    helper.assertValueEqual(region.interior().size(), width * height * depth - totalMaterials, "large greenhouse air cells");
                    helper.assertTrue(level.getBiome(inside).is(Biomes.FOREST), "Large greenhouse did not expose its biome");
                    var stableCells = region.interior();
                    GreenhouseEnvironment.invalidateClimate();
                    helper.assertTrue(GreenhouseEnvironment.regionAt(level, inside).interior() == stableCells,
                            "Climate refresh copied or rescanned the large room topology");
                    for (int i = 0; i < 2050; i++) level.setBlock(min.offset(i % width, height, i / width), Blocks.OBSIDIAN.defaultBlockState(), flags);
                    helper.assertTrue(GreenhouseEnvironment.inspect(level, inside).valid(),
                            "Opaque roof tiles invalidated the registered biome");
                    helper.assertFalse(GreenhouseEnvironment.hasGrowingLight(level, min.offset(0, 1, 0)),
                            "A plant under opaque roofing received greenhouse light");
                    helper.assertTrue(GreenhouseEnvironment.hasGrowingLight(level, inside),
                            "A plant under remaining glass lost its lighting because other roof tiles are opaque");
                    for (int i = 0; i < 2050; i++) level.setBlock(min.offset(i % width, height, i / width), Blocks.GLASS.defaultBlockState(), flags);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            BlockPos north = min.offset(x, y, -1);
                            if (!level.getBlockState(north).is(GreenhouseContent.AIR_CONDITIONER.get()))
                                level.setBlock(north, Blocks.OBSIDIAN.defaultBlockState(), flags);
                            level.setBlock(min.offset(x, y, depth), Blocks.OBSIDIAN.defaultBlockState(), flags);
                        }
                        for (int z = 0; z < depth; z++) {
                            level.setBlock(min.offset(-1, y, z), Blocks.OBSIDIAN.defaultBlockState(), flags);
                            level.setBlock(min.offset(width, y, z), Blocks.OBSIDIAN.defaultBlockState(), flags);
                        }
                    }
                    for (int z = 0; z < 4; z++) level.setBlock(min.offset(-1, 0, z), Blocks.GLASS.defaultBlockState(), flags);
                    helper.assertTrue(GreenhouseEnvironment.inspect(level, inside).valid(), "Sealed opaque walls invalidated a large glass-roof greenhouse");
                    for (int y = 0; y < 4; y++) {
                        for (int x = 0; x < width; x++) {
                            BlockPos north = min.offset(x, y, -1);
                            if (!level.getBlockState(north).is(GreenhouseContent.AIR_CONDITIONER.get()))
                                level.setBlock(north, Blocks.GLASS.defaultBlockState(), flags);
                            level.setBlock(min.offset(x, y, depth), Blocks.GLASS.defaultBlockState(), flags);
                        }
                        for (int z = 0; z < depth; z++) {
                            level.setBlock(min.offset(-1, y, z), Blocks.GLASS.defaultBlockState(), flags);
                            level.setBlock(min.offset(width, y, z), Blocks.GLASS.defaultBlockState(), flags);
                        }
                    }
                    var repaired = GreenhouseEnvironment.regionAt(level, inside);
                    helper.assertTrue(repaired != null && repaired.interior() == stableCells, "Glazing-only changes restarted room topology");
                    BlockPos opening = new BlockPos(inside.getX(), min.getY() + height, inside.getZ());
                    level.setBlock(opening, Blocks.AIR.defaultBlockState(), flags);
                    var leaking = GreenhouseEnvironment.inspect(level, inside);
                    helper.assertTrue(leaking.valid() && leaking.status().endsWith("leaking"),
                            "A brief roof opening discarded the known large greenhouse: " + leaking);
                    helper.assertTrue(level.getBiome(inside).is(Biomes.FOREST), "Large room lost its biome during leak grace");
                    helper.assertTrue(GreenhouseEnvironment.regionAt(level, opening.above()) == null,
                            "Large leaking room expanded above its old roof");
                    level.setBlock(opening, Blocks.GLASS.defaultBlockState(), flags);
                    var resealed = GreenhouseEnvironment.regionAt(level, inside);
                    helper.assertTrue(resealed != null && resealed.interior() == stableCells
                                    && GreenhouseEnvironment.inspect(level, inside).status().endsWith("valid"),
                            "Resealing a large roof lost its room or climate");
                    helper.succeed();
                } finally { restore.run(); }
            });
        } catch (RuntimeException | Error exception) {
            restore.run(); throw exception;
        }
    }
}
