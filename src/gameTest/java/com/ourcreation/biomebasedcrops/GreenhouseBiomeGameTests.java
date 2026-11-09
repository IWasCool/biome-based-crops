package com.ourcreation.biomebasedcrops;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseBiomeGameTests {
    private static final BlockPos CENTER = new BlockPos(8, 2, 8);

    private GreenhouseBiomeGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_biome_overlay")
    public static void actualBiomeQueriesChangeOnlyTheEnclosedInterior(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            BlockPos center = prepareDesertRoom(helper);
            ServerLevel level = helper.getLevel();
            Holder<Biome> rawQuart = level.getChunkAt(center).getNoiseBiome(
                    QuartPos.fromBlock(center.getX()), QuartPos.fromBlock(center.getY()), QuartPos.fromBlock(center.getZ()));
            CropClimate nativeClimate = CropClimate.at(GreenhouseEnvironment.nativeBiome(level, center), center);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "Fixture did not recognize its desert greenhouse");

            for (BlockPos interior : List.of(center, center.west(4), center.east(4), center.above(4))) {
                helper.assertTrue(level.getBiome(interior).is(Biomes.DESERT), "Level.getBiome did not expose the greenhouse at " + interior);
                helper.assertTrue(level.getBiomeManager().getBiome(interior).is(Biomes.DESERT), "BiomeManager bypassed the greenhouse at " + interior);
                helper.assertTrue(level.getBiomeManager().getNoiseBiomeAtPosition(interior).is(Biomes.DESERT),
                        "Exact-position noise query bypassed the greenhouse at " + interior);
                helper.assertTrue(level.getBiomeManager().getNoiseBiomeAtPosition(interior.getX() + 0.5,
                        interior.getY() + 0.5, interior.getZ() + 0.5).is(Biomes.DESERT),
                        "Double-coordinate biome query bypassed the greenhouse at " + interior);
            }
            for (BlockPos outside : List.of(center.east(5), center.east(6), center.above(5), center.below())) {
                helper.assertTrue(level.getBiome(outside).equals(GreenhouseEnvironment.nativeBiome(level, outside)),
                        "A solid shell or exterior position borrowed the greenhouse biome: " + outside);
            }
            // Repeated emulated queries must not turn the emulated biome into the next
            // native baseline, or apply the heaters to the already heated desert again.
            for (int query = 0; query < 20; query++) {
                GreenhouseEnvironment.clearCache();
                helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Overlay became unstable after a fresh scan");
                helper.assertTrue(GreenhouseEnvironment.inspect(level, center).baseClimate() == nativeClimate,
                        "Greenhouse fed its emulated biome back into the native temperature baseline");
            }
            Holder<Biome> afterQuart = level.getChunkAt(center).getNoiseBiome(
                    QuartPos.fromBlock(center.getX()), QuartPos.fromBlock(center.getY()), QuartPos.fromBlock(center.getZ()));
            helper.assertTrue(rawQuart.equals(afterQuart), "Greenhouse permanently rewrote the chunk biome palette");
            helper.assertTrue(level.getNoiseBiome(QuartPos.fromBlock(center.getX()), QuartPos.fromBlock(center.getY()),
                    QuartPos.fromBlock(center.getZ())).equals(rawQuart), "Raw quart query leaked the room into a whole biome cell");
            helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, center).is(Biomes.PLAINS), "Native biome accessor returned the overlay");
            helper.succeed();
        } finally {
            settings.restore();
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_biome_overlay")
    public static void naturalSpawnTableLookupUsesTheRoomBiome(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            BlockPos center = prepareDesertRoom(helper);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(hasHuskSpawn(level, center), "Natural-spawn table lookup ignored the emulated desert biome");
            BlockPos outside = center.east(6);
            helper.assertFalse(hasHuskSpawn(level, outside), "Exterior plains inherited desert husk spawns");

            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            helper.assertTrue(level.getBiome(center).is(Biomes.PLAINS), "Disabling greenhouses left the desert overlay active");
            helper.assertFalse(hasHuskSpawn(level, center), "Disabling the overlay retained the desert natural-spawn table");
            helper.succeed();
        } finally {
            settings.restore();
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_biome_overlay", timeoutTicks = 100)
    public static void openingRoofExpiresBiomeOverlayAndRestoringRoomRecoversIt(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            BlockPos center = prepareDesertRoom(helper);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Initial greenhouse overlay was missing");
            BlockPos roof = center.above(5);
            level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            // No manual cache clear: prove that the normal expiry notices the hole.
            helper.runAfterDelay(GreenhouseSettings.CACHE_TICKS.get() + 2L, () -> {
                try {
                    helper.assertTrue(level.getBiome(center).is(Biomes.PLAINS), "Broken roof kept an emulated biome after cache expiry");
                    helper.assertFalse(hasHuskSpawn(level, center), "Broken roof kept desert natural-spawn entries");
                    level.setBlock(roof, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
                    GreenhouseEnvironment.clearCache();
                    helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Repaired greenhouse failed to restore its biome");

                    GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
                    helper.assertTrue(level.getBiome(center).is(Biomes.PLAINS), "Disabled overlay did not revert immediately");
                    GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
                    helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Re-enabling lost an otherwise valid room");
                    helper.succeed();
                } finally {
                    settings.restore();
                }
            });
        } catch (RuntimeException | Error exception) {
            settings.restore();
            throw exception;
        }
    }

    private static boolean hasHuskSpawn(ServerLevel level, BlockPos pos) {
        var generator = level.getChunkSource().getGenerator();
        var spawnTable = generator.getMobsAt(level.getBiome(pos), level.structureManager(), MobCategory.MONSTER, pos);
        // This is the same public table lookup plus NeoForge hook used by
        // NaturalSpawner.mobsAt after its exact-position level.getBiome query.
        return EventHooks.getPotentialSpawns(level, MobCategory.MONSTER, pos, spawnTable).unwrap().stream()
                .anyMatch(spawn -> spawn.type == EntityType.HUSK);
    }

    private static BlockPos prepareDesertRoom(GameTestHelper helper) {
        GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
        GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.set(true);
        GreenhouseSettings.RADIUS.set(4);
        GreenhouseSettings.CACHE_TICKS.set(20);
        GreenhouseSettings.BIOME_MATERIAL_RULES.set(List.of(
                "minecraft:desert=minecraft:diamond_block*2,minecraft:emerald_block*1,minecraft:lapis_block*1"));
        Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
        GreenhouseRecipes.clearCache();
        GreenhouseEnvironment.clearCache();
        helper.setBiome(Biomes.PLAINS);
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(CENTER);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -1; y <= 5; y++) {
            BlockState state = y == -1 ? Blocks.BRICKS.defaultBlockState()
                    : y == 5 || Math.abs(x) == 5 || Math.abs(z) == 5 ? Blocks.GLASS.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            level.setBlock(center.offset(x, y, z), state, Block.UPDATE_ALL);
        }
        level.setBlock(center.offset(-2, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(-1, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(1, -1, 0), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(2, -1, 0), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        CropClimate base = CropClimate.at(GreenhouseEnvironment.nativeBiome(level, center), center);
        var desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
        int delta = CropClimate.at(desert, center).ordinal() - base.ordinal();
        Block heat = switch (delta) {
            case 1 -> Blocks.CAMPFIRE;
            case 2 -> Blocks.MAGMA_BLOCK;
            case 3, 4 -> Blocks.LAVA;
            default -> Blocks.AIR;
        };
        Block cooling = switch (delta) {
            case -1, 3 -> Blocks.SNOW_BLOCK;
            case -2 -> Blocks.ICE;
            case -3 -> Blocks.PACKED_ICE;
            case -4 -> Blocks.BLUE_ICE;
            default -> Blocks.AIR;
        };
        level.setBlock(center.north(2), heat.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.south(2), cooling.defaultBlockState(), Block.UPDATE_ALL);
        GreenhouseEnvironment.clearCache();
        return center;
    }

    private static final class Settings {
        private final boolean greenhouses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        private final boolean ambient = GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.get();
        private final boolean thermostats = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        private final int radius = GreenhouseSettings.RADIUS.get();
        private final int cacheTicks = GreenhouseSettings.CACHE_TICKS.get();
        private final List<? extends String> rules = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());

        private void restore() {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(greenhouses);
            GreenhouseSettings.ENABLE_AMBIENT_TEMPERATURE.set(ambient);
            GreenhouseSettings.RADIUS.set(radius);
            GreenhouseSettings.CACHE_TICKS.set(cacheTicks);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(rules);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermostats);
            GreenhouseRecipes.clearCache();
            GreenhouseEnvironment.clearCache();
        }
    }
}
