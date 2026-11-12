package com.ourcreation.biomebasedcrops;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.util.Unit;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Opt-in server work benchmark. It never runs a stress workload in ordinary CI or ships in the mod JAR. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class CropPerformanceGameTests {
    private static volatile long consumedResults;
    private CropPerformanceGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "opt_in_performance", timeoutTicks = 10000)
    public static void controlledCropGuardLoad(GameTestHelper helper) throws Throwable {
        if (!Boolean.getBoolean("biomeBasedCrops.performance")) { helper.succeed(); return; }
        var run = new StressRun(helper);
        try { run.start(); }
        catch (Throwable failure) { run.close(); throw failure; }
    }

    private static final class StressRun {
        private static final int CHECKS = 1024, INITIAL_WARMUP = 120, WINDOW_WARMUP = 40, SAMPLES = 120;
        private static final boolean[] ORDER = {false, true, true, false, false, true, true, false};
        private static final BlockState WHEAT = Blocks.WHEAT.defaultBlockState();
        private static final ResourceLocation TAN = ResourceLocation.parse("toughasnails:thermoregulator");
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean shipMode = Boolean.getBoolean("biomeBasedCrops.performanceShip");
        private final List<String> scenarios = new ArrayList<>(List.of("natural_match", "wrong_climate_uncovered", "electrical_front"));
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final Consumer<ServerTickEvent.Pre> pre = this::beforeTick;
        private final Consumer<ServerTickEvent.Post> post = this::afterTick;
        private final boolean savedNatural = Config.BLOCK_NATURAL_GROWTH.get();
        private final boolean savedCrops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        private final boolean savedCompat = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        private final boolean savedHouses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        private final List<? extends String> savedRules = List.copyOf(Config.CROP_CLIMATE_RULES.get());
        private final List<? extends String> savedBiomes = List.copyOf(Config.CROP_BIOME_RULES.get());
        private final List<BlockPos> crops = new ArrayList<>(), machines = new ArrayList<>();
        private int scene = -1, windowIndex, warmup, sampleCount;
        private long tickStarted, guardElapsed, accepted;
        private boolean pendingSample, ended;
        private Window window;
        private Object ship, container;
        private Object physics;
        private Boolean savedPhysicsPaused;
        private BlockPos localAnchor, worldAnchor;

        StressRun(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            if (ModList.get().isLoaded("toughasnails")) scenarios.add("tan_front");
        }

        void start() throws Throwable {
            if (shipMode && !ModList.get().isLoaded("sable")) throw new IllegalStateException("Ship performance run requires Sable");
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Config.CROP_BIOME_RULES.set(List.of());
            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            helper.setDayTime(1000);
            helper.setBiome(Biomes.PLAINS);
            NeoForge.EVENT_BUS.addListener(pre);
            NeoForge.EVENT_BUS.addListener(post);
            nextScene();
            helper.onEachTick(this::tick);
        }

        private void beforeTick(ServerTickEvent.Pre event) {
            if (event.getServer() == level.getServer()) tickStarted = System.nanoTime();
        }

        private void tick() {
            if (ended) return;
            try {
                if (windowIndex == ORDER.length && window == null) {
                    nextScene();
                    if (ended) return;
                }
                maintainControllers();
                if (warmup-- > 0) { consumedResults += query(); return; }
                if (window == null) {
                    Config.BLOCK_NATURAL_GROWTH.set(ORDER[windowIndex]);
                    window = new Window();
                    sampleCount = 0;
                    warmup = WINDOW_WARMUP;
                    if (shipMode) moveShip(windowIndex);
                    chargeControllers();
                    return;
                }
                if (!window.begun) {
                    window.begin(activeControllers(), nativeBiome());
                    helper.assertValueEqual(activeControllers(), machines.size(), "Active controller count at window start");
                    helper.assertValueEqual(query(), expectedAccepted(ORDER[windowIndex]), "Acceptance at window start");
                    return;
                }
                long start = System.nanoTime();
                accepted = query();
                guardElapsed = System.nanoTime() - start;
                long expected = expectedAccepted(ORDER[windowIndex]);
                helper.assertValueEqual(accepted, expected, "Benchmark guard acceptance changed for " + scenarios.get(scene));
                consumedResults += accepted;
                pendingSample = true;
            } catch (Throwable failure) {
                BiomeBasedCrops.LOGGER.error("Performance fixture failed in scene {} window {}", scene, windowIndex, failure);
                close();
                helper.fail("Performance fixture failed: " + failure);
            }
        }

        private long query() {
            long count = 0;
            for (int i = 0; i < CHECKS; i++) if (CommonEvents.canGrowNaturally(level, crops.get(i), WHEAT)) count++;
            return count;
        }

        private long expectedAccepted(boolean enabled) {
            return enabled && scenarios.get(scene).equals("wrong_climate_uncovered") ? 0 : CHECKS;
        }

        private void afterTick(ServerTickEvent.Post event) {
            try {
              if (!ended && event.getServer() == level.getServer() && pendingSample) {
                pendingSample = false;
                window.guard.add(guardElapsed);
                long elapsed = System.nanoTime() - tickStarted;
                helper.assertTrue(elapsed >= guardElapsed, "Server tick event span did not include the measured guard work");
                window.ticks.add(elapsed);
                window.accepted += accepted;
                if (++sampleCount == SAMPLES) {
                    helper.assertValueEqual(activeControllers(), machines.size(), "Active controller count at window end");
                    helper.assertTrue(window.biomeStart.equals(nativeBiome()), "Biome drifted during a measured window");
                    window.activeEnd = activeControllers();
                    window.biomeEnd = nativeBiome();
                    Map<String, Object> row = window.result(scenarios.get(scene), shipMode, windowIndex, ORDER[windowIndex]);
                    rows.add(row);
                    var compact = new LinkedHashMap<>(row);
                    compact.remove("guardSamplesNs"); compact.remove("serverTickPrePostSamplesNs");
                    BiomeBasedCrops.LOGGER.info("[PERF] {}", new GsonBuilder().create().toJson(compact));
                    window = null;
                    windowIndex++;
                }
              }
            } catch (Throwable failure) {
                close();
                helper.runAfterDelay(1, () -> helper.fail("Performance sample failed: " + failure));
            }
        }

        private void nextScene() throws Throwable {
            clearScene(scene + 1 == scenarios.size());
            if (++scene == scenarios.size()) { report(); close(); helper.succeed(); return; }
            String scenario = scenarios.get(scene);
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.CROP_CLIMATE_RULES.set(List.of("wheat=" + (scenario.equals("natural_match") ? "neutral" : "hot")));
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            GreenhouseEnvironment.clearCache();
            windowIndex = 0;
            warmup = INITIAL_WARMUP;
            if (shipMode) { if (ship == null) allocateShip(); }
            else localAnchor = helper.absolutePos(new BlockPos(8, 80, 8));
            for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
                BlockPos machine = localAnchor.offset(x * 10, 0, z * 10);
                if (scenario.endsWith("_front")) {
                    Block block = scenario.equals("tan_front") ? BuiltInRegistries.BLOCK.get(TAN) : GreenhouseContent.AIR_CONDITIONER.get();
                    BlockState state = block.defaultBlockState();
                    if (scenario.equals("tan_front")) state = state.setValue(BlockStateProperties.ENABLED, true);
                    level.setBlock(machine, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    helper.assertTrue(level.getBlockEntity(machine) != null, "Controller BE missing immediately after placement at " + machine);
                    machines.add(machine);
                    if (scenario.equals("electrical_front")) ((AirConditionerBlockEntity) level.getBlockEntity(machine)).setClimate(CropClimate.HOT);
                    else ((Container) level.getBlockEntity(machine)).setItem(1, new ItemStack(Items.COAL, 64));
                }
                int placed = 0;
                for (int forward = 1; forward <= 4 && placed < 16; forward++) for (int side = -3; side <= 3 && placed < 16; side++) {
                    if (forward * forward + side * side > 16) continue;
                    BlockPos crop = machine.offset(side, 0, -forward);
                    level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    level.setBlock(crop, WHEAT, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    crops.add(crop);
                    placed++;
                }
            }
            helper.assertValueEqual(crops.size(), CHECKS, "Benchmark crop count");
            if (shipMode) {
                worldAnchor = new BlockPos(2000, 40, 2000);
                fillBiome(worldAnchor);
                moveShip(0);
            } else fillBiome(localAnchor);
            chargeControllers();
            maintainControllers();
            helper.assertValueEqual(activeControllers(), machines.size(), "Initial active controller count");
            helper.assertValueEqual(query(), expectedAccepted(true), "Initial scene acceptance");
        }

        private void maintainControllers() {
            for (BlockPos at : machines) {
                BlockEntity entity = level.getBlockEntity(at);
                if (entity instanceof AirConditionerBlockEntity unit) {
                    AirConditionerBlockEntity.serverTick(level, at, unit.getBlockState(), unit);
                } else tickController(entity);
            }
        }

        private void chargeControllers() {
            for (BlockPos at : machines) if (level.getBlockEntity(at) instanceof AirConditionerBlockEntity unit)
                while (unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0) { }
        }

        private int activeControllers() {
            int count = 0;
            for (BlockPos at : machines) {
                BlockEntity entity = level.getBlockEntity(at);
                if (entity instanceof AirConditionerBlockEntity unit ? unit.hasPaidAirflow()
                        : ThermoregulatorDetector.supportsClimates(entity.getBlockState(), Set.of(CropClimate.HOT))) count++;
            }
            return count;
        }

        private String nativeBiome() {
            return GreenhouseEnvironment.nativeBiome(level, crops.getFirst()).unwrapKey().orElseThrow().location().toString();
        }

        @SuppressWarnings("unchecked")
        private static <T extends BlockEntity> void tickController(T entity) {
            var ticker = entity.getBlockState().getTicker(entity.getLevel(), (BlockEntityType<T>) entity.getType());
            if (ticker == null) throw new IllegalStateException("Controller ticker is missing");
            ticker.tick(entity.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity);
        }

        private void fillBiome(BlockPos anchor) {
            var biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            var sampler = level.getChunkSource().randomState().sampler();
            for (int cx = (anchor.getX() - 96) >> 4; cx <= (anchor.getX() + 96) >> 4; cx++)
                for (int cz = (anchor.getZ() - 96) >> 4; cz <= (anchor.getZ() + 96) >> 4; cz++)
                    level.getChunk(cx, cz).fillBiomesFromNoise((x, y, z, unused) -> biome, sampler);
        }

        private void allocateShip() throws Throwable {
            var lookup = MethodHandles.lookup();
            Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> serverContainers = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class<?> ships = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> plots = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
            container = lookup.findStatic(containers, "getContainer", MethodType.methodType(serverContainers, ServerLevel.class)).invoke(level);
            Class<?> physicsType = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
            physics = lookup.findStatic(physicsType, "require", MethodType.methodType(physicsType, Level.class)).invoke(level);
            if (savedPhysicsPaused == null) savedPhysicsPaused = (Boolean) lookup.findVirtual(physicsType, "getPaused", MethodType.methodType(boolean.class)).invoke(physics);
            lookup.findVirtual(physicsType, "setPaused", MethodType.methodType(void.class, boolean.class)).invoke(physics, true);
            ship = lookup.findVirtual(containers, "allocateNewSubLevel", MethodType.methodType(ships, Pose3d.class)).invoke(container, new Pose3d());
            Class<?> ticketType = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
            Class<?> serverShip = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Object ticket = lookup.findStaticGetter(ticketType, "COMMAND_FORCED", ticketType).invoke();
            lookup.findVirtual(serverContainers, "addForceLoadTicket",
                    MethodType.methodType(boolean.class, serverShip, ticketType, Object.class)).invoke(container, ship, ticket, Unit.INSTANCE);
            Object plot = lookup.findVirtual(ships, "getPlot", MethodType.methodType(plots)).invoke(ship);
            localAnchor = (BlockPos) lookup.findVirtual(plots, "getCenterBlock", MethodType.methodType(BlockPos.class)).invoke(plot);
            for (int cx = (localAnchor.getX() - 8) >> 4; cx <= (localAnchor.getX() + 80) >> 4; cx++)
                for (int cz = (localAnchor.getZ() - 8) >> 4; cz <= (localAnchor.getZ() + 80) >> 4; cz++)
                    lookup.findVirtual(plots, "newEmptyChunk", MethodType.methodType(void.class, ChunkPos.class)).invoke(plot, new ChunkPos(cx, cz));
            // One connected hull stays loaded across scenarios; recycling a Sable storage plot
            // in the same server tick can overlap the old plot's queued chunk cleanup.
            for (int x = -4; x <= 73; x++) for (int z = -4; z <= 73; z++)
                level.setBlock(localAnchor.offset(x, -2, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        private void moveShip(int index) throws Throwable {
            Quaterniondc orientation = new Quaterniond().rotateY((index % 4) * Math.PI / 2);
            Pose3d pose = (Pose3d) ((SubLevelAccess) ship).logicalPose();
            Vector3d offset = new Vector3d(localAnchor.getX() + 0.5, localAnchor.getY() + 0.5, localAnchor.getZ() + 0.5)
                    .sub(pose.rotationPoint()).mul(pose.scale());
            orientation.transform(offset);
            Vector3d bodyPosition = new Vector3d(worldAnchor.getX() + 0.5, worldAnchor.getY() + 0.5, worldAnchor.getZ() + 0.5).sub(offset);
            var lookup = MethodHandles.lookup();
            Class<?> physicsType = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
            Class<?> pipelineType = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipeline");
            Class<?> bodyType = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
            Class<?> serverShip = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Object pipeline = lookup.findVirtual(physicsType, "getPipeline", MethodType.methodType(pipelineType)).invoke(physics);
            lookup.findVirtual(pipelineType, "teleport", MethodType.methodType(void.class, bodyType, Vector3dc.class, Quaterniondc.class))
                    .invoke(pipeline, ship, bodyPosition, orientation);
            lookup.findVirtual(pipelineType, "resetVelocity", MethodType.methodType(void.class, bodyType)).invoke(pipeline, ship);
            lookup.findVirtual(physicsType, "updatePose", MethodType.methodType(void.class, serverShip)).invoke(physics, ship);
        }

        private void clearScene(boolean removeShip) throws Throwable {
            for (BlockPos at : machines) level.removeBlock(at, false);
            for (BlockPos at : crops) { level.removeBlock(at, false); level.removeBlock(at.below(), false); }
            machines.clear(); crops.clear();
            if (ship != null && removeShip) disposeShip();
        }

        private void disposeShip() throws Throwable {
            var lookup = MethodHandles.lookup();
            Class<?> ships = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> serverShip = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> serverContainers = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class<?> ticketType = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
            Object ticket = lookup.findStaticGetter(ticketType, "COMMAND_FORCED", ticketType).invoke();
            lookup.findVirtual(serverContainers, "removeForceLoadTicket",
                    MethodType.methodType(boolean.class, serverShip, ticketType, Object.class)).invoke(container, ship, ticket, Unit.INSTANCE);
            lookup.findVirtual(ships, "markRemoved", MethodType.methodType(void.class)).invoke(ship);
            lookup.findVirtual(containers, "processSubLevelRemovals", MethodType.methodType(void.class)).invoke(container);
            ship = null;
        }

        private void report() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "synthetic explicit crop guard calls in an unpaced GameTest server");
            result.put("backend", shipMode ? "sable" : "ordinary_world");
            result.put("randomTickSpeed", level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
            result.put("checksPerTick", CHECKS);
            result.put("realCropCells", CHECKS);
            result.put("controllersInControllerScenes", 64);
            result.put("controllerWork", "64 explicit controller ticker calls each benchmark tick in addition to any normal level ticking. Electrical tickers deduplicate same-tick calls; TAN may receive extra ticks. Included in Pre/Post span, excluded from guard batch. FE top-ups only at unmeasured window boundaries.");
            result.put("serverTickMeasurement", "ServerTickEvent.Pre to this listener's Post; includes the guard batch and intervening level work, excludes later Post listeners and GameTestServer bookkeeping. This server is unpaced, so no observed TPS is inferred.");
            result.put("shipPhysics", shipMode ? "Paused to hold controlled poses; authoritative translations/rotations between windows. This does not measure flight dynamics." : "Not loaded");
            result.put("order", "off,on,on,off; repeated twice per scene, 120 initial warmup and 40 per-window washout ticks");
            result.put("java", System.getProperty("java.version"));
            result.put("mods", ModList.get().getMods().stream().map(mod -> mod.getModId() + ":" + mod.getVersion()).toList());
            result.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            result.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
            result.put("createdAt", Instant.now().toString());
            result.put("consumedResults", consumedResults);
            result.put("windows", rows);
            Path output = Path.of(System.getProperty("biomeBasedCrops.performanceOutput", "performance-result.json")).toAbsolutePath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result));
            BiomeBasedCrops.LOGGER.info("[PERF] Result written to {}", output);
        }

        private void close() {
            if (ended) return;
            ended = true;
            NeoForge.EVENT_BUS.unregister(pre);
            NeoForge.EVENT_BUS.unregister(post);
            if (ship != null) try { disposeShip(); }
            catch (Throwable failure) { BiomeBasedCrops.LOGGER.error("Could not remove benchmark ship after the run", failure); }
            if (savedPhysicsPaused != null) try {
                MethodHandles.lookup().findVirtual(physics.getClass(), "setPaused", MethodType.methodType(void.class, boolean.class)).invoke(physics, savedPhysicsPaused.booleanValue());
            } catch (Throwable failure) { BiomeBasedCrops.LOGGER.error("Could not restore benchmark physics pause state", failure); }
            Config.BLOCK_NATURAL_GROWTH.set(savedNatural);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(savedCrops);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(savedCompat);
            Config.CROP_CLIMATE_RULES.set(savedRules);
            Config.CROP_BIOME_RULES.set(savedBiomes);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(savedHouses);
            CropMatcher.clearCaches(); CropClimateRules.clearCache(); GreenhouseEnvironment.clearCache();
        }
    }

    private static final class Window {
        final List<Long> ticks = new ArrayList<>(), guard = new ArrayList<>();
        long gcCount, gcMillis, heap, cpuStarted, accepted;
        String startedAt, biomeStart, biomeEnd;
        int activeStart, activeEnd;
        boolean begun;

        void begin(int active, String biome) {
            gcCount = gcCount(); gcMillis = gcMillis(); heap = usedHeap();
            cpuStarted = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            startedAt = Instant.now().toString(); activeStart = active; biomeStart = biome; begun = true;
        }

        Map<String, Object> result(String scenario, boolean ship, int index, boolean enabled) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", scenario); row.put("backend", ship ? "sable" : "ordinary_world");
            row.put("window", index); row.put("repeat", index / 4); row.put("enabled", enabled);
            row.put("startedAt", startedAt); row.put("finishedAt", Instant.now().toString());
            row.put("samples", ticks.size()); row.put("checksPerTick", StressRun.CHECKS);
            row.put("accepted", accepted); row.put("guardMs", stats(guard)); row.put("serverTickPrePostMs", stats(ticks));
            row.put("guardSamplesNs", guard); row.put("serverTickPrePostSamplesNs", ticks);
            row.put("gcCollections", gcCount() - gcCount); row.put("gcPauseMs", gcMillis() - gcMillis);
            row.put("serverThreadCpuMs", (ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - cpuStarted) / 1_000_000.0);
            row.put("heapBeforeBytes", heap); row.put("heapAfterBytes", usedHeap());
            row.put("activeControllersStart", activeStart); row.put("activeControllersEnd", activeEnd);
            row.put("nativeBiomeStart", biomeStart); row.put("nativeBiomeEnd", biomeEnd);
            return row;
        }
    }

    private static Map<String, Double> stats(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        double average = Arrays.stream(sorted).average().orElse(0) / 1_000_000.0;
        return Map.of("mean", average, "p50", sorted[(int) Math.floor((sorted.length - 1) * 0.5)] / 1_000_000.0,
                "p95", sorted[(int) Math.floor((sorted.length - 1) * 0.95)] / 1_000_000.0,
                "max", sorted[sorted.length - 1] / 1_000_000.0);
    }

    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum(); }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
    private static long usedHeap() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
}
