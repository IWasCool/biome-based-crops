package com.ourcreation.biomebasedcrops;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

/** Optional proof using actual Sable plots and TAN fuel timers, without a greenhouse. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class SableTanCropGameTests {
    private SableTanCropGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "sable_tan_crops", timeoutTicks = 100)
    public static void fueledTanRegulatorConditionsShipCropsAfterMovementAndRotation(GameTestHelper helper) throws Throwable {
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Class<?> serverContainers = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Class<?> shipType = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        Class<?> plotType = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
        var lookup = MethodHandles.lookup();
        // Exact signatures avoid resolving Sable's client-only overload on a dedicated server.
        Object container = lookup.findStatic(containers, "getContainer",
                MethodType.methodType(serverContainers, ServerLevel.class)).invoke(level);
        Object ship = lookup.findVirtual(containers, "allocateNewSubLevel",
                MethodType.methodType(shipType, Pose3d.class)).invoke(container, new Pose3d());
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        boolean greenhouses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        try {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            Object plot = lookup.findVirtual(shipType, "getPlot", MethodType.methodType(plotType)).invoke(ship);
            BlockPos crop = (BlockPos) lookup.findVirtual(plotType, "getCenterBlock",
                    MethodType.methodType(BlockPos.class)).invoke(plot);
            lookup.findVirtual(plotType, "newEmptyChunk",
                    MethodType.methodType(void.class, ChunkPos.class)).invoke(plot, new ChunkPos(crop));
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(ShipCropEnvironment.isOnShip(level, crop), "TAN fixture crop was not inside the actual Sable plot");

            BlockPos coldWorld = helper.absolutePos(new BlockPos(0, 80, 128));
            BlockPos hotWorld = coldWorld.east(128);
            BlockPos temperateWorld = coldWorld.east(256);
            fillNativeBiome(level, coldWorld, Biomes.SNOWY_PLAINS);
            fillNativeBiome(level, hotWorld, Biomes.DESERT);
            fillNativeBiome(level, temperateWorld, Biomes.FOREST);
            BlockPos machine = crop.south(2);
            Block regulator = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toughasnails:thermoregulator"));

            for (boolean heating : new boolean[] { true, false }) {
                CropProfile profile = new CropProfile(Set.of(heating ? CropClimate.HOT : CropClimate.ICY));
                level.setBlock(machine, regulator.defaultBlockState().setValue(BlockStateProperties.ENABLED, true)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
                SableCropGameTests.moveShipTo(level, ship, crop, heating ? coldWorld : hotWorld, new Quaterniond());
                helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(heating ? Biomes.SNOWY_PLAINS : Biomes.DESERT),
                        "Ship TAN fixture did not reach its first native biome");
                helper.assertFalse(CommonEvents.canGrow(level, crop, profile), "Unconditioned ship crop ignored its current native biome");
                helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop, profile), "Unfueled ship regulator supplied a climate");
                Container fuel = (Container) level.getBlockEntity(machine);
                fuel.setItem(heating ? 1 : 0, new ItemStack(heating ? Items.COAL : Items.ICE));
                tickRegulator(level.getBlockEntity(machine));
                helper.assertTrue(ThermoregulatorDetector.canSupportProfile(level, crop, profile), "Fueled ship TAN mode did not reach its front crop");
                helper.assertTrue(CommonEvents.canGrow(level, crop, profile), "Fueled ship TAN regulator still required a greenhouse");

                SableCropGameTests.moveShipTo(level, ship, crop, temperateWorld, new Quaterniond());
                helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(Biomes.FOREST), "Moving TAN ship did not update its native biome");
                helper.assertTrue(CommonEvents.canGrow(level, crop, profile), "Moving the ship broke TAN crop coverage");
                SableCropGameTests.moveShipTo(level, ship, crop, temperateWorld, new Quaterniond().rotateY(Math.PI / 2));
                helper.assertTrue(CommonEvents.canGrow(level, crop, profile), "Rotating the ship broke its local TAN coverage");
                CropClimate transition = heating ? CropClimate.WARM : CropClimate.COLD;
                helper.assertTrue(GreenhouseEnvironment.currentClimate(level, machine.north(5)) == transition,
                        "Rotated TAN ship lost its first transition ring in its current world biome");
                helper.assertTrue(CommonEvents.canGrow(level, machine.north(5), new CropProfile(Set.of(transition))),
                        "Rotated TAN ship transition ring did not support its matching crop");
                level.setBlock(machine, level.getBlockState(machine).setValue(BlockStateProperties.ENABLED, false), Block.UPDATE_CLIENTS);
                helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(Biomes.FOREST), "Stopping TAN unexpectedly moved the ship's crop");
                helper.assertFalse(ThermoregulatorDetector.canSupportProfile(level, crop, profile), "Disabled ship TAN regulator supplied a climate");
                helper.assertFalse(CommonEvents.canGrow(level, crop, profile), "Disabled ship TAN regulator continued supporting crops");
                level.removeBlock(machine, false);
                helper.assertTrue(GreenhouseEnvironment.nativeBiome(level, crop).is(Biomes.FOREST), "Removing TAN unexpectedly moved the ship's crop");
                helper.assertFalse(CommonEvents.canGrow(level, crop, profile), "Removed ship TAN regulator continued supporting crops");
            }
            helper.succeed();
        } finally {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(greenhouses);
            GreenhouseEnvironment.clearCache();
            lookup.findVirtual(shipType, "markRemoved", MethodType.methodType(void.class)).invoke(ship);
            lookup.findVirtual(containers, "processSubLevelRemovals", MethodType.methodType(void.class)).invoke(container);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickRegulator(T entity) {
        var ticker = entity.getBlockState().getTicker(entity.getLevel(), (BlockEntityType<T>) entity.getType());
        if (ticker == null) throw new IllegalStateException("Ship TAN controller has no server ticker");
        ticker.tick(entity.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity);
    }

    private static void fillNativeBiome(ServerLevel level, BlockPos center, ResourceKey<Biome> biome) {
        var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        var sampler = level.getChunkSource().randomState().sampler();
        for (int cx = (center.getX() >> 4) - 1; cx <= (center.getX() >> 4) + 1; cx++) {
            for (int cz = (center.getZ() >> 4) - 1; cz <= (center.getZ() >> 4) + 1; cz++) {
                level.getChunk(cx, cz).fillBiomesFromNoise((x, y, z, unused) -> holder, sampler);
            }
        }
    }
}
