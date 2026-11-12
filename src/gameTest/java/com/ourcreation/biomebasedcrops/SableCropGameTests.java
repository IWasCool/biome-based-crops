package com.ourcreation.biomebasedcrops;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Optional runtime proof using real Sable storage plots and live poses, without requiring Sable to load the mod. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class SableCropGameTests {
    private SableCropGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "sable_crops", timeoutTicks = 100)
    public static void shipCropsFollowTheWorldBiomeAndLocalAirConditioner(GameTestHelper helper) throws Throwable {
        if (!ModList.get().isLoaded("sable")) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Class<?> serverContainers = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Class<?> shipType = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        Class<?> plotType = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
        var lookup = MethodHandles.lookup();
        // Class.getMethod resolves every overload, including Sable's ClientLevel overload.
        // Exact method handles keep this optional proof safe on a dedicated server.
        Object container = lookup.findStatic(containers, "getContainer",
                MethodType.methodType(serverContainers, ServerLevel.class)).invoke(level);
        Pose3d initialPose = new Pose3d();
        Object ship = lookup.findVirtual(containers, "allocateNewSubLevel",
                MethodType.methodType(shipType, Pose3d.class)).invoke(container, initialPose);
        try {
            Object plot = lookup.findVirtual(shipType, "getPlot", MethodType.methodType(plotType)).invoke(ship);
            BlockPos crop = (BlockPos) lookup.findVirtual(plotType, "getCenterBlock",
                    MethodType.methodType(BlockPos.class)).invoke(plot);
            lookup.findVirtual(plotType, "newEmptyChunk",
                    MethodType.methodType(void.class, ChunkPos.class)).invoke(plot, new ChunkPos(crop));
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos machine = crop.south(2);
            level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            var unit = (AirConditionerBlockEntity) level.getBlockEntity(machine);
            helper.assertTrue(ShipCropEnvironment.isOnShip(level, crop), "Sable did not recognize the actual crop plot");

            BlockPos temperate = helper.absolutePos(new BlockPos(0, 80, 0));
            BlockPos desert = temperate.east(128);
            fillNativeBiome(level, temperate, Biomes.FOREST);
            fillNativeBiome(level, desert, Biomes.DESERT);
            var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            CropClimate temperateClimate = CropClimate.at(registry.getHolderOrThrow(Biomes.FOREST), temperate);
            CropClimate desertClimate = CropClimate.at(registry.getHolderOrThrow(Biomes.DESERT), desert);
            helper.assertTrue(temperateClimate != desertClimate, "Fixture native climates must differ");
            CropProfile temperateCrop = new CropProfile(Set.of(temperateClimate));
            CropProfile desertCrop = new CropProfile(Set.of(desertClimate));
            moveShipTo(level, ship, crop, temperate, new Quaterniond());
            helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(Biomes.FOREST), "Crop read its storage biome instead of the ship's Forest location");
            helper.assertTrue(CommonEvents.canGrow(level, crop, temperateCrop), "Temperate ship crop could not grow over Forest");
            helper.assertFalse(CommonEvents.canGrow(level, crop, desertCrop), "Hot ship crop grew over Forest without conditioning");

            moveShipTo(level, ship, crop, desert, new Quaterniond());
            helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(Biomes.DESERT), "Moving ship retained its previous biome");
            helper.assertTrue(CommonEvents.canGrow(level, crop, desertCrop), "Hot ship crop did not follow the Desert biome");
            helper.assertFalse(CommonEvents.canGrow(level, crop, temperateCrop), "Temperate ship crop ignored its new Desert location");

            unit.setClimate(temperateClimate);
            unit.energyStorage().receiveEnergy(10_000, false);
            AirConditionerBlockEntity.serverTick(level, machine, unit.getBlockState(), unit);
            helper.assertTrue(unit.hasPaidAirflow(), "Ship AC did not activate outside a greenhouse");
            helper.assertTrue(CommonEvents.canGrow(level, crop, temperateCrop), "Local ship AC did not condition the crop");
            moveShipTo(level, ship, crop, desert, new Quaterniond().rotateY(Math.PI / 2));
            helper.assertTrue(CommonEvents.canGrow(level, crop, temperateCrop), "Rotating the ship broke its local AC coverage");
            level.removeBlock(machine, false);
            helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(Biomes.DESERT), "Changing ship mass displaced the crop from its world biome");
            helper.assertFalse(CommonEvents.canGrow(level, crop, temperateCrop), "Removed ship AC continued to condition the crop");
            helper.succeed();
        } catch (Throwable failure) {
            BiomeBasedCrops.LOGGER.error("Sable crop compatibility proof failed", failure);
            throw failure;
        } finally {
            lookup.findVirtual(shipType, "markRemoved", MethodType.methodType(void.class)).invoke(ship);
            lookup.findVirtual(containers, "processSubLevelRemovals", MethodType.methodType(void.class)).invoke(container);
        }
    }

    static void moveShipTo(ServerLevel level, Object ship, BlockPos localCrop, BlockPos worldCrop, Quaterniondc orientation) throws Throwable {
        // Update the authoritative rigid body, not just its cached logical pose. Block
        // changes recalculate ship mass and would overwrite a pose-only test teleport.
        Pose3d pose = (Pose3d) ((SubLevelAccess) ship).logicalPose();
        Vector3d cropOffset = new Vector3d(localCrop.getX() + 0.5, localCrop.getY() + 0.5, localCrop.getZ() + 0.5)
                .sub(pose.rotationPoint()).mul(pose.scale());
        orientation.transform(cropOffset);
        Vector3d bodyPosition = new Vector3d(worldCrop.getX() + 0.5, worldCrop.getY() + 0.5, worldCrop.getZ() + 0.5)
                .sub(cropOffset);
        var lookup = MethodHandles.lookup();
        Class<?> physicsType = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
        Class<?> pipelineType = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipeline");
        Class<?> bodyType = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
        Class<?> serverShipType = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
        Object physics = lookup.findStatic(physicsType, "require", MethodType.methodType(physicsType, Level.class)).invoke(level);
        Object pipeline = lookup.findVirtual(physicsType, "getPipeline", MethodType.methodType(pipelineType)).invoke(physics);
        lookup.findVirtual(pipelineType, "teleport", MethodType.methodType(void.class, bodyType, Vector3dc.class, Quaterniondc.class))
                .invoke(pipeline, ship, bodyPosition, orientation);
        lookup.findVirtual(physicsType, "updatePose", MethodType.methodType(void.class, serverShipType)).invoke(physics, ship);
    }

    private static void fillNativeBiome(ServerLevel level, BlockPos center, ResourceKey<Biome> biome) {
        var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        var sampler = level.getChunkSource().randomState().sampler();
        // Include neighboring chunks because Minecraft's biome zoom samples adjacent quart cells.
        for (int cx = (center.getX() >> 4) - 1; cx <= (center.getX() >> 4) + 1; cx++) {
            for (int cz = (center.getZ() >> 4) - 1; cz <= (center.getZ() >> 4) + 1; cz++) {
                level.getChunk(cx, cz).fillBiomesFromNoise((x, y, z, unused) -> holder, sampler);
            }
        }
    }
}
