package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseHabitatGameTests {
    private GreenhouseHabitatGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void nativeFloorRequiresTheWholeFootprintAndBedsKeepTheirUnderlay(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1"));
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of("minecraft:forest=100:minecraft:sand"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.SAND);
            level.setBlock(center.offset(-5, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            var initial = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(initial.valid(), "Complete native floor failed: " + initial);
            helper.assertValueEqual(initial.floorArea(), 81, "actual floor footprint");
            helper.assertValueEqual(initial.floor().found(), 81, "native support count");
            helper.assertValueEqual(initial.floor().required(), 81, "whole-floor native requirement");
            var geometry = GreenhouseRoomScanner.completedAroundController(level, center);
            var cells = geometry.interior;
            BlockPos support = center.offset(2, -1, 2);
            level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            var wrong = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(wrong.status().endsWith("missing_floor"), "One wrong floor tile was accepted: " + wrong);
            helper.assertValueEqual(wrong.floor().found(), 80, "same-seal replacement refreshed floor count");
            helper.assertTrue(GreenhouseRoomScanner.completedAroundController(level, center).interior == cells,
                    "Changing a full floor block restarted topology");
            level.setBlock(support.below(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(GreenhouseEnvironment.inspect(level, center).floor().found(), 80,
                    "Buried sand under ordinary stone faked an exposed native floor");

            level.setBlock(support, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_ALL);
            level.setBlock(support.above(), Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Raised crop bed ignored its native sand underlay");
            CropProfile exactForest = exact("minecraft:forest", CropClimate.NEUTRAL);
            helper.assertTrue(CommonEvents.canGrow(level, support.above(), exactForest), "Native-backed crop bed could not grow its emulated biome crop");
            level.setBlock(support.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            var changedUnderlay = GreenhouseEnvironment.inspect(level, center);
            helper.assertValueEqual(changedUnderlay.floor().found(), 80, "Underlay two below air did not refresh immediately");
            helper.assertFalse(CommonEvents.canGrow(level, support.above(), exactForest), "Wrong crop-bed underlay retained exact biome support");
            helper.assertTrue(GreenhouseRoomScanner.completedAroundController(level, center).interior == cells,
                    "Underlay replacement restarted topology");
            level.setBlock(support.below(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Restoring native underlay did not recover the habitat");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_forest", timeoutTicks = 60)
    public static void forestNeedsInteriorTreesAndCanopiesInsteadOfWoodenWalls(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.GRASS_BLOCK);
            int index = 0;
            for (int y = 0; y < 5; y++) for (int z = -4; z <= 4; z++) {
                Block material = index < 16 ? Blocks.OAK_LOG : index < 24 ? Blocks.BIRCH_LOG : Blocks.OAK_LEAVES;
                level.setBlock(center.offset(-5, y, z), material.defaultBlockState(), Block.UPDATE_ALL);
                index++;
            }
            for (int y = 0; y < 5; y++) for (int z = -4; z <= 4; z++) {
                level.setBlock(center.offset(5, y, z), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
            }
            GreenhouseEnvironment.inspect(level, center);
            var geometry = GreenhouseRoomScanner.completedAroundController(level, center);
            helper.assertValueEqual(geometry.features.getOrDefault(id("oak_log"), 0), 0, "Exterior wall logs counted as living trees");
            helper.assertValueEqual(geometry.canopy.getOrDefault(id("oak_leaves"), 0), 0, "Exterior leaf walls counted as raised canopy");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, exact("minecraft:forest", CropClimate.NEUTRAL)),
                    "Wooden and leaf walls emulated a forest");
            for (int y = 0; y < 5; y++) for (int z = -4; z <= 4; z++) {
                level.setBlock(center.offset(-5, y, z), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(center.offset(5, y, z), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
            forestVegetation(level, center);
            awaitForest(helper, center, settings, 0);
        } catch (RuntimeException | Error exception) {
            settings.restore();
            throw exception;
        }
    }

    private static void awaitForest(GameTestHelper helper, BlockPos center, Settings settings, int attempts) {
        ServerLevel level = helper.getLevel();
        var complete = GreenhouseEnvironment.inspect(level, center);
        if (complete.status().endsWith("scanning") && attempts < 20) {
            helper.runAfterDelay(1, () -> awaitForest(helper, center, settings, attempts + 1));
            return;
        }
        try {
            helper.assertTrue(complete.valid() && id("forest").equals(complete.targetBiome()), "Real forest habitat failed: " + complete);
            helper.assertValueEqual(complete.floorArea(), 81, "forest footprint");
            helper.assertValueEqual(complete.floor().required(), 73, "90 percent native floor rounded upward");
            helper.assertTrue(complete.floor().found() >= 73, "Standing trees left too little native soil");
            helper.assertValueEqual(progress(complete, "oak_log").required(), 16, "substantial primary tree quota");
            helper.assertValueEqual(progress(complete, "oak_leaves").required(), 64, "substantial canopy quota");
            helper.assertValueEqual(progress(complete, "birch_log").required(), 11, "secondary trees scale with actual footprint");
            var planted = GreenhouseRoomScanner.completedAroundController(level, center);
            helper.assertValueEqual(planted.features.getOrDefault(id("oak_log"), 0), 16, "Every exposed block in four normal four-high oak trunks must count");
            helper.assertValueEqual(planted.features.getOrDefault(id("birch_log"), 0), 12, "Normal birch trunk top caps must count");
            helper.succeed();
        } finally { settings.restore(); }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void curatedDesertUsesSandFloorCactusAndNativeStructure(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.SAND);
            for (int y = 0; y < 4; y++) for (int z = -4; z <= -1; z++) {
                level.setBlock(center.offset(-5, y, z), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            int[][] cactus = {{-3, -3}, {-1, -3}, {1, -3}, {3, -3}, {-3, -1}, {-1, -1}};
            for (int[] at : cactus) level.setBlock(center.offset(at[0], 0, at[1]), Blocks.CACTUS.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = -4; x <= 3; x++) level.setBlock(center.offset(x, 0, 3), Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos controller = center.north(2);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            ((AirConditionerBlockEntity) level.getBlockEntity(controller)).setClimate(CropClimate.HOT);
            AutoGreenhouseGameTests.conditionFixtureAir(level, controller, CropClimate.HOT);
            var complete = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(complete.valid() && id("desert").equals(complete.targetBiome()), "Curated desert habitat failed: " + complete);
            helper.assertValueEqual(complete.floor().found(), 81, "Sand beneath the floor-standing controller was lost");
            helper.assertValueEqual(complete.floor().required(), 81, "desert whole-floor sand requirement");
            helper.assertValueEqual(progress(complete, "cactus").required(), 6, "cactus density for81floor cells");
            helper.assertValueEqual(progress(complete, "sandstone").found(), 16, "Native sandstone frame was not accepted");
            BlockPos wrong = center.offset(2, -1, 2);
            level.setBlock(wrong, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "A corner sand display replaced the mandatory whole sand floor");
        });
    }

    static void forestVegetation(ServerLevel level, BlockPos center) {
        int[][] roots = {{-3, -3}, {-1, -3}, {1, -3}, {3, -3}, {-3, -1}, {-1, -1}, {1, -1}};
        Set<Long> clearCanopy = new java.util.HashSet<>();
        for (int i = 0; i < roots.length; i++) {
            int x = roots[i][0], z = roots[i][1];
            Block wood = i < 4 ? Blocks.OAK_LOG : Blocks.BIRCH_LOG;
            for (int y = 0; y < 4; y++) level.setBlock(center.offset(x, y, z), wood.defaultBlockState(), Block.UPDATE_ALL);
            clearCanopy.add(center.offset(x, 2, z).asLong());
            clearCanopy.add(center.offset(x + 1, 2, z).asLong());
        }
        int leaves = 0;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            BlockPos at = center.offset(x, 2, z);
            if (leaves < 64 && !clearCanopy.contains(at.asLong())) {
                level.setBlock(at, Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), Block.UPDATE_ALL);
                leaves++;
            }
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void actualAirColumnsRejectSteppedRoofsWallsAndRaisedGroundAsCanopy(GameTestHelper helper) {
        run(helper, () -> {
            helper.setBiome(Biomes.PLAINS);
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
            for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 4, 2))) {
                boolean floor = at.getY() == center.getY() - 1;
                boolean shell = Math.abs(at.getX() - center.getX()) == 2 || Math.abs(at.getZ() - center.getZ()) == 2
                        || at.getY() == center.getY() + 4;
                level.setBlock(at, floor ? Blocks.GRASS_BLOCK.defaultBlockState()
                        : shell ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            level.setBlock(center.offset(0, 1, 0), leaves, Block.UPDATE_ALL);
            level.setBlock(center.offset(1, 1, 0), leaves, Block.UPDATE_ALL);
            level.setBlock(center.offset(2, 1, 0), leaves, Block.UPDATE_ALL);
            level.setBlock(center.offset(-1, 2, 0), leaves, Block.UPDATE_ALL);
            level.setBlock(center.offset(-1, 3, 0), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(0, 0, 1), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(0, 1, 1), leaves, Block.UPDATE_ALL);
            GreenhouseEnvironment.clearCache();
            GreenhouseEnvironment.inspect(level, center);
            var geometry = GreenhouseRoomScanner.completedAroundController(level, center);
            helper.assertTrue(geometry != null, "Small stepped enclosure was not scanned");
            helper.assertValueEqual(geometry.canopy.getOrDefault(id("oak_leaves"), 0), 2,
                    "Only interior raised leaves may count; stepped roof, outer wall and terrace support must be excluded");
            level.setBlock(center.offset(1, 4, 1), leaves, Block.UPDATE_ALL);
            helper.assertValueEqual(geometry.canopy.getOrDefault(id("oak_leaves"), 0), 2,
                    "Same-seal roof replacement entered the living canopy count");
        });
    }

    private static BlockPos room(GameTestHelper helper, Block floor) {
        helper.setBiome(Biomes.PLAINS);
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-5, -1, -5), center.offset(5, 5, 5))) {
            boolean ground = at.getY() == center.getY() - 1;
            boolean wall = Math.abs(at.getX() - center.getX()) == 5 || Math.abs(at.getZ() - center.getZ()) == 5 || at.getY() == center.getY() + 5;
            level.setBlock(at, ground ? floor.defaultBlockState() : wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        GreenhouseEnvironment.clearCache();
        return center;
    }

    private static GreenhouseEnvironment.RequirementProgress progress(GreenhouseEnvironment.Inspection inspection, String block) {
        return inspection.requirements().stream().filter(requirement -> requirement.block().equals(id(block))).findFirst().orElseThrow();
    }

    private static CropProfile exact(String biome, CropClimate climate) {
        return new CropProfile(Set.of(climate), List.of(BiomeSelector.parse(biome).orElseThrow()));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }

    private static void run(GameTestHelper helper, Runnable test) {
        Settings settings = new Settings();
        try {
            test.run();
            helper.succeed();
        } finally { settings.restore(); }
    }

    private static final class Settings {
        private final boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        private final List<? extends String> materials = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());
        private final List<? extends String> floors = List.copyOf(GreenhouseSettings.BIOME_FLOOR_RULES.get());

        Settings() {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of());
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of());
            GreenhouseEnvironment.clearCache();
        }

        void restore() {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            GreenhouseSettings.BIOME_FLOOR_RULES.set(floors);
            GreenhouseEnvironment.clearCache();
        }
    }
}
