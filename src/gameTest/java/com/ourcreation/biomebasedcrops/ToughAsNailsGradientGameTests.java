package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class ToughAsNailsGradientGameTests {
    private ToughAsNailsGradientGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "tan_gradient_temperature")
    public static void regulatorGradientReachesGaugeAndPlayerExactlyOnce(GameTestHelper helper) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        boolean greenhouses = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        var level = helper.getLevel();
        BlockPos machine = helper.absolutePos(new BlockPos(8, 80, 8));
        try {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            helper.setDayTime(1000);
            fillBiome(level, machine, CropClimate.HOT);
            Block regulator = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toughasnails:thermoregulator"));
            level.setBlock(machine, regulator.defaultBlockState().setValue(BlockStateProperties.ENABLED, true)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), Block.UPDATE_ALL);
            BlockEntity unit = level.getBlockEntity(machine);
            ((Container) unit).setItem(0, new ItemStack(Items.ICE));
            tickRegulator(unit);

            Class<?> api = Class.forName("toughasnails.api.temperature.TemperatureHelper");
            Class<?> temperature = Class.forName("toughasnails.api.temperature.TemperatureLevel");
            Class<?> implementation = Class.forName("toughasnails.temperature.TemperatureHelperImpl");
            Method valueOf = temperature.getMethod("valueOf", String.class);
            Method positional = api.getMethod("getTemperatureAtPos", Level.class, BlockPos.class);
            Method gauge = implementation.getMethod("getTemperatureAtPosWithoutProximity", Level.class, BlockPos.class);
            Method nativeRegulators = implementation.getMethod("modifyTemperatureByThermoregulators",
                    Level.class, Set.class, BlockPos.class, temperature);
            Method nativeEffect = unit.getClass().getMethod("getEffectAtPos", BlockPos.class);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Object data = api.getMethod("getTemperatureData", Player.class).invoke(null, player);
            Class<?> contract = Class.forName("toughasnails.api.temperature.ITemperature");
            Set<BlockPos> controllers = Set.of(machine);
            CropClimate[] rings = {CropClimate.ICY, CropClimate.COLD, CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT};
            for (int distance = 1; distance <= 8; distance++) {
                BlockPos query = machine.east(distance);
                CropClimate expected = rings[Math.max(0, distance - 4)];
                Object ambient = positional.invoke(null, level, query);
                Object instrument = gauge.invoke(null, level, query);
                Object adjusted = nativeRegulators.invoke(null, level, controllers, query, ambient);
                helper.assertTrue(expected.name().equals(ambient.toString()), "Wrong public TAN ambient at " + distance + ": " + ambient);
                helper.assertTrue(expected.name().equals(instrument.toString()), "Wrong TAN gauge at " + distance + ": " + instrument);
                helper.assertTrue(ambient.equals(adjusted), "TAN applied its regulator effect twice at " + distance + ": " + adjusted);
                player.setPos(Vec3.atBottomCenterOf(query));
                contract.getMethod("setNearbyThermoregulators", Set.class).invoke(data, new HashSet<>(controllers));
                ToughAsNailsGreenhouseGameTests.assertNativePlayerTemperature(helper, api, player, expected);
            }

            // TAN fills native air below a roof. Exercise a real filled volume and its
            // sheltered Neutral ambient, which differs from this biome's Hot classification.
            for (int distance = 1; distance <= 8; distance++)
                level.setBlock(machine.east(distance).above(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int tick = 0; tick < 20; tick++) tickRegulator(unit);
            BlockPos query = machine.east(5);
            Object sheltered = positional.invoke(null, level, query);
            Object shelteredAdjusted = nativeRegulators.invoke(null, level, controllers, query, sheltered);
            helper.assertTrue("COLD".equals(sheltered.toString()) && sheltered.equals(shelteredAdjusted),
                    "TAN applied a second adjustment to the sheltered Cold ring: " + shelteredAdjusted);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            Object restoredEffect = nativeEffect.invoke(unit, query);
            Object restoredTemperature = nativeRegulators.invoke(null, level, controllers, query, valueOf.invoke(null, "HOT"));
            helper.assertTrue("COOLING".equals(restoredEffect.toString()), "Disabling compatibility did not restore native TAN effect: " + restoredEffect);
            helper.assertTrue("NEUTRAL".equals(restoredTemperature.toString()), "Native TAN regulator adjustment was not restored: " + restoredTemperature);

            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            fillBiome(level, machine, CropClimate.WARM);
            for (boolean tan : new boolean[]{true, false}) {
                level.removeBlock(machine, false);
                // With no controller, TAN's real shaded baseline must be Neutral.
                helper.assertTrue("NEUTRAL".equals(positional.invoke(null, level, machine.east(5)).toString()),
                        "Shaded fixture must have native Neutral air");
                Block block = tan ? regulator : GreenhouseContent.AIR_CONDITIONER.get();
                var state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
                if (tan) state = state.setValue(BlockStateProperties.ENABLED, true);
                level.setBlock(machine, state, Block.UPDATE_ALL);
                BlockEntity heater = level.getBlockEntity(machine);
                if (tan) {
                    ((Container) heater).setItem(1, new ItemStack(Items.COAL));
                    tickRegulator(heater);
                } else {
                    var ac = (AirConditionerBlockEntity) heater;
                    ac.setClimate(CropClimate.HOT);
                    ac.energyStorage().receiveEnergy(10_000, false);
                    AirConditionerBlockEntity.serverTick(level, machine, ac.getBlockState(), ac);
                    helper.assertTrue(ac.hasPaidAirflow(), "Shaded electrical heater did not activate");
                }
                for (int distance = 1; distance <= 8; distance++) {
                    BlockPos at = machine.east(distance);
                    CropClimate expected = distance <= 4 ? CropClimate.HOT : distance == 5 ? CropClimate.WARM : CropClimate.NEUTRAL;
                    Object ambient = positional.invoke(null, level, at);
                    Object instrument = gauge.invoke(null, level, at);
                    helper.assertTrue(expected.name().equals(ambient.toString()) && ambient.equals(instrument),
                            "Shaded " + (tan ? "TAN" : "electrical") + " heater skipped a climate at " + distance + ": " + ambient);
                    player.setPos(Vec3.atBottomCenterOf(at));
                    contract.getMethod("setNearbyThermoregulators", Set.class).invoke(data,
                            new HashSet<>(tan ? controllers : Set.<BlockPos>of()));
                    ToughAsNailsGreenhouseGameTests.assertNativePlayerTemperature(helper, api, player, expected);
                }
            }
        } finally {
            level.removeBlock(machine, false);
            for (int distance = 1; distance <= 8; distance++) level.removeBlock(machine.east(distance).above(2), false);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(greenhouses);
            GreenhouseEnvironment.clearCache();
        }
        helper.succeed();
    }

    private static void fillBiome(ServerLevel level, BlockPos center, CropClimate climate) {
        var biome = level.registryAccess().registryOrThrow(Registries.BIOME).holders()
                .filter(holder -> CropClimate.at(holder, center) == climate).findFirst().orElseThrow();
        var sampler = level.getChunkSource().randomState().sampler();
        for (int cx = (center.getX() >> 4) - 1; cx <= (center.getX() >> 4) + 1; cx++) {
            for (int cz = (center.getZ() >> 4) - 1; cz <= (center.getZ() >> 4) + 1; cz++)
                level.getChunk(cx, cz).fillBiomesFromNoise((x, y, z, unused) -> biome, sampler);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickRegulator(T entity) {
        var ticker = entity.getBlockState().getTicker(entity.getLevel(), (BlockEntityType<T>) entity.getType());
        if (ticker == null) throw new IllegalStateException("TAN controller has no server ticker");
        ticker.tick(entity.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity);
    }
}
