package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class ToughAsNailsGreenhouseGameTests {
    private ToughAsNailsGreenhouseGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "player_temperature_readout")
    public static void heldThermometersReadThePlayerWhenClickingDifferentClimate(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos machine = helper.absolutePos(new BlockPos(8, 80, 8));
        BlockPos playerPos = machine.north(6);
        BlockPos clicked = machine.north(3).below();
        level.getChunkAt(playerPos);
        level.getChunkAt(clicked);
        level.setBlock(clicked, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        var unit = (AirConditionerBlockEntity) level.getBlockEntity(machine);
        unit.setClimate(CropClimate.HOT);
        unit.energyStorage().receiveEnergy(3600, false);
        AirConditionerBlockEntity.serverTick(level, machine, level.getBlockState(machine), unit);
        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, clicked.above()) == CropClimate.HOT,
                "Fixture must have a controlled Hot clicked location");
        Component[] captured = new Component[1];
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "thermo-location")) {
            @Override public void displayClientMessage(Component message, boolean overlay) { captured[0] = message; }
        };
        player.setPos(Vec3.atBottomCenterOf(playerPos));
        helper.assertTrue(GreenhouseEnvironment.currentClimate(level, playerPos) != CropClimate.HOT,
                "Fixture player must be outside controller coverage");
        ThermometerItem.showReading(player);
        Component expected = captured[0];
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked).relative(Direction.UP, .5), Direction.UP, clicked, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GreenhouseContent.THERMOMETER.get()));
        captured[0] = null;
        var own = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, clicked, hit);
        TemperatureInstrumentCompatibility.onUseBlock(own);
        helper.assertTrue(own.isCanceled() && expected.equals(captured[0]), "Held thermometer did not read the player's climate");
        captured[0] = null;
        GreenhouseContent.THERMOMETER.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(expected.equals(captured[0]), "Block use sampled the clicked climate instead of the player");
        for (String id : new String[] { "toughasnails:thermometer", "northstar:thermometer" }) {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
            if (item.isEmpty()) continue;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item.get()));
            captured[0] = null;
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, clicked, hit);
            TemperatureInstrumentCompatibility.onUseBlock(event);
            helper.assertTrue(event.isCanceled() && expected.equals(captured[0]), id + " did not use the player's climate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "shared_temperature_readout")
    public static void nativeTanThermometerUsesTheSameReading(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        Component[] captured = new Component[1];
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "instrument-test")) {
            @Override public void displayClientMessage(Component message, boolean overlay) { captured[0] = message; }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("toughasnails:thermometer"))));
        ThermometerItem.showReading(player);
        Component expected = captured[0];
        captured[0] = null;
        var interaction = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        TemperatureInstrumentCompatibility.onUseItem(interaction);
        helper.assertTrue(interaction.isCanceled() && expected.equals(captured[0]), "Optional thermometer did not show shared climate and oxygen");
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "tan_instrument_readings")
    public static void instrumentsFollowFueledTanRegulatorCategories(GameTestHelper helper) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        int radius = Config.THERMOREGULATOR_RADIUS.get();
        try {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Config.THERMOREGULATOR_RADIUS.set(16);
            helper.setBiome(Biomes.PLAINS);
            var level = helper.getLevel();
            BlockPos machine = helper.absolutePos(new BlockPos(6, 60, 6));
            BlockPos query = machine.east(2);
            level.getChunkAt(machine.east(17));
            Block regulator = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toughasnails:thermoregulator"));
            CropClimate baseline = GreenhouseEnvironment.currentClimate(level, query);
            for (boolean heating : new boolean[] { true, false }) {
                level.removeBlock(machine, false);
                level.setBlock(machine, regulator.defaultBlockState().setValue(BlockStateProperties.ENABLED, true), Block.UPDATE_ALL);
                Container fuel = (Container) level.getBlockEntity(machine);
                fuel.setItem(heating ? 1 : 0, new ItemStack(heating ? Items.COAL : Items.ICE));
                tickRegulator(level.getBlockEntity(machine));
                CropClimate desired = heating ? CropClimate.HOT : CropClimate.ICY;
                helper.assertTrue(ThermoregulatorDetector.nearbyClimate(level, query).orElseThrow() == desired, "Fuel mode did not supply its climate category");
                helper.assertTrue(GreenhouseEnvironment.currentClimate(level, query) == desired, "Instrument ignored the active climate category");
                Object gauge = Class.forName("toughasnails.temperature.TemperatureHelperImpl")
                        .getMethod("getTemperatureAtPosWithoutProximity", Level.class, BlockPos.class).invoke(null, level, query);
                helper.assertTrue(gauge.toString().equals(desired.name()), "Optional gauge did not use the climate category: " + gauge);
                helper.assertTrue(ThermoregulatorDetector.nearbyClimate(level, machine.east(15)).isPresent(), "Controller did not reach across its expanded radius");
                helper.assertTrue(ThermoregulatorDetector.nearbyClimate(level, machine.east(17)).isEmpty(), "Controller exceeded its climate radius");
            }
            level.setBlock(machine.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GreenhouseEnvironment.currentClimate(level, query) == baseline, "Disabled regulator still changes the climate");
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            helper.assertTrue(ThermoregulatorDetector.nearbyClimate(level, query).isEmpty(), "Disabled compatibility still reads external controllers");
        } finally {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            Config.THERMOREGULATOR_RADIUS.set(radius);
            GreenhouseEnvironment.clearCache();
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickRegulator(T entity) {
        var level = entity.getLevel();
        var ticker = entity.getBlockState().getTicker(level, (BlockEntityType<T>) entity.getType());
        if (ticker == null) throw new IllegalStateException("Optional controller has no server ticker");
        ticker.tick(level, entity.getBlockPos(), entity.getBlockState(), entity);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "tan_room_temperature")
    public static void allFiveRoomClimatesReachOptionalGaugeAndPlayer(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toughasnails")) { helper.succeed(); return; }
        int budget = GreenhouseSettings.SCAN_BLOCKS_PER_TICK.get();
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        try {
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(16384);
            helper.setBiome(Biomes.PLAINS);
            var level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
            for (BlockPos at : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 3, 3))) {
                boolean floor = at.getY() == center.getY() - 1;
                boolean roof = at.getY() == center.getY() + 3;
                boolean wall = Math.abs(at.getX() - center.getX()) == 3 || Math.abs(at.getZ() - center.getZ()) == 3;
                level.setBlock(at, floor || wall ? Blocks.BRICKS.defaultBlockState()
                        : roof ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockPos machine = center.north();
            level.setBlock(machine, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
            BlockPos gauge = center.east();
            Block gaugeBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toughasnails:temperature_gauge"));
            level.setBlock(gauge, gaugeBlock.defaultBlockState(), Block.UPDATE_ALL);
            Class<?> api = Class.forName("toughasnails.api.temperature.TemperatureHelper");
            Method positional = api.getMethod("getTemperatureAtPos", Level.class, BlockPos.class);
            Method gaugeQuery = Class.forName("toughasnails.temperature.TemperatureHelperImpl")
                    .getMethod("getTemperatureAtPosWithoutProximity", Level.class, BlockPos.class);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            Object baseline = positional.invoke(null, level, center);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
            var air = GreenhouseEnvironment.controllerAirState(level, machine, true);
            helper.assertTrue(air != null, "Glass-roof room scan did not complete");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(Vec3.atBottomCenterOf(center));
            for (CropClimate desired : CropClimate.values()) {
                air.addPaidDrive(machine.asLong(), level.getGameTime(), desired, true);
                helper.assertTrue(GreenhouseEnvironment.currentClimate(level, center) == desired, "Room did not acquire selected climate");
                Object gaugeClimate = gaugeQuery.invoke(null, level, gauge);
                Object playerAmbient = positional.invoke(null, level, center);
                helper.assertTrue(desired.name().equals(gaugeClimate.toString()), "Gauge ignored " + desired + ": " + gaugeClimate);
                helper.assertTrue(desired.name().equals(playerAmbient.toString()), "Player ignored " + desired + ": " + playerAmbient);
                boolean hotMode = desired == CropClimate.WARM || desired == CropClimate.HOT;
                level.setBlock(gauge, level.getBlockState(gauge).setValue(BlockStateProperties.INVERTED, !hotMode), Block.UPDATE_ALL);
                level.getBlockState(gauge).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(gauge), Direction.UP, gauge, false));
                int signal = desired == CropClimate.NEUTRAL ? 0 : desired == CropClimate.COLD || desired == CropClimate.WARM ? 7 : 15;
                helper.assertValueEqual(level.getBlockState(gauge).getValue(BlockStateProperties.POWER), signal, "Gauge signal for " + desired);
                assertNativePlayerTemperature(helper, api, player, desired);
            }
            GreenhouseSettings.ENABLE_GREENHOUSES.set(false);
            helper.assertTrue(baseline.equals(positional.invoke(null, level, center)), "Disabled greenhouse still changes native climate");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not exercise optional climate API", error);
        } finally {
            GreenhouseSettings.SCAN_BLOCKS_PER_TICK.set(budget);
            GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled);
            GreenhouseEnvironment.clearCache();
        }
        helper.succeed();
    }

    static void assertNativePlayerTemperature(GameTestHelper helper, Class<?> api, Player player, CropClimate desired)
            throws ReflectiveOperationException {
        Object config = Class.forName("toughasnails.init.ModConfig").getField("temperature").get(null);
        var enabledField = config.getClass().getField("enableTemperature");
        boolean enabled = enabledField.getBoolean(config);
        try {
            enabledField.setBoolean(config, true);
            Object data = api.getMethod("getTemperatureData", Player.class).invoke(null, player);
            Class<?> contract = Class.forName("toughasnails.api.temperature.ITemperature");
            contract.getMethod("setDryTicks", int.class).invoke(data, 10000);
            Method tick = Class.forName("toughasnails.temperature.TemperatureHandler").getMethod("onPlayerTick", Player.class);
            // Advance the native body simulation without waiting through the mod's long configured delays.
            for (int i = 0; i < 7; i++) {
                contract.getMethod("setChangeDelayTicks", int.class).invoke(data, 0);
                tick.invoke(null, player);
            }
            Object target = contract.getMethod("getTargetLevel").invoke(data);
            Object body = api.getMethod("getTemperatureForPlayer", Player.class).invoke(null, player);
            helper.assertTrue(desired.name().equals(target.toString()), "TAN player target ignored the room: " + target);
            helper.assertTrue(desired.name().equals(body.toString()), "TAN body temperature did not respond to the room: " + body);
        } finally {
            enabledField.setBoolean(config, enabled);
        }
    }
}
