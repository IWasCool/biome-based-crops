package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseHabitatGameTests {
    private GreenhouseHabitatGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void naturallyMatchingClimateNeedsAnIntentionalControllerForBiomeRegistration(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1"));
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of("minecraft:forest=100:minecraft:sand"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.SAND, false);
            level.setBlock(center.offset(-3, 0, -3), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            CropProfile forest = exact("minecraft:forest", CropClimate.NEUTRAL);
            helper.assertFalse(forest.allows(GreenhouseEnvironment.nativeBiome(level, center), center),
                    "Fixture already belongs to the crop's native biome");
            var inspection = GreenhouseEnvironment.inspect(level, center);
            helper.assertFalse(inspection.valid(), "An ordinary house registered a biome without a thermoregulator");
            helper.assertTrue(inspection.status().endsWith("missing_controller"), "Missing registration controller was not reported: " + inspection);
            var geometry = GreenhouseRoomScanner.completedAroundController(level, center);
            helper.assertTrue(geometry != null && geometry.machines.isEmpty(), "Fixture contains a climate controller");
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == CropClimate.NEUTRAL,
                    "Fixture's natural climate does not match its forest habitat");
            helper.assertTrue(CommonEvents.canGrow(level, center, forest),
                    "The biome registration gate incorrectly disabled greenhouse-assisted growth");
            helper.assertTrue(CommonEvents.canGrowNaturally(level, center, Blocks.WHEAT.defaultBlockState()),
                    "Native-climate crops incorrectly required a registration controller");
            helper.assertTrue(CommonEvents.canGrowNaturally(level, center, Blocks.OAK_SAPLING.defaultBlockState()),
                    "Native-climate saplings incorrectly required a registration controller");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null, "Unregistered house published a biome region");
            var nativeSpawns = creatureSpawns(level, center);
            assertUnregisteredForestGrowth(helper, level, center);
            helper.assertTrue(nativeSpawns.equals(creatureSpawns(level, center)), "Greenhouse-assisted growth changed the native spawn table");

            addWallController(level, center, CropClimate.NEUTRAL);
            var controller = (AirConditionerBlockEntity) level.getBlockEntity(center.west(5).above(3));
            controller.setEnabled(false);
            helper.assertValueEqual(controller.energyStored(), 0, "Registration marker unexpectedly had power");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "An installed idle controller did not permit natural-climate registration");
            helper.assertFalse(controller.hasPaidAirflow(), "Presence-only registration started thermal conditioning");
            helper.assertTrue(CommonEvents.canGrow(level, center, forest), "Intentional natural-climate greenhouse did not support its registered biome");
            level.setBlock(center.west(5).above(3), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            assertUnregisteredForestGrowth(helper, level, center);
            helper.assertTrue(nativeSpawns.equals(creatureSpawns(level, center)), "Removing the marker retained a foreign spawn table");
        });
    }

    private static void assertUnregisteredForestGrowth(GameTestHelper helper, ServerLevel level, BlockPos center) {
        List<? extends String> oldRules = List.copyOf(Config.CROP_BIOME_RULES.get());
        try {
            Config.CROP_BIOME_RULES.set(List.of("wheat=minecraft:forest", "oak_sapling=minecraft:forest"));
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            for (var state : List.of(Blocks.WHEAT.defaultBlockState(), Blocks.OAK_SAPLING.defaultBlockState())) {
                helper.assertTrue(CropMatcher.profileForState(state).orElseThrow().hasBiomeRule(),
                        "Plant fixture did not have an exact foreign-biome requirement");
                helper.assertTrue(CommonEvents.canGrowNaturally(level, center, state),
                        "Unregistered complete habitat blocked a matching crop or sapling");
                helper.assertTrue(CommonEvents.canGrowWithFertilizer(level, center, state),
                        "Registration marker became a fertilizer requirement");
            }
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).status().endsWith("missing_controller"),
                    "A successful growth check leaked its habitat into registered inspection");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null && level.getBiome(center).is(Biomes.PLAINS),
                    "A successful growth check changed the world's registered biome");
            var atmosphere = GreenhouseEnvironment.atmosphereRegionAt(level, center);
            helper.assertTrue(atmosphere == null || atmosphere.biome() == null,
                    "A successful growth check published a client biome snapshot");
        } finally {
            Config.CROP_BIOME_RULES.set(oldRules);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
        }
    }

    private static List<?> creatureSpawns(ServerLevel level, BlockPos pos) {
        try {
            var lookup = NaturalSpawner.class.getDeclaredMethod("mobsAt", ServerLevel.class, StructureManager.class,
                    ChunkGenerator.class, MobCategory.class, BlockPos.class, Holder.class);
            lookup.setAccessible(true);
            return ((net.minecraft.util.random.WeightedRandomList<?>) lookup.invoke(null, level, level.structureManager(),
                    level.getChunkSource().getGenerator(), MobCategory.CREATURE, pos, null)).unwrap();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not exercise the vanilla natural-spawn table lookup", exception);
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void unfueledTanControllerCanMarkAnOtherwiseNaturalHabitat(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1"));
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of("minecraft:forest=100:minecraft:sand"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.SAND, false);
            level.setBlock(center.offset(-3, 0, -3), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Controller-free fixture unexpectedly registered");
            BlockPos marker = center.west(5).above(2);
            Block tan = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toughasnails:thermoregulator"));
            level.setBlock(marker, tan.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Unfueled TAN controller was not accepted as an installed registration marker");
            helper.assertTrue(ThermoregulatorDetector.providedClimateAt(level, marker, center).isEmpty(), "Unfueled TAN fixture unexpectedly supplied heat");
            level.setBlock(marker, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Removing TAN marker left the cached biome registered");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void nativeFloorRequiresTheWholeFootprintAndBedsKeepTheirUnderlay(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1"));
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of("minecraft:forest=100:minecraft:sand"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.SAND);
            level.setBlock(center.offset(-3, 0, -3), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
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
            addWallController(level, center, CropClimate.NEUTRAL);
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
            addWallController(level, center, CropClimate.NEUTRAL);
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
            helper.assertValueEqual(complete.roomVolume(), 405, "Planting trees and canopy must not shrink the habitat percentage denominator");
            helper.assertValueEqual(progress(complete, "oak_log").required(), 13, "primary tree volume percentage");
            helper.assertValueEqual(progress(complete, "oak_leaves").required(), 53, "canopy whole volume percentage");
            helper.assertValueEqual(progress(complete, "birch_log").required(), 9, "secondary tree whole volume percentage");
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
            for (int x = -4; x <= 3; x++) for (int z = 1; z <= 2; z++)
                level.setBlock(center.offset(x, 0, z), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(-4, 1, 1), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_ALL);
            int[][] cactus = {{-3, -3}, {-1, -3}, {1, -3}, {3, -3}, {-3, -1}, {-1, -1}};
            for (int[] at : cactus) level.setBlock(center.offset(at[0], 0, at[1]), Blocks.CACTUS.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = -4; x <= 4; x++) level.setBlock(center.offset(x, 0, 3), Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos controller = center.north(2);
            level.setBlock(controller, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            ((AirConditionerBlockEntity) level.getBlockEntity(controller)).setClimate(CropClimate.HOT);
            addWallController(level, center, CropClimate.HOT);
            AutoGreenhouseGameTests.conditionFixtureAir(level, controller, CropClimate.HOT);
            var complete = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(complete.valid() && id("desert").equals(complete.targetBiome()), "Curated desert habitat failed: " + complete);
            helper.assertValueEqual(complete.floor().found(), 81, "Sand beneath the floor-standing controller was lost");
            helper.assertValueEqual(complete.floor().required(), 81, "desert whole-floor sand requirement");
            helper.assertValueEqual(progress(complete, "cactus").required(), 5, "cactus whole volume percentage");
            helper.assertValueEqual(progress(complete, "sandstone").required(), 13, "sandstone volume percentage");
            helper.assertValueEqual(progress(complete, "sandstone").found(), 17, "Native interior sandstone boulders were not accepted");
            helper.assertValueEqual(complete.roomVolume(), 405, "Boulders and the controller must not shrink habitat size");
            helper.assertValueEqual(GreenhouseEnvironment.controllerAirState(level, controller, false).roomVolume(), 387,
                    "Thermal physics must still use the actual connected air volume");
            BlockPos nativeBase = center.offset(-4, -1, 1);
            var cells = GreenhouseRoomScanner.completedAroundController(level, center).interior;
            level.setBlock(nativeBase, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(GreenhouseEnvironment.inspect(level, center).floor().found(), 80,
                    "Native foundation beneath a two-high boulder did not refresh immediately");
            helper.assertTrue(GreenhouseRoomScanner.completedAroundController(level, center).interior == cells,
                    "Changing native ground beneath scenery restarted topology");
            level.setBlock(nativeBase, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Restoring scenery's native base did not recover desert");
            BlockPos wall = center.offset(5, 2, 0);
            level.setBlock(wall, Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_ALL);
            var opaqueWall = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(opaqueWall.valid(), "A sealed opaque wall invalidated the glass-roof greenhouse: " + opaqueWall);
            helper.assertValueEqual(GreenhouseRoomScanner.completedAroundController(level, center).inside.getOrDefault(id("sandstone"), 0), 17,
                    "An exterior sandstone wall counted as interior scenery");
            level.setBlock(wall, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.above(5), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "An opaque roof tile removed the desert biome");
            helper.assertFalse(GreenhouseEnvironment.hasGrowingLight(level, center), "An opaque roof tile admitted plant skylight");
            helper.assertFalse(CommonEvents.canGrow(level, center, exact("minecraft:desert", CropClimate.HOT)),
                    "A foreign-biome crop grew under an opaque roof tile");
            level.setBlock(center.above(5), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos wrong = center.offset(2, -1, 2);
            level.setBlock(wrong, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "A corner sand display replaced the mandatory whole sand floor");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void opaqueRoofsKeepTheBiomeAndOnlyBlockPlantsBelowTheirColumns(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.GRASS_BLOCK);
            level.setBlock(center.offset(2, 0, 2), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Initial glazed room was invalid");
            var roofs = new java.util.ArrayList<BlockPos>();
            var walls = new java.util.ArrayList<BlockPos>();
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) roofs.add(center.offset(x, 5, z));
            for (int y = 0; y < 5; y++) for (int along = -4; along <= 4; along++) {
                walls.add(center.offset(-5, y, along));
                walls.add(center.offset(5, y, along));
                walls.add(center.offset(along, y, -5));
                walls.add(center.offset(along, y, 5));
            }
            for (BlockPos wall : walls) {
                if (!level.getBlockState(wall).is(GreenhouseContent.AIR_CONDITIONER.get()))
                    level.setBlock(wall, Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            }
            var decorated = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(decorated.valid(), "A sealed brick room with a glass roof was rejected: " + decorated);
            helper.assertValueEqual(decorated.glazing().roofFound(), 81, "informational roof glass count");
            helper.assertValueEqual(decorated.glazing().wallRequired(), 0, "wall glass must not be required");
            helper.assertValueEqual(decorated.glazing().wallBlocks(), 0, "fixture must contain no wall windows");

            level.setBlock(roofs.getFirst(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            var partialRoof = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(partialRoof.valid(), "An opaque roof tile invalidated the registered biome");
            helper.assertValueEqual(partialRoof.glazing().roofFound(), 80, "informational partial roof glass count");
            CropProfile forest = exact("minecraft:forest", CropClimate.NEUTRAL);
            BlockPos shaded = roofs.getFirst().below(5);
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, shaded, forest), "A shaded crop received greenhouse plant lighting");
            helper.assertTrue(CommonEvents.canGrow(level, center, forest), "One opaque column blocked a crop under a different glass column");
            for (BlockPos roof : roofs) level.setBlock(roof, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "A fully opaque roof removed the biome");
            helper.assertTrue(level.getBiome(center).is(Biomes.FOREST), "F3 biome lookup lost the opaque-roof habitat");
            helper.assertFalse(CommonEvents.canGrow(level, center, forest), "A foreign-biome crop grew under a fully opaque roof");
            level.setBlock(center.above(5), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(CommonEvents.canGrow(level, center, forest), "A single sky-exposed glass column did not light its crop");
            helper.assertFalse(CommonEvents.canGrow(level, center.south(), forest), "Plant lighting spread sideways under an opaque roof");
            for (BlockPos roof : roofs) level.setBlock(roof, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Restoring the glass roof did not recover the greenhouse");
            level.setBlock(center.offset(-5, 2, 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            var leaking = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(leaking.valid() && leaking.status().endsWith("leaking"),
                    "A brief wall hole discarded the known greenhouse: " + leaking);
            helper.assertTrue(level.getBiome(center).is(Biomes.FOREST), "Wall leak grace lost the room's biome");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center.west(6)) == null,
                    "The leaking room expanded into exterior air");
            level.setBlock(center.offset(-5, 2, 1), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).status().endsWith("valid"),
                    "Resealing the wall did not recover the greenhouse");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_basalt_habitat")
    public static void basaltFloorMagmaAndHotClimateRegisterTheActualBasaltDeltasBiome(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.BASALT);
            List<BlockPos> magma = List.of(center.offset(-3, -1, -3), center.offset(-1, -1, -3), center.offset(1, -1, -3));
            for (BlockPos at : magma) level.setBlock(at, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            var interiorMagma = new java.util.ArrayList<BlockPos>();
            for (int x = -4; x <= 4; x++) interiorMagma.add(center.offset(x, 0, 2));
            for (int x = -4; x <= -1; x++) interiorMagma.add(center.offset(x, 0, 3));
            // Reserve solid scenery before conditioning so material swaps keep the same room topology.
            for (BlockPos at : interiorMagma) level.setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos machine = center.north(2);
            level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            ((AirConditionerBlockEntity) level.getBlockEntity(machine)).setClimate(CropClimate.HOT);
            addWallController(level, center, CropClimate.HOT);
            AutoGreenhouseGameTests.conditionFixtureAir(level, machine, CropClimate.HOT);
            var floorOnly = GreenhouseEnvironment.inspect(level, center);
            helper.assertFalse(floorOnly.valid(), "Magma embedded in the foundation counted as interior habitat");
            helper.assertValueEqual(progress(floorOnly, "magma_block").found(), 0, "Foundation magma must not count as habitat");
            helper.assertValueEqual(progress(floorOnly, "magma_block").required(), 13, "Whole three-percent interior magma quota");
            for (BlockPos at : interiorMagma) level.setBlock(at, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            var actual = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(actual.valid() && actual.targetBiome().equals(id("basalt_deltas")), "Basalt habitat failed to register: " + actual);
            helper.assertValueEqual(progress(actual, "magma_block").required(), 13, "Whole three-percent interior magma quota");
            helper.assertValueEqual(progress(actual, "magma_block").found(), 13, "Only interior magma may count");
            helper.assertTrue(level.getBiome(center).is(Biomes.BASALT_DELTAS), "Room did not expose the actual vanilla Basalt Deltas biome");
            level.setBlock(interiorMagma.getFirst(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "Less than three-percent magma still registered");
            level.setBlock(interiorMagma.getFirst(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Restoring required magma did not recover the biome");
            AutoGreenhouseGameTests.conditionFixtureAir(level, machine, CropClimate.ICY);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "An Icy room registered the Hot Basalt Deltas habitat");
            helper.assertFalse(level.getBiome(center).is(Biomes.BASALT_DELTAS), "Wrong climate retained the Basalt Deltas biome");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "sealed_temperature_containment")
    public static void sealedOpaqueRoomsRetainClimateDuringBriefWallOpenings(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:desert=minecraft:diamond_block*1"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.GRASS_BLOCK);
            level.setBlock(center.west(2), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            for (int y = 0; y < 5; y++) for (int along = -4; along <= 4; along++) {
                level.setBlock(center.offset(-5, y, along), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(center.offset(5, y, along), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(center.offset(along, y, -5), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(center.offset(along, y, 5), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockPos machine = center.north();
            level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            ((AirConditionerBlockEntity) level.getBlockEntity(machine)).setClimate(CropClimate.HOT);
            addWallController(level, center, CropClimate.HOT);
            AutoGreenhouseGameTests.conditionFixtureAir(level, machine, CropClimate.HOT);
            CropClimate conditioned = GreenhouseEnvironment.currentClimate(level, center);
            helper.assertTrue(conditioned == CropClimate.HOT, "Sealed opaque walls did not retain conditioned air");
            level.setBlock(center.above(5), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == conditioned,
                    "Replacing roof glass with a sealed block released stored temperature");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "A sealed opaque roof removed the biome");
            helper.assertFalse(GreenhouseEnvironment.hasGrowingLight(level, center), "The opaque roof admitted plant skylight");
            level.setBlock(center.south(5), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == conditioned,
                    "A brief wall opening immediately discarded the room's conditioned air");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "A brief wall opening lost the retained biome");
            helper.assertFalse(GreenhouseEnvironment.hasGrowingLight(level, center), "Leak grace lit a crop below an opaque column");
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center.south(6)) == CropClimate.NEUTRAL,
                    "Conditioned air escaped the retained interior boundary");
            level.setBlock(center.south(5), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == conditioned,
                    "Resealing the wall lost the room's climate");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void inspectingAMaterialPlanDoesNotChangeTheActualGreenhouse(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1",
                    "minecraft:desert=minecraft:emerald_block*3"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.GRASS_BLOCK);
            level.setBlock(center.offset(2, 0, 2), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            var original = GreenhouseEnvironment.inspect(level, center);
            helper.assertTrue(original.valid() && id("forest").equals(original.targetBiome()), "Initial forest was invalid: " + original);
            var planned = GreenhouseEnvironment.inspectForBiome(level, center, id("desert"));
            helper.assertTrue(id("desert").equals(planned.targetBiome()), "Material inspection ignored the selected biome");
            helper.assertValueEqual(progress(planned, "emerald_block").required() - progress(planned, "emerald_block").found(), 3,
                    "exact emerald block deficit");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).targetBiome().equals(id("forest")), "Planning changed the actual biome");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center).biome().equals(id("forest")), "Planning changed the biome overlay");
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "native_greenhouse_habitats")
    public static void glassRoofAllowsSealedFunctionalWallsWithoutCountLimits(GameTestHelper helper) {
        run(helper, () -> {
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of("minecraft:forest=minecraft:diamond_block*1"));
            ServerLevel level = helper.getLevel();
            BlockPos center = room(helper, Blocks.GRASS_BLOCK);
            level.setBlock(center, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos query = center.south();
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "Full glass fixture was invalid");
            for (int z : new int[]{-3, -1, 1, 3})
                level.setBlock(center.offset(-5, 0, z), Blocks.IRON_TRAPDOOR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "Closed entrance cells were not exempt from glass");
            level.setBlock(center.offset(5, 0, 0), Blocks.IRON_TRAPDOOR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "A fifth closed entrance invalidated a sealed wall");
            level.setBlock(center.offset(5, 0, 0), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            for (int z : new int[]{-3, -1, 1, 3})
                level.setBlock(center.offset(-5, 0, z), GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "Four functional wall controllers were rejected");
            level.setBlock(center.offset(5, 0, 0), GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "A fifth climate device invalidated a sealed wall");
            BlockPos roof = center.above(5);
            level.setBlock(roof, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "A sealed roof controller removed the biome");
            helper.assertFalse(GreenhouseEnvironment.hasGrowingLight(level, center), "A roof controller admitted plant skylight");
            helper.assertTrue(GreenhouseEnvironment.hasGrowingLight(level, query), "A roof controller blocked a different glass column");
            level.setBlock(roof, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "Restoring the roof glass did not recover functional walls");
            BlockPos door = center.offset(5, 0, 2);
            level.setBlock(door, Blocks.IRON_TRAPDOOR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).valid(), "A closed door did not seal the room");
            level.setBlock(door, level.getBlockState(door).setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
            var leaking = GreenhouseEnvironment.inspect(level, query);
            helper.assertTrue(leaking.valid() && leaking.status().endsWith("leaking"),
                    "A briefly opened door discarded the known greenhouse: " + leaking);
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, door.east()) == null,
                    "The open door expanded its greenhouse into exterior air");
            level.setBlock(door, level.getBlockState(door).setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, query).status().endsWith("valid"),
                    "Closing the door did not restore the greenhouse");
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
        return room(helper, floor, true);
    }

    private static BlockPos room(GameTestHelper helper, Block floor, boolean registrationController) {
        helper.setBiome(Biomes.PLAINS);
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-5, -1, -5), center.offset(5, 5, 5))) {
            boolean ground = at.getY() == center.getY() - 1;
            boolean wall = Math.abs(at.getX() - center.getX()) == 5 || Math.abs(at.getZ() - center.getZ()) == 5 || at.getY() == center.getY() + 5;
            level.setBlock(at, ground ? floor.defaultBlockState() : wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (registrationController) addWallController(level, center, CropClimate.NEUTRAL);
        GreenhouseEnvironment.clearCache();
        return center;
    }

    private static void addWallController(ServerLevel level, BlockPos center, CropClimate climate) {
        // Keep the outlet above the generated Y+2 canopy, with a clear interior air cell.
        BlockPos position = center.west(5).above(3);
        level.setBlock(position, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                .setValue(AirConditionerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        var controller = (AirConditionerBlockEntity) level.getBlockEntity(position);
        controller.setClimate(climate);
        // These unpowered fixtures count as installed markers/capacity, not background scan sources.
        controller.setEnabled(false);
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
        private final double roof = GreenhouseSettings.MIN_ROOF_GLASS_RATIO.get(), walls = GreenhouseSettings.MIN_WALL_GLASS_RATIO.get();

        Settings() {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of());
            GreenhouseSettings.BIOME_FLOOR_RULES.set(List.of());
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(1.0D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.0D);
            GreenhouseEnvironment.clearCache();
        }

        void restore() {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(materials);
            GreenhouseSettings.BIOME_FLOOR_RULES.set(floors);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(roof);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(walls);
            GreenhouseEnvironment.clearCache();
        }
    }
}
