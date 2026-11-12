package com.ourcreation.biomebasedcrops.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlUtil;
import com.ourcreation.biomebasedcrops.AirConditionerBlock;
import com.ourcreation.biomebasedcrops.AirConditionerBlockEntity;
import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.CropClimate;
import com.ourcreation.biomebasedcrops.GreenhouseContent;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import com.ourcreation.biomebasedcrops.GreenhouseSettings;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in visual workload; loaded only from gameTest, never included in the release JAR. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID, value = Dist.CLIENT)
public final class ClientPerformanceHarness {
    private static final boolean ENABLED = Boolean.getBoolean("biome_based_crops.clientPerformance");
    private static final long SECOND = 1_000_000_000L;
    private static final long INITIAL_WARMUP = 20 * SECOND, SETTLE = 4 * SECOND, SAMPLE = 12 * SECOND;
    private static final int[] COUNTS = {0, 16, 0, 64, 0, 64, 0, 16, 0};
    private static final List<BlockPos> MACHINES = new ArrayList<>();
    private static final double CAMERA_X = 10.5, CAMERA_Y = 135.0, CAMERA_Z = -15.5;
    private static final float CAMERA_YAW = 0, CAMERA_PITCH = 18;
    private static final List<WindowResult> RESULTS = new ArrayList<>();
    private static final List<Long> INTERVALS = new ArrayList<>(), WORK = new ArrayList<>();
    private static final List<String> CSV = new ArrayList<>();
    private static volatile MinecraftServer server;
    private static volatile boolean setupReady;
    private static volatile int requestedCount, actualServerCount;
    private static int actualClientCount, actualClientMachineCount;
    private static boolean setupQueued, settingsApplied, screenshotInactive, screenshotActive, sampling;
    private static volatile boolean done;
    private static String ambientClimate;
    private static int phase = -1, width, height, interruptions, phaseInterruptions;
    private static long started, initialReady, phaseStarted, lastFrame, frameStarted;
    private static long lastVanillaSample, vanillaFpsSum;
    private static int vanillaFpsSamples, vanillaFpsMin = Integer.MAX_VALUE, vanillaFpsMax;
    private static String failure;

    private ClientPerformanceHarness() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (!settingsApplied) {
            settingsApplied = true;
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            mc.getWindow().setFramerateLimit(260);
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(6);
            mc.options.graphicsMode().set(GraphicsStatus.FANCY);
            mc.options.particles().set(ParticleStatus.ALL);
            mc.options.fov().set(70);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
        }
        if (!setupQueued) {
            setupQueued = true;
            server = mc.getSingleplayerServer();
            server.execute(() -> {
                try { prepareScene(server); }
                catch (Throwable error) {
                    failure = error.toString();
                    BiomeBasedCrops.LOGGER.error("[CLIENT_PERF] Scene setup failed", error);
                    setupReady = true;
                }
            });
        }
        if (!setupReady) return;
        mc.player.getAbilities().flying = true;
        mc.player.setPos(CAMERA_X, CAMERA_Y, CAMERA_Z);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setYRot(CAMERA_YAW);
        mc.player.setXRot(CAMERA_PITCH);
        mc.player.yRotO = CAMERA_YAW;
        mc.player.xRotO = CAMERA_PITCH;
        actualClientCount = 0;
        actualClientMachineCount = 0;
        for (BlockPos at : MACHINES) {
            var state = mc.level.getBlockState(at);
            if (state.is(GreenhouseContent.AIR_CONDITIONER.get())) {
                actualClientMachineCount++;
                if (state.getValue(AirConditionerBlock.ACTIVE)) actualClientCount++;
            }
        }
        long now = System.nanoTime();
        if (sampling && now - lastVanillaSample >= SECOND) {
            int fps = mc.getFps();
            if (fps > 0) {
                vanillaFpsSum += fps;
                vanillaFpsSamples++;
                vanillaFpsMin = Math.min(vanillaFpsMin, fps);
                vanillaFpsMax = Math.max(vanillaFpsMax, fps);
            }
            lastVanillaSample = now;
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || done || !setupReady || event.getServer() != server) return;
        ServerLevel level = server.overworld();
        int count = 0;
        for (int i = 0; i < MACHINES.size(); i++) {
            BlockPos at = MACHINES.get(i);
            if (!(level.getBlockEntity(at) instanceof AirConditionerBlockEntity unit)) continue;
            CropClimate target = i < requestedCount ? CropClimate.HOT : CropClimate.NEUTRAL;
            if (unit.selectedClimate() != target) unit.setClimate(target);
            unit.energyStorage().receiveEnergy(10_000, false);
            if (unit.hasPaidAirflow()) count++;
        }
        actualServerCount = count;
    }

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        if (ENABLED && !done) frameStarted = System.nanoTime();
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (!setupReady || mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        if (started == 0) {
            started = now;
            width = mc.getWindow().getWidth();
            height = mc.getWindow().getHeight();
            CSV.add("stage,expected_active_ac,frame_interval_ms,render_frame_event_span_ms");
        }
        if (failure != null || now - started > 360 * SECOND) {
            if (failure == null) failure = "Six-minute timeout; the benchmark needs an uninterrupted focused window.";
            finish(mc);
            return;
        }
        boolean valid = mc.isWindowActive() && !mc.isPaused() && mc.screen == null
                && GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE
                && mc.getWindow().getWidth() == width && mc.getWindow().getHeight() == height
                && !mc.options.enableVsync().get() && mc.getWindow().getFramerateLimit() >= 260
                && mc.options.renderDistance().get() == 6 && mc.options.simulationDistance().get() == 6
                && mc.options.graphicsMode().get() == GraphicsStatus.FANCY && mc.options.particles().get() == ParticleStatus.ALL
                && actualClientMachineCount == 64
                && actualServerCount == requestedCount && actualClientCount == requestedCount;
        if (!valid) {
            // Restart a whole window, instead of selectively dropping slow frames from its sample.
            interruptions++;
            phaseInterruptions++;
            initialReady = 0;
            phaseStarted = now;
            lastFrame = 0;
            sampling = false;
            clearVanillaSamples();
            INTERVALS.clear();
            WORK.clear();
            return;
        }
        if (phase < 0) {
            if (initialReady == 0) initialReady = now;
            if (!screenshotInactive && now - initialReady >= 10 * SECOND) {
                screenshotInactive = true;
                Screenshot.grab(mc.gameDirectory, "client-performance-inactive.png", mc.getMainRenderTarget(),
                        message -> BiomeBasedCrops.LOGGER.info("[CLIENT_PERF] {}", message.getString()));
            }
            if (now - initialReady < INITIAL_WARMUP) return;
            beginPhase(0, now);
            return;
        }
        long elapsed = now - phaseStarted;
        if (!screenshotActive && requestedCount == 64 && elapsed >= 2 * SECOND && elapsed < SETTLE) {
            screenshotActive = true;
            Screenshot.grab(mc.gameDirectory, "client-performance-64-active.png", mc.getMainRenderTarget(),
                    message -> BiomeBasedCrops.LOGGER.info("[CLIENT_PERF] {}", message.getString()));
        }
        if (elapsed < SETTLE) { lastFrame = 0; sampling = false; return; }
        if (elapsed < SETTLE + SAMPLE) {
            sampling = true;
            if (lastFrame != 0 && frameStarted >= lastFrame) {
                INTERVALS.add(now - lastFrame);
                WORK.add(now - frameStarted);
            }
            lastFrame = now;
            return;
        }
        sampling = false;
        if (INTERVALS.size() < 100) {
            failure = "Insufficient measured frames in stage " + phase;
            finish(mc);
            return;
        }
        Stats intervalStats = stats(INTERVALS);
        WindowResult result = new WindowResult(phase, requestedCount, actualServerCount, actualClientCount,
                INTERVALS.size(), 1000.0 / intervalStats.meanMs, intervalStats, stats(WORK), phaseInterruptions,
                vanillaFpsSamples, vanillaFpsSamples == 0 ? 0 : vanillaFpsSum / (double) vanillaFpsSamples,
                vanillaFpsSamples == 0 ? 0 : vanillaFpsMin, vanillaFpsMax);
        RESULTS.add(result);
        for (int i = 0; i < INTERVALS.size(); i++) CSV.add(String.format(Locale.ROOT, "%d,%d,%.6f,%.6f",
                phase, requestedCount, INTERVALS.get(i) / 1_000_000.0, WORK.get(i) / 1_000_000.0));
        BiomeBasedCrops.LOGGER.info("[CLIENT_PERF] stage={} active={} frames={} meanFPS={} p95FrameMs={}",
                phase, requestedCount, result.samples, result.meanFps, result.frameIntervals.p95Ms);
        checkpoint(mc);
        if (phase + 1 == COUNTS.length) finish(mc);
        else beginPhase(phase + 1, now);
    }

    private static void beginPhase(int index, long now) {
        phase = index;
        requestedCount = COUNTS[index];
        phaseStarted = now;
        phaseInterruptions = 0;
        lastFrame = 0;
        sampling = false;
        clearVanillaSamples();
        INTERVALS.clear();
        WORK.clear();
        BiomeBasedCrops.LOGGER.info("[CLIENT_PERF] Beginning stage {} with {} active ACs", phase, requestedCount);
    }

    private static void clearVanillaSamples() {
        lastVanillaSample = vanillaFpsSum = 0;
        vanillaFpsSamples = vanillaFpsMax = 0;
        vanillaFpsMin = Integer.MAX_VALUE;
    }

    private static Path resultsDirectory(Minecraft mc) {
        // DevLaunch normally passes gameDirectory as '.', so normalize it before resolving its sibling.
        return mc.gameDirectory.toPath().toAbsolutePath().normalize().resolveSibling("results");
    }

    private static void checkpoint(Minecraft mc) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject partial = new JsonObject();
        partial.addProperty("complete", false);
        partial.addProperty("completedWindows", RESULTS.size());
        partial.addProperty("gpu", GlUtil.getRenderer());
        partial.add("windows", gson.toJsonTree(RESULTS));
        try {
            Path directory = resultsDirectory(mc);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("client-performance.partial.json"), gson.toJson(partial));
        } catch (IOException error) {
            BiomeBasedCrops.LOGGER.error("[CLIENT_PERF] Could not save aggregate checkpoint", error);
        }
    }

    private static void prepareScene(MinecraftServer runningServer) {
        ServerLevel level = runningServer.overworld();
        GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
        GreenhouseEnvironment.clearCache();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, runningServer);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, runningServer);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, runningServer);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, runningServer);
        level.setDayTime(1000);
        level.setWeatherParameters(0, 100000, false, false);
        var plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        var sampler = level.getChunkSource().randomState().sampler();
        // Paint both the complete scene and a biome-zoom margin, independently of the copied template.
        for (int cx = -2; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) {
            var chunk = level.getChunk(cx, cz);
            chunk.fillBiomesFromNoise((x, y, z, unused) -> plains, sampler);
            chunk.setUnsaved(true);
        }
        for (BlockPos at : BlockPos.betweenClosed(new BlockPos(-4, 127, -4), new BlockPos(25, 129, 26)))
            level.setBlock(at, at.getY() == 127 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
            BlockPos at = new BlockPos(x * 3, 128, z * 3);
            level.setBlock(at, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(at);
            while (unit.energyStorage().receiveEnergy(10_000, false) > 0) { }
            unit.setClimate(CropClimate.NEUTRAL);
            if (unit.localClimate() != CropClimate.NEUTRAL)
                throw new IllegalStateException("Benchmark baseline must have Neutral ambient climate at " + at);
            MACHINES.add(at);
        }
        ambientClimate = CropClimate.NEUTRAL.name();
        for (var player : runningServer.getPlayerList().getPlayers()) {
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_YAW, CAMERA_PITCH);
        }
        requestedCount = actualServerCount = 0;
        setupReady = true;
        BiomeBasedCrops.LOGGER.info("[CLIENT_PERF] Fixed scene ready: 64 visible AC blocks, starting focused-window warmup");
    }

    private static Stats stats(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        double mean = values.stream().mapToDouble(Long::doubleValue).average().orElse(0) / 1_000_000.0;
        return new Stats(mean, percentile(sorted, 0.5), percentile(sorted, 0.95), percentile(sorted, 0.99),
                sorted[sorted.length - 1] / 1_000_000.0);
    }

    private static double percentile(long[] sorted, double quantile) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * quantile) - 1)] / 1_000_000.0;
    }

    private static void finish(Minecraft mc) {
        done = true;
        JsonObject report = new JsonObject();
        report.addProperty("recordedAt", Instant.now().toString());
        report.addProperty("success", failure == null && RESULTS.size() == COUNTS.length);
        if (failure != null) report.addProperty("failure", failure);
        report.addProperty("description", "Integrated client/server render workload. Same 64 AC geometry; active fan, particles and integrated-server cost change together. Audio is muted. Not a pure GPU-only benchmark.");
        report.addProperty("scene", "8x8 AC blocks, spacing 3, fronts toward fixed camera; no mobs or random crop ticks");
        report.addProperty("camera", "10.5,135,-15.5 yaw=0 pitch=18");
        report.addProperty("width", width);
        report.addProperty("height", height);
        report.addProperty("renderDistance", mc.options.renderDistance().get());
        report.addProperty("simulationDistance", mc.options.simulationDistance().get());
        report.addProperty("graphics", mc.options.graphicsMode().get().name());
        report.addProperty("particles", mc.options.particles().get().name());
        report.addProperty("fov", mc.options.fov().get());
        report.addProperty("vsync", mc.options.enableVsync().get());
        report.addProperty("frameCapSetting", mc.options.framerateLimit().get());
        report.addProperty("windowFrameLimit", mc.getWindow().getFramerateLimit());
        report.addProperty("unlimitedSentinel", 260);
        report.addProperty("pauseOnLostFocus", mc.options.pauseOnLostFocus);
        report.addProperty("ambientClimate", ambientClimate);
        report.addProperty("refillCadence", "Every integrated-server tick, all 64 units receive up to 10,000 FE each; active units consume 360 FE per tick.");
        report.addProperty("masterVolume", 0);
        report.addProperty("gpu", GlUtil.getRenderer());
        report.addProperty("gpuVendor", GlUtil.getVendor());
        report.addProperty("openGL", GlUtil.getOpenGLVersion());
        report.addProperty("java", System.getProperty("java.version"));
        report.addProperty("processors", Runtime.getRuntime().availableProcessors());
        report.addProperty("maximumHeapBytes", Runtime.getRuntime().maxMemory());
        report.addProperty("focusedAndUnminimizedRequired", true);
        report.addProperty("all64ClientMachinesPresentRequired", true);
        JsonArray screenshots = new JsonArray();
        screenshots.add("run/screenshots/client-performance-inactive.png");
        screenshots.add("run/screenshots/client-performance-64-active.png");
        report.add("sceneScreenshots", screenshots);
        report.addProperty("interruptedFramesThatRestartedAWindow", interruptions);
        report.addProperty("initialWarmupSeconds", INITIAL_WARMUP / SECOND);
        report.addProperty("settleSecondsPerStage", SETTLE / SECOND);
        report.addProperty("measurementSecondsPerStage", SAMPLE / SECOND);
        report.addProperty("plannedDurationAfterSceneReadySeconds", (INITIAL_WARMUP + COUNTS.length * (SETTLE + SAMPLE)) / SECOND);
        report.addProperty("actualDurationAfterSceneReadySeconds", (System.nanoTime() - started) / (double) SECOND);
        JsonArray order = new JsonArray();
        for (int count : COUNTS) order.add(count);
        report.add("requestedActiveAcSequence", order);
        JsonArray packs = new JsonArray();
        mc.options.resourcePacks.forEach(packs::add);
        report.add("resourcePacks", packs);
        JsonArray mods = new JsonArray();
        ModList.get().getMods().forEach(mod -> mods.add(mod.getModId() + "@" + mod.getVersion()));
        report.add("mods", mods);
        var gson = new GsonBuilder().setPrettyPrinting().create();
        report.add("windows", gson.toJsonTree(RESULTS));
        try {
            Path directory = resultsDirectory(mc);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("client-performance.json"), gson.toJson(report));
            Files.write(directory.resolve("client-frame-times.csv"), CSV);
            BiomeBasedCrops.LOGGER.info("[CLIENT_PERF] Complete: success={} results={}", report.get("success"), directory.toAbsolutePath());
        } catch (IOException error) {
            BiomeBasedCrops.LOGGER.error("[CLIENT_PERF] Could not save results", error);
        }
        if (Boolean.parseBoolean(System.getProperty("biome_based_crops.clientPerformanceAutoExit", "true"))) mc.stop();
    }

    private record WindowResult(int stage, int expectedActiveAc, int observedServerActiveAc, int observedClientActiveAc,
                                int samples, double meanFps, Stats frameIntervals, Stats renderFrameEventSpans, int interruptions,
                                int vanillaFpsSamples, double vanillaMeanFps, int vanillaMinFps, int vanillaMaxFps) { }
    private record Stats(double meanMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) { }
}
