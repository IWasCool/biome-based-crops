package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class AirConditionerGameTests {
    private static final BlockPos UNIT = new BlockPos(6, 2, 6);

    private AirConditionerGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void energyCapabilityAcceptsEveryFaceAndCannotExtract(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        ServerLevel level = helper.getLevel();
        for (Direction face : Direction.values()) {
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, unit.getBlockPos(), face);
            helper.assertTrue(energy != null, "Missing FE capability on " + face);
            helper.assertTrue(energy.canReceive() && !energy.canExtract(), "FE direction contract on " + face);
            int before = unit.energyStored();
            helper.assertValueEqual(energy.receiveEnergy(20, true), 20, "simulated input on " + face);
            helper.assertValueEqual(unit.energyStored(), before, "simulation changed battery on " + face);
            helper.assertValueEqual(energy.receiveEnergy(20, false), 20, "real input on " + face);
            helper.assertValueEqual(unit.energyStored(), before + 20, "face did not share the same battery on " + face);
            helper.assertValueEqual(energy.extractEnergy(20, false), 0, "machine allowed FE extraction on " + face);
        }
        while (unit.energyStored() < AirConditionerBlockEntity.CAPACITY) {
            helper.assertTrue(unit.energyStorage().receiveEnergy(Integer.MAX_VALUE, false) > 0, "Battery stopped accepting before full");
        }
        helper.assertValueEqual(unit.energyStored(), AirConditionerBlockEntity.CAPACITY, "battery capacity");
        helper.assertValueEqual(unit.energyStorage().receiveEnergy(100, false), 0, "overfilling input");
        helper.assertValueEqual(unit.energyStorage().receiveEnergy(-1, false), 0, "negative FE input");
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void powerCostTracksActualLocalClimateAcrossAllTargetDistances(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        helper.setBiome(Biomes.PLAINS);
        helper.assertTrue(CropClimate.at(GreenhouseEnvironment.nativeBiome(helper.getLevel(), unit.getBlockPos()), unit.getBlockPos())
                == CropClimate.NEUTRAL, "Plains fixture was not naturally neutral");
        assertCost(helper, unit, CropClimate.NEUTRAL, 0);
        assertCost(helper, unit, CropClimate.WARM, 2500);
        assertCost(helper, unit, CropClimate.HOT, 3250);
        assertCost(helper, unit, CropClimate.COLD, 2750);
        assertCost(helper, unit, CropClimate.ICY, 3500);
        unit.setTargetTemperature(21);
        helper.assertValueEqual(unit.currentEnergyCost(), 2050, "same-band single-degree power cost");

        helper.setBiome(Biomes.ICE_SPIKES);
        helper.assertTrue(CropClimate.at(GreenhouseEnvironment.nativeBiome(helper.getLevel(), unit.getBlockPos()), unit.getBlockPos())
                == CropClimate.ICY, "Ice-spikes fixture was not naturally icy");
        assertCost(helper, unit, CropClimate.ICY, 0);
        assertCost(helper, unit, CropClimate.COLD, 2750);
        assertCost(helper, unit, CropClimate.NEUTRAL, 3500);
        assertCost(helper, unit, CropClimate.HOT, 4750);

        helper.setBiome(Biomes.DESERT);
        helper.assertTrue(CropClimate.at(GreenhouseEnvironment.nativeBiome(helper.getLevel(), unit.getBlockPos()), unit.getBlockPos())
                == CropClimate.HOT, "Desert fixture was not naturally hot");
        assertCost(helper, unit, CropClimate.HOT, 0);
        assertCost(helper, unit, CropClimate.WARM, 2750);
        assertCost(helper, unit, CropClimate.NEUTRAL, 3250);
        assertCost(helper, unit, CropClimate.ICY, 4750);
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void serverTicksConsumePowerAndStopWhenStarvedRedstoneOrNaturallyMatched(GameTestHelper helper) {
        boolean enabled = GreenhouseSettings.ENABLE_GREENHOUSES.get();
        Runnable restore = () -> { GreenhouseSettings.ENABLE_GREENHOUSES.set(enabled); GreenhouseEnvironment.invalidateClimate(); };
        GreenhouseSettings.ENABLE_GREENHOUSES.set(true);
        AirConditionerBlockEntity unit = placeInRoom(helper);
        unit.setClimate(CropClimate.WARM);
        int cost = unit.currentEnergyCost();
        unit.energyStorage().receiveEnergy(cost * 2, false);
        BlockPos signal = unit.getBlockPos().east();
        helper.startSequence()
                .thenExecute(checked(() -> {
                    helper.assertValueEqual(cost, 2500, "test unit running cost");
                    tick(unit);
                    helper.assertValueEqual(unit.energyStored(), cost, "first paid tick");
                    tick(unit);
                    helper.assertValueEqual(unit.energyStored(), cost, "duplicate same-game-tick call consumed extra FE");
                    helper.assertTrue(unit.getBlockState().getValue(AirConditionerBlock.ACTIVE), "Paid unit did not animate");
                    helper.assertFalse(unit.canProvideClimate(CropClimate.WARM), "Selected target instantly replaced physical air");
                }, restore))
                .thenIdle(1)
                .thenExecute(checked(() -> {
                    tick(unit);
                    helper.assertValueEqual(unit.energyStored(), 0, "second real paid tick");
                }, restore))
                .thenIdle(1)
                .thenExecute(checked(() -> {
                    tick(unit);
                    helper.assertFalse(unit.getBlockState().getValue(AirConditionerBlock.ACTIVE), "Starved unit kept animating");
                    unit.energyStorage().receiveEnergy(cost * 5, false);
                }, restore))
                .thenIdle(1)
                .thenExecute(checked(() -> {
                    tick(unit);
                    helper.assertValueEqual(unit.energyStored(), cost * 4, "refueled real tick");
                    helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState()
                            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                            .setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
                }, restore))
                .thenIdle(1)
                .thenExecute(checked(() -> {
                    tick(unit);
                    helper.assertValueEqual(unit.energyStored(), cost * 4, "redstone-disabled unit consumed FE");
                    helper.assertFalse(unit.getBlockState().getValue(AirConditionerBlock.ACTIVE), "Redstone-disabled unit kept animating");
                    helper.getLevel().setBlock(signal, helper.getLevel().getBlockState(signal)
                            .setValue(BlockStateProperties.POWERED, false), Block.UPDATE_ALL);
                }, restore))
                .thenIdle(1)
                .thenExecute(checked(() -> {
                    tick(unit);
                    helper.assertValueEqual(unit.energyStored(), cost * 3, "unit did not resume after lever switched off");
                    unit.setTargetTemperature(20);
                    helper.assertTrue(unit.currentTemperature() > 20.1F, "Paid room did not accumulate real warmth");
                    helper.assertValueEqual(unit.currentEnergyCost(), AirConditionerBlockEntity.BASE_FE_PER_TICK,
                            "Cooling stored heat back to the native setpoint must still cost FE");
                    restore.run();
                }, restore))
                .thenSucceed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void targetAndStoredEnergySurviveNbtAndMalformedValuesStayBounded(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        unit.setTargetTemperature(7);
        unit.energyStorage().receiveEnergy(432, false);
        var registries = helper.getLevel().registryAccess();
        var saved = unit.saveWithoutMetadata(registries);
        var restored = new AirConditionerBlockEntity(unit.getBlockPos(), unit.getBlockState());
        restored.loadWithComponents(saved, registries);
        helper.assertTrue(restored.selectedClimate() == CropClimate.COLD, "Saved climate target changed");
        helper.assertValueEqual(restored.targetTemperature(), 7, "Numeric target was rounded to a climate preset");
        helper.assertValueEqual(restored.energyStored(), 432, "saved FE battery");
        saved.putInt("TargetTemperature", 200);
        restored.loadWithComponents(saved, registries);
        helper.assertValueEqual(restored.targetTemperature(), 50, "Saved numeric temperature exceeded the dial range");
        saved.putInt("Energy", Integer.MAX_VALUE);
        saved.remove("TargetTemperature");
        saved.putString("Climate", "unknown_temperature");
        restored.loadWithComponents(saved, registries);
        helper.assertValueEqual(restored.energyStored(), AirConditionerBlockEntity.CAPACITY, "malformed oversized battery");
        helper.assertTrue(restored.selectedClimate() == CropClimate.NEUTRAL, "Malformed target lacked a safe fallback");
        saved.putInt("Energy", -1);
        restored.loadWithComponents(saved, registries);
        helper.assertValueEqual(restored.energyStored(), 0, "malformed negative battery");
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "air_conditioners")
    public static void menuRejectsInvalidTargetsAndDistanceAndTracksAuthoritativeData(GameTestHelper helper) {
        AirConditionerBlockEntity unit = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = unit.getBlockPos();
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        var menu = new AirConditionerMenu(1, player.getInventory(), unit);
        helper.assertTrue(menu.stillValid(player), "Nearby menu was invalid");
        helper.assertFalse(menu.clickMenuButton(player, -1), "Menu accepted negative target");
        helper.assertFalse(menu.clickMenuButton(player, 71), "Menu accepted out-of-range target");
        helper.assertTrue(menu.clickMenuButton(player, 65), "Menu rejected a valid target");
        helper.assertTrue(unit.selectedClimate() == CropClimate.HOT && menu.targetClimate() == CropClimate.HOT, "Menu target did not reach machine");
        helper.assertValueEqual(menu.energyCost(), 3250, "menu dynamic power cost");
        helper.assertTrue(menu.localClimate() == CropClimate.NEUTRAL, "Menu showed a non-native local climate");
        unit.energyStorage().receiveEnergy(123456, false);
        helper.assertValueEqual(menu.energyStored(), 123456, "menu 32-bit live energy count");
        player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        helper.assertFalse(menu.stillValid(player), "Distant player kept menu access");
        helper.assertFalse(menu.clickMenuButton(player, 0), "Distant player changed temperature");
        helper.assertTrue(unit.selectedClimate() == CropClimate.HOT, "Rejected distant click changed machine");
        helper.succeed();
    }

    private static Runnable checked(Runnable action, Runnable restore) {
        return () -> { try { action.run(); } catch (RuntimeException | Error exception) { restore.run(); throw exception; } };
    }

    private static AirConditionerBlockEntity placeInRoom(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        BlockPos center = helper.absolutePos(UNIT);
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 3, 2))) {
            boolean floor = at.getY() == center.getY() - 1;
            boolean wall = Math.abs(at.getX() - center.getX()) == 2 || Math.abs(at.getZ() - center.getZ()) == 2
                    || at.getY() == center.getY() + 3;
            helper.getLevel().setBlock(at, floor ? Blocks.STONE.defaultBlockState()
                    : wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.getLevel().setBlock(center, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        GreenhouseEnvironment.clearCache();
        return (AirConditionerBlockEntity) helper.getLevel().getBlockEntity(center);
    }

    private static AirConditionerBlockEntity place(GameTestHelper helper) {
        helper.setBiome(Biomes.PLAINS);
        BlockPos pos = helper.absolutePos(UNIT);
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState(), Block.UPDATE_ALL);
        return (AirConditionerBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void assertCost(GameTestHelper helper, AirConditionerBlockEntity unit, CropClimate target, int expected) {
        unit.setClimate(target);
        helper.assertValueEqual(unit.currentEnergyCost(), expected, "FE/t targeting " + target);
    }

    private static void tick(AirConditionerBlockEntity unit) {
        AirConditionerBlockEntity.serverTick(unit.getLevel(), unit.getBlockPos(), unit.getBlockState(), unit);
    }
}
