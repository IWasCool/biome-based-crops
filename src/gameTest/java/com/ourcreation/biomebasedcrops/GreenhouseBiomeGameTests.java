package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class GreenhouseBiomeGameTests {
    private static final BlockPos CENTER = new BlockPos(8, 2, 8);

    private GreenhouseBiomeGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_biome_overlay")
    public static void matchingNativeClimateRegistersOnlyWithAnInwardControllerAndRemovalRevokesAllBiomeQueries(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            BlockPos center = prepareDesertRoom(helper);
            ServerLevel level = helper.getLevel();
            level.removeBlock(center.north(2), false);
            BlockPos marker = center.west(5).above(2);
            level.setBlock(marker, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.setBiome(Biomes.NETHER_WASTES);
            GreenhouseEnvironment.clearCache();
            var desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
            CropClimate required = CropClimate.at(desert, center);
            helper.assertTrue(CropClimate.at(GreenhouseEnvironment.nativeBiome(level, center), center) == required,
                    "Native fixture must match the target biome's climate without conditioning");
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "A matching house registered without an AC or TAN controller");
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null && level.getBiome(center).is(Biomes.NETHER_WASTES),
                    "An ordinary house changed its F3 biome");
            helper.assertFalse(hasHuskSpawn(level, center), "An ordinary house acquired the foreign biome's spawn table");
            assertNoRegisteredClientBiome(helper, level, center);

            level.setBlock(marker, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                    .setValue(AirConditionerBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
            helper.assertFalse(GreenhouseEnvironment.inspect(level, center).valid(), "An outward-facing controller registered its rear room");
            level.setBlock(marker, level.getBlockState(marker).setValue(AirConditionerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(marker);
            unit.setClimate(required);
            unit.setEnabled(false);
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "An idle installed controller did not mark intentional biome registration");
            helper.assertValueEqual(unit.energyStored(), 0, "Presence-only marker unexpectedly received energy");
            helper.assertTrue(level.getBiome(center).is(Biomes.DESERT) && hasHuskSpawn(level, center),
                    "A registered habitat did not supply its exact biome and spawn table");
            assertClientBiomeProvider(helper, level, center);

            level.setBlock(marker, level.getBlockState(marker).setValue(AirConditionerBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null && level.getBiome(center).is(Biomes.NETHER_WASTES),
                    "Rotating the only controller away retained a cached biome");
            helper.assertFalse(hasHuskSpawn(level, center), "Rotating away retained the foreign spawn table");
            level.setBlock(marker, level.getBlockState(marker).setValue(AirConditionerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Restoring the inward marker did not recover registration");
            level.setBlock(marker, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.regionAt(level, center) == null && level.getBiome(center).is(Biomes.NETHER_WASTES),
                    "Removing the only controller retained a cached biome");
            helper.assertFalse(hasHuskSpawn(level, center), "Removing the controller retained the foreign spawn table");
            assertNoRegisteredClientBiome(helper, level, center);
            helper.succeed();
        } finally {
            settings.restore();
        }
    }

    private static void assertNoRegisteredClientBiome(GameTestHelper helper, ServerLevel level, BlockPos inside) {
        var atmosphere = GreenhouseEnvironment.atmosphereRegionAt(level, inside);
        helper.assertTrue(atmosphere == null || atmosphere.biome() == null,
                "An unregistered room still advertised a biome in its client atmosphere snapshot");
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
                // DebugScreenOverlay uses this exact Level.getBiome(player.blockPosition()) query for F3.
                helper.assertTrue(level.getBiomeManager().getBiome(interior).is(Biomes.DESERT), "BiomeManager bypassed the greenhouse at " + interior);
                helper.assertTrue(level.getBiomeManager().getNoiseBiomeAtPosition(interior).is(Biomes.DESERT),
                        "Exact-position noise query bypassed the greenhouse at " + interior);
                helper.assertTrue(level.getBiomeManager().getNoiseBiomeAtPosition(interior.getX() + 0.5,
                        interior.getY() + 0.5, interior.getZ() + 0.5).is(Biomes.DESERT),
                        "Double-coordinate biome query bypassed the greenhouse at " + interior);
            }
            Component[] reading = new Component[1];
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "greenhouse-biome-readout")) {
                @Override public void displayClientMessage(Component message, boolean overlay) { reading[0] = message; }
            };
            player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            ThermometerItem.showReading(player);
            helper.assertTrue(reading[0] != null && reading[0].getContents() instanceof TranslatableContents,
                    "Thermometer did not publish its local readout");
            var args = ((TranslatableContents) reading[0].getContents()).getArgs();
            helper.assertTrue(args.length == 2 && BiomeSelector.parse("minecraft:desert").orElseThrow().component().equals(args[1]),
                    "Thermometer biome did not match the biome returned to F3");
            assertClientBiomeProvider(helper, level, center);
            for (BlockPos outside : List.of(center.east(5), center.east(6), center.above(5), center.below())) {
                helper.assertTrue(level.getBiome(outside).equals(GreenhouseEnvironment.nativeBiome(level, outside)),
                        "A solid shell or exterior position borrowed the greenhouse biome: " + outside);
            }
            // Repeated emulated queries must not turn the emulated biome into the next
            // native baseline, or apply the heaters to the already heated desert again.
            for (int query = 0; query < 20; query++) {
                GreenhouseEnvironment.invalidateClimate();
                helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Overlay became unstable after a climate refresh");
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
    public static void opaqueRoofRegistersTheWholeBiomeButOnlyGlassColumnsGrowPlants(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            BlockPos center = prepareDesertRoom(helper);
            ServerLevel level = helper.getLevel();
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                level.setBlock(center.offset(x, 5, z), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            }
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).valid(), "An opaque roof removed the valid desert habitat");
            for (BlockPos sample : List.of(center, center.west(4), center.east(4), center.above(4))) {
                helper.assertTrue(level.getBiome(sample).is(Biomes.DESERT), "F3 lookup lost the opaque-roof biome at " + sample);
                helper.assertTrue(level.getBiomeManager().getBiome(sample).is(Biomes.DESERT),
                        "BiomeManager lost the opaque-roof biome at " + sample);
            }
            helper.assertTrue(hasHuskSpawn(level, center), "An opaque roof removed the biome's vanilla natural-spawn table");
            assertClientBiomeProvider(helper, level, center);
            CropProfile desert = new CropProfile(Set.of(CropClimate.HOT),
                    List.of(BiomeSelector.parse("minecraft:desert").orElseThrow()));
            helper.assertFalse(GreenhouseEnvironment.hasGrowingLight(level, center), "An opaque roof admitted plant skylight");
            helper.assertFalse(GreenhouseEnvironment.canSupport(level, center, desert), "An unlit foreign-biome plant received support");
            helper.assertFalse(CommonEvents.canGrow(level, center, desert), "A foreign-biome plant grew under an opaque roof");
            level.setBlock(center.above(5), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(CommonEvents.canGrow(level, center, desert), "One sky-exposed glass column did not light its plant");
            helper.assertFalse(CommonEvents.canGrow(level, center.south(), desert), "Plant skylight spread to a neighboring opaque column");
            helper.assertTrue(level.getBiome(center.south()).is(Biomes.DESERT), "An unlit plant column lost its registered biome");
            assertClientBiomeProvider(helper, level, center);
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

    @GameTest(template = "empty", skyAccess = true, batch = "greenhouse_biome_leak_grace", timeoutTicks = 100)
    public static void openingRoofKeepsBiomeDuringGraceAndRestoringRoomSealsIt(GameTestHelper helper) {
        Settings settings = new Settings();
        try {
            BlockPos center = prepareDesertRoom(helper);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Initial greenhouse overlay was missing");
            BlockPos roof = center.above(5);
            level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "A brief roof opening lost the retained room biome");
            helper.assertTrue(GreenhouseEnvironment.inspect(level, center).status().endsWith("leaking"),
                    "A roof opening did not report its leak");
            // No manual topology clear: the normal block-change hook must notice the hole.
            helper.runAfterDelay(GreenhouseSettings.CACHE_TICKS.get() + 2L, () -> {
                try {
                    helper.assertTrue(level.getBiome(center).is(Biomes.DESERT), "Cache refresh discarded the room's leak grace");
                    helper.assertTrue(hasHuskSpawn(level, center), "Leak grace lost the retained biome's vanilla spawn table");
                    level.setBlock(roof, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
                    AutoGreenhouseGameTests.conditionFixtureAir(level, center.north(2), CropClimate.HOT);
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
        try {
            // Invoke vanilla's actual spawn-table path, including structure overrides and the NeoForge event.
            var lookup = NaturalSpawner.class.getDeclaredMethod("mobsAt", ServerLevel.class, StructureManager.class,
                    ChunkGenerator.class, MobCategory.class, BlockPos.class, Holder.class);
            lookup.setAccessible(true);
            @SuppressWarnings("unchecked")
            var table = (net.minecraft.util.random.WeightedRandomList<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData>)
                    lookup.invoke(null, level, level.structureManager(), level.getChunkSource().getGenerator(), MobCategory.MONSTER, pos, null);
            var roughLookup = NaturalSpawner.class.getDeclaredMethod("getRoughBiome", BlockPos.class, ChunkAccess.class);
            roughLookup.setAccessible(true);
            Biome spawnCostBiome = (Biome) roughLookup.invoke(null, pos, level.getChunkAt(pos));
            if (!spawnCostBiome.equals(level.getBiome(pos).value()))
                throw new AssertionError("Natural spawn cost biome disagrees with the selected spawn table");
            return table.unwrap().stream().anyMatch(spawn -> spawn.type == EntityType.HUSK);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not exercise the vanilla natural-spawn biome lookup", exception);
        }
    }

    private static void assertClientBiomeProvider(GameTestHelper helper, ServerLevel level, BlockPos inside) {
        Holder<Biome> nativeBiome = GreenhouseEnvironment.nativeBiome(level, inside);
        var dimension = level.dimension().location();
        var region = GreenhouseEnvironment.atmosphereRegionAt(level, inside);
        helper.assertTrue(region != null && Biomes.DESERT.location().equals(region.biome()),
                "The server atmosphere snapshot did not include the registered biome");
        var sections = GreenhouseBiomeNetwork.masksFor(region.interior(), inside, region.coverage());
        try {
            GreenhouseClientBiomes.accept(new GreenhouseBiomeNetwork.RoomPayload(dimension, region.biome(), sections,
                    region.climate(), region.oxygenPercent()));
            for (BlockPos sample : List.of(inside, inside.west(4), inside.east(4), inside.above(4))) {
                helper.assertTrue(GreenhouseClientBiomes.biomeAt(level, sample, nativeBiome).is(Biomes.DESERT),
                        "Client room snapshot did not cover the actual registered interior at " + sample);
            }
            helper.assertTrue(GreenhouseClientBiomes.biomeAt(level, inside.east(5), nativeBiome).equals(nativeBiome),
                    "Client room snapshot leaked into the room's solid shell");
            GreenhouseClientBiomes.accept(new GreenhouseBiomeNetwork.RoomPayload(ResourceLocation.parse("minecraft:the_nether"),
                    Biomes.DESERT.location(), sections, CropClimate.HOT, 100));
            helper.assertTrue(GreenhouseClientBiomes.biomeAt(level, inside, nativeBiome).equals(nativeBiome),
                    "Client room snapshot crossed dimensions");
            GreenhouseClientBiomes.clear();
            helper.assertTrue(GreenhouseClientBiomes.biomeAt(level, inside, nativeBiome).equals(nativeBiome),
                    "Clearing the client snapshot retained its biome");
        } finally {
            GreenhouseClientBiomes.clear();
        }
    }

    private static BlockPos prepareDesertRoom(GameTestHelper helper) {
        GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
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
        level.setBlock(center.offset(-2, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(1, 0, 0), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(2, 0, 0), Blocks.LAPIS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        var desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
        level.setBlock(center.north(2), GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        AirConditionerBlockEntity machine = (AirConditionerBlockEntity) level.getBlockEntity(center.north(2));
        machine.setClimate(CropClimate.at(desert, center));
        BlockPos second = center.west(5).above(2);
        level.setBlock(second, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState()
                .setValue(AirConditionerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        ((AirConditionerBlockEntity) level.getBlockEntity(second)).setClimate(CropClimate.HOT);
        while (machine.energyStored() < AirConditionerBlockEntity.CAPACITY) {
            if (machine.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) <= 0) {
                throw new IllegalStateException("Fixture could not charge the real FE buffer");
            }
        }
        AirConditionerBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        AutoGreenhouseGameTests.conditionFixtureAir(level, machine.getBlockPos(), CropClimate.HOT);
        return center;
    }

    private static final class Settings {
        private final boolean greenhouses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        private final boolean thermostats = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        private final int cacheTicks = GreenhouseSettings.CACHE_TICKS.get();
        private final List<? extends String> rules = List.copyOf(GreenhouseSettings.BIOME_MATERIAL_RULES.get());

        private void restore() {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(greenhouses);
            GreenhouseSettings.CACHE_TICKS.set(cacheTicks);
            GreenhouseSettings.BIOME_MATERIAL_RULES.set(rules);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(thermostats);
            GreenhouseRecipes.clearCache();
            GreenhouseEnvironment.clearCache();
        }
    }
}
