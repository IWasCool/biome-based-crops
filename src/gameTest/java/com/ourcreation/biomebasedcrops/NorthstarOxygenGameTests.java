package com.ourcreation.biomebasedcrops;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class NorthstarOxygenGameTests {
    private NorthstarOxygenGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "northstar_room_oxygen", timeoutTicks = 300)
    public static void realMoonAndMarsOxygenQueriesUseOnlyFilledIntactRoomInteriors(GameTestHelper helper) {
        if (!ModList.get().isLoaded("northstar")) {
            helper.succeed();
            return;
        }
        boolean oldEnabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        List<Fixture> fixtures = new ArrayList<>();
        Runnable cleanup = () -> {
            for (Fixture fixture : fixtures) fixture.restore();
            GreenhouseSettings.ENABLE_GREENHOUSES.set(oldEnabled);
            GreenhouseEnvironment.clearCache();
        };
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            Access access = Access.create();
            exercisePlanet(helper, access, "moon", -183F, fixtures, () ->
                    exercisePlanet(helper, access, "mars", -100F, fixtures, () -> {
                        cleanup.run();
                        helper.succeed();
                    }, cleanup), cleanup);
        } catch (RuntimeException | Error error) {
            cleanup.run();
            throw error;
        }
    }

    private static void exercisePlanet(GameTestHelper helper, Access access, String planet, float nativeCelsius,
                                       List<Fixture> fixtures, Runnable done, Runnable cleanup) {
        Fixture fixture = new Fixture(helper, planet);
        fixtures.add(fixture);
        ServerLevel level = fixture.level;
        BlockPos inside = fixture.center.east();
        BlockPos outside = fixture.center.east(3);
        Object manager = access.dimension(level);
        Object temperatureManager = access.temperatureDimension(level);
        CropProfile anyClimate = new CropProfile(EnumSet.allOf(CropClimate.class));
        helper.assertFalse(access.nativeOxygen(manager), planet + " unexpectedly has a naturally breathable atmosphere");
        helper.assertFalse(NorthstarAtmosphere.naturalAtmosphereBreathable(level), "Adapter invented native planet oxygen");
        helper.assertTrue(NorthstarAtmosphere.naturalTemperature(level, inside).orElseThrow() == nativeCelsius,
                "Adapter did not read the real " + planet + " native temperature");
        helper.assertTrue(GreenhouseEnvironment.inspect(level, inside).enclosed(), "Small planetary fixture did not seal");
        helper.assertTrue(GreenhouseEnvironment.oxygenPercent(level, inside) < 1F, "Unpowered room started with free oxygen");
        helper.assertFalse(access.oxygenAt(manager, inside), "Unfilled room was breathable to Northstar");
        helper.assertTrue(access.sealer(manager, inside) == null, "Unfilled room returned a breathing provider");
        helper.assertFalse(CommonEvents.canGrow(level, outside, anyClimate), "A climate-compatible crop grew in the airless planet exterior");
        fixture.unit.setClimate(CropClimate.NEUTRAL);
        advancePaidTicks(helper, fixture, 10, () -> {
            float partial = GreenhouseEnvironment.oxygenPercent(level, inside);
            helper.assertTrue(partial > 0F && partial < RoomClimateState.BREATHABLE_PERCENT,
                    "Oxygen did not fill gradually on " + planet + ": " + partial);
            helper.assertFalse(access.oxygenAt(manager, inside), "Partial atmosphere enabled breathing too early");
            advancePaidTicks(helper, fixture, 45, () -> {
                float filled = GreenhouseEnvironment.oxygenPercent(level, inside);
                helper.assertTrue(filled >= RoomClimateState.BREATHABLE_PERCENT, "Paid room did not become breathable: " + filled);
                helper.assertTrue(GreenhouseEnvironment.currentClimate(level, inside) == CropClimate.NEUTRAL,
                        "Paid thermoregulator never warmed the planetary room");
                Object provider = access.sealer(manager, inside);
                helper.assertTrue(provider != null, "Northstar breathing lookup did not see the filled room");
                helper.assertTrue(access.sealed(provider, inside), "Returned provider did not seal its exact query");
                helper.assertTrue(access.oxygenAt(manager, inside), "Northstar block oxygen query rejected the filled room");
                helper.assertTrue(access.sealerAtEye(manager, Vec3.atCenterOf(inside)) != null,
                        "Northstar entity eye-position lookup rejected the filled room");
                float actual = LegacyClimate.toCelsius(GreenhouseEnvironment.currentClimate(level, inside));
                helper.assertTrue(Math.abs(access.temperatureAt(temperatureManager, inside) - actual) < 0.001F,
                        "Northstar block API did not map the room climate category");
                helper.assertTrue(Math.abs(access.temperatureAtEye(temperatureManager, Vec3.atCenterOf(inside)) - actual) < 0.001F,
                        "Northstar entity API did not map the room climate category");
                helper.assertTrue(access.temperatureAt(temperatureManager, outside) == nativeCelsius,
                        "Controlled temperature leaked into the native planet exterior");
                helper.assertTrue(GreenhouseEnvironment.withoutOverlay(() -> access.temperatureAt(temperatureManager, inside)) == nativeCelsius,
                        "Native temperature sampling fed controlled room air back into the planet baseline");
                helper.assertTrue(CommonEvents.canGrow(level, inside, anyClimate), "A climate-compatible crop could not grow in the oxygenated room");
                helper.assertFalse(NorthstarAtmosphere.nativeProviderBreathable(level, inside),
                        "Native sealer lookup fed the greenhouse provider back into its oxygen baseline");
                helper.assertFalse(access.nativeOxygen(manager), "A room changed the entire planet's oxygen flag");
                helper.assertFalse(access.oxygenAt(manager, outside), "Room oxygen escaped into the planet exterior");
                helper.assertFalse(access.oxygenAt(manager, fixture.center.above(3)), "Solid roof borrowed interior oxygen");
                helper.assertFalse(access.sealed(provider, outside), "Room provider sealed an exterior position");
                int volume = GreenhouseEnvironment.inspect(level, inside).roomVolume();
                helper.assertTrue(volume > 0, "Physical room did not report its air volume");
                access.drain(provider, volume * 1000F * 0.10F);
                float consumed = GreenhouseEnvironment.oxygenPercent(level, inside);
                helper.assertTrue(consumed <= filled - 9.9F, "Northstar breathing drain did not consume the shared air reservoir");
                helper.assertFalse(access.oxygenAt(manager, inside), "Breathing drain below 95% left oxygen active");
                helper.assertFalse(access.sealed(provider, inside), "Previously returned provider ignored oxygen depletion");
                advancePaidTicks(helper, fixture, 8, () -> {
                    helper.assertTrue(access.oxygenAt(manager, inside), "Powered room failed to replenish consumed air");
                    level.setBlock(fixture.center.above(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    helper.assertFalse(access.oxygenAt(manager, inside), "Opening the roof retained Northstar room oxygen");
                    helper.assertTrue(access.sealer(manager, inside) == null, "Broken room retained its provider");
                    helper.assertFalse(access.sealed(provider, inside), "A cached provider survived the roof leak");
                    helper.assertFalse(access.nativeOxygen(manager), "Roof change changed native planet atmosphere");
                    helper.assertTrue(fixture.unit.hasPaidAirflow(), "Opening the roof unexpectedly stopped the paid local controller");
                    helper.assertTrue(access.temperatureAt(temperatureManager, inside) == LegacyClimate.toCelsius(CropClimate.NEUTRAL),
                            "Opening a known room discarded its climate before the leak grace expired");
                    BlockPos wind = fixture.unit.getBlockPos().relative(fixture.unit.getBlockState().getValue(AirConditionerBlock.FACING));
                    helper.assertTrue(access.temperatureAt(temperatureManager, wind) == LegacyClimate.toCelsius(CropClimate.NEUTRAL),
                            "Paid outlet wind did not affect the position directly in front");
                    helper.assertFalse(CommonEvents.canGrow(level, inside, anyClimate), "A crop kept growing after its oxygenated room opened");
                    level.setBlock(fixture.unit.getBlockPos().below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    helper.runAfterDelay(2L, () -> {
                        try {
                            AirConditionerBlockEntity.serverTick(level, fixture.unit.getBlockPos(), fixture.unit.getBlockState(), fixture.unit);
                            helper.assertFalse(fixture.unit.hasPaidAirflow(), "Redstone-disabled local controller remained active");
                            helper.assertTrue(access.temperatureAt(temperatureManager, inside) == LegacyClimate.toCelsius(CropClimate.NEUTRAL),
                                    "Stopping the controller discarded the leaking room's captured climate");
                            helper.assertFalse(access.oxygenAt(manager, inside), "Disabled controller restored oxygen to the open room");
                            fixture.restore();
                            fixtures.remove(fixture);
                            done.run();
                        } catch (RuntimeException | Error error) {
                            cleanup.run();
                            throw error;
                        }
                    });
                }, cleanup);
            }, cleanup);
        }, cleanup);
    }

    private static void advancePaidTicks(GameTestHelper helper, Fixture fixture, int remaining,
                                          Runnable done, Runnable cleanup) {
        helper.runAfterDelay(1L, () -> {
            try {
                int cost = fixture.unit.currentEnergyCost();
                long needed = Math.min(AirConditionerBlockEntity.CAPACITY, Math.max(1L, cost) * 3L);
                while (fixture.unit.energyStored() < needed) {
                    helper.assertTrue(fixture.unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0,
                            "Fixture could not deliver the required real FE input");
                }
                AirConditionerBlockEntity.serverTick(fixture.level, fixture.unit.getBlockPos(), fixture.unit.getBlockState(), fixture.unit);
                GreenhouseEnvironment.oxygenPercent(fixture.level, fixture.center.east());
                if (remaining > 1) advancePaidTicks(helper, fixture, remaining - 1, done, cleanup);
                else done.run();
            } catch (RuntimeException | Error error) {
                cleanup.run();
                throw error;
            }
        });
    }

    private static final class Fixture {
        private final ServerLevel level;
        private final PlanetLevel planetLevel;
        private final BlockPos center = new BlockPos(12000, 288, 12000);
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<ChunkPos> tickets = new ArrayList<>();
        private final AirConditionerBlockEntity unit;
        private boolean restored;

        private Fixture(GameTestHelper helper, String planet) {
            planetLevel = PlanetLevel.open(helper.getLevel().getServer(), planet);
            level = planetLevel.level;
            try {
                for (int cx = (center.getX() - 2) >> 4; cx <= (center.getX() + 2) >> 4; cx++) {
                    for (int cz = (center.getZ() - 2) >> 4; cz <= (center.getZ() + 2) >> 4; cz++) {
                        if (!level.getForcedChunks().contains(ChunkPos.asLong(cx, cz))) {
                            level.setChunkForced(cx, cz, true);
                            tickets.add(new ChunkPos(cx, cz));
                        }
                        level.getChunk(cx, cz);
                    }
                }
                int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 3; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    original.put(pos, level.getBlockState(pos));
                    Block block = y == -1 ? Blocks.OBSIDIAN
                            : y == 3 || Math.abs(x) == 2 || Math.abs(z) == 2 ? Blocks.GLASS : Blocks.AIR;
                    level.setBlock(pos, block.defaultBlockState(), flags);
                }
                BlockPos machine = center.west();
                level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), flags);
                unit = (AirConditionerBlockEntity) level.getBlockEntity(machine);
                if (unit == null) throw new IllegalStateException("Thermoregulator fixture has no block entity");
                GreenhouseEnvironment.clearCache();
            } catch (RuntimeException | Error error) {
                restore();
                throw error;
            }
        }

        private void restore() {
            if (restored) return;
            restored = true;
            original.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            for (ChunkPos ticket : tickets) level.setChunkForced(ticket.x, ticket.z, false);
            GreenhouseEnvironment.clearCache();
            planetLevel.close();
        }
    }

    /** GameTestServer deliberately omits mod stems; decode and host the real datapack stem for this fixture only. */
    private record PlanetLevel(ServerLevel level, LevelStorageSource.LevelStorageAccess temporaryStorage) {
        private static PlanetLevel open(MinecraftServer server, String planet) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath("northstar", planet));
            ServerLevel existing = server.getLevel(key);
            if (existing != null) return new PlanetLevel(existing, null);
            if (!(server instanceof GameTestServer)) throw new IllegalStateException("Installed Northstar is missing dimension " + planet);
            LevelStorageSource.LevelStorageAccess storage = null;
            ServerLevel created = null;
            try {
                var resource = server.getResourceManager().getResourceOrThrow(
                        ResourceLocation.fromNamespaceAndPath("northstar", "dimension/" + planet + ".json"));
                LevelStem stem;
                try (var reader = resource.openAsReader()) {
                    stem = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()),
                            JsonParser.parseReader(reader)).getOrThrow();
                }
                storage = LevelStorageSource.createDefault(server.getWorldPath(LevelResource.ROOT).resolve("northstar-oxygen-tests"))
                        .createAccess(planet);
                var data = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
                long seed = BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed());
                // DerivedLevelData reads the real test-server clock. Each callback pays the real BE once on that tick.
                created = new ServerLevel(server, Util.backgroundExecutor(), storage, data, key, stem,
                        NO_PROGRESS, false, seed, List.of(), false, server.overworld().getRandomSequences());
                NeoForge.EVENT_BUS.post(new LevelEvent.Load(created));
                return new PlanetLevel(created, storage);
            } catch (IOException | RuntimeException error) {
                if (created != null) {
                    try { new PlanetLevel(created, storage).close(); }
                    catch (RuntimeException closeError) { error.addSuppressed(closeError); }
                } else if (storage != null) {
                    try { storage.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
                }
                throw new IllegalStateException("Could not load real Northstar " + planet + " datapack stem for GameTestServer", error);
            }
        }

        private void close() {
            if (temporaryStorage == null) return;
            RuntimeException failure = null;
            try { NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level)); }
            catch (RuntimeException error) { failure = error; }
            try {
                level.close();
            } catch (IOException error) {
                if (failure == null) failure = new IllegalStateException("Could not close the temporary planetary test level", error);
                else failure.addSuppressed(error);
            } finally {
                try { temporaryStorage.close(); }
                catch (IOException error) {
                    if (failure == null) failure = new IllegalStateException("Could not close planetary fixture storage", error);
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }

        private static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
            @Override public void updateSpawnPos(ChunkPos center) {}
            @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) {}
            @Override public void start() {}
            @Override public void stop() {}
        };
    }

    private record Access(Method dimension, Method nativeOxygen, Method oxygenAt, Method sealer, Method sealerAtEye,
                          Method sealed, Method drain, Method temperatureDimension, Method temperatureAt, Method temperatureAtEye) {
        private static Access create() {
            try {
                Class<?> manager = Class.forName("com.lightning.northstar.world.oxygen.NorthstarOxygen");
                Class<?> provider = Class.forName("com.lightning.northstar.world.oxygen.NorthstarOxygen$Provider");
                Class<?> temperature = Class.forName("com.lightning.northstar.world.temperature.NorthstarTemperature");
                return new Access(manager.getMethod("getDimension", Level.class), manager.getMethod("hasOxygen"),
                        manager.getMethod("hasOxygen", Vec3i.class), manager.getMethod("getSealer", Vec3i.class),
                        manager.getMethod("getSealer", Vec3.class), provider.getMethod("isSealed", Vec3i.class),
                        provider.getMethod("drainOxygen", float.class), temperature.getMethod("getDimension", Level.class),
                        temperature.getMethod("getTemperature", Vec3i.class), temperature.getMethod("getTemperature", Vec3.class));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Installed Northstar lacks its verified oxygen contract", error);
            }
        }

        private Object dimension(Level level) { return invoke(dimension, null, level); }
        private boolean nativeOxygen(Object manager) { return (Boolean) invoke(nativeOxygen, manager); }
        private boolean oxygenAt(Object manager, BlockPos pos) { return (Boolean) invoke(oxygenAt, manager, pos); }
        private Object sealer(Object manager, BlockPos pos) { return invoke(sealer, manager, pos); }
        private Object sealerAtEye(Object manager, Vec3 pos) { return invoke(sealerAtEye, manager, pos); }
        private boolean sealed(Object provider, BlockPos pos) { return (Boolean) invoke(sealed, provider, pos); }
        private void drain(Object provider, float amount) { invoke(drain, provider, amount); }
        private Object temperatureDimension(Level level) { return invoke(temperatureDimension, null, level); }
        private float temperatureAt(Object manager, BlockPos pos) { return (Float) invoke(temperatureAt, manager, pos); }
        private float temperatureAtEye(Object manager, Vec3 pos) { return (Float) invoke(temperatureAtEye, manager, pos); }

        private static Object invoke(Method method, Object receiver, Object... arguments) {
            try {
                return method.invoke(receiver, arguments);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Real Northstar oxygen query failed: " + method.getName(), error);
            }
        }
    }
}
