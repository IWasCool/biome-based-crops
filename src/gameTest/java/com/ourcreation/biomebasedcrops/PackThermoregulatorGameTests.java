package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Field;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the installed pack's fuel tags and TAN ticker without a compile-time TAN dependency. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class PackThermoregulatorGameTests {
    private static final BlockPos MACHINE = new BlockPos(6, 2, 6);

    private PackThermoregulatorGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_thermoregulators")
    public static void coreCoolingFuelWorksWithPlayerTemperatureDisabled(GameTestHelper helper) {
        withPackTemperatureDisabled(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos machine = placeMachine(helper);
            Container inventory = inventory(helper, machine);
            putFuel(helper, inventory, 0, Items.BREEZE_ROD);
            tickMachine(helper, machine);

            helper.assertTrue(inventory.getItem(0).isEmpty(), "Core's breeze-rod cooling fuel was not consumed");
            helper.assertTrue(mode(level.getBlockState(machine), "cooling"), "Cooling did not activate with player temperature disabled");
            assertSupports(helper, machine, CropClimate.COLD, true);
            assertSupports(helper, machine, CropClimate.HOT, false);

            // TAN burns the consumed item through its timer, even with an empty input slot.
            tickMachine(helper, machine);
            helper.assertTrue(inventory.getItem(0).isEmpty(), "Cooling control unexpectedly acquired fuel");
            assertSupports(helper, machine, CropClimate.ICY, true);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_thermoregulators")
    public static void coreHeatingAndDualFuelModesFollowActualTicker(GameTestHelper helper) {
        withPackTemperatureDisabled(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos machine = placeMachine(helper);
            Container inventory = inventory(helper, machine);
            putFuel(helper, inventory, 1, Items.MAGMA_CREAM);
            tickMachine(helper, machine);

            helper.assertTrue(inventory.getItem(1).isEmpty(), "Core's magma-cream heating fuel was not consumed");
            helper.assertTrue(mode(level.getBlockState(machine), "heating"), "Heating did not activate with player temperature disabled");
            assertSupports(helper, machine, CropClimate.HOT, true);
            assertSupports(helper, machine, CropClimate.COLD, false);

            putFuel(helper, inventory, 0, Items.BREEZE_ROD);
            tickMachine(helper, machine);
            helper.assertTrue(mode(level.getBlockState(machine), "cooling") && mode(level.getBlockState(machine), "heating"),
                    "Supplying both core fuel types did not activate both TAN timers");
            assertSupports(helper, machine, CropClimate.NEUTRAL, true);
            assertSupports(helper, machine, CropClimate.COLD, false);
            assertSupports(helper, machine, CropClimate.HOT, false);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_thermoregulators")
    public static void redstoneStopsFuelConsumptionAndCropSupport(GameTestHelper helper) {
        withPackTemperatureDisabled(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos machine = placeMachine(helper);
            Container inventory = inventory(helper, machine);
            putFuel(helper, inventory, 0, Items.BREEZE_ROD);
            level.setBlock(machine.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(level.getBlockState(machine).getValue(BlockStateProperties.ENABLED),
                    "Redstone did not disable the actual TAN block");
            tickMachine(helper, machine);
            helper.assertValueEqual(inventory.getItem(0).getCount(), 1, "fuel remaining in powered-off regulator");
            assertSupports(helper, machine, CropClimate.COLD, false);
            assertSupports(helper, machine, CropClimate.NEUTRAL, false);

            level.removeBlock(machine.below(), false);
            helper.assertTrue(level.getBlockState(machine).getValue(BlockStateProperties.ENABLED),
                    "Removing redstone did not re-enable the regulator");
            tickMachine(helper, machine);
            assertSupports(helper, machine, CropClimate.COLD, true);
        });
    }

    private static BlockPos placeMachine(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(MACHINE);
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("toughasnails", "thermoregulator"));
        helper.getLevel().setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.ENABLED, true), Block.UPDATE_ALL);
        return pos;
    }

    private static Container inventory(GameTestHelper helper, BlockPos pos) {
        if (helper.getLevel().getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw new IllegalStateException("Installed TAN thermoregulator has no container at " + pos);
    }

    private static void putFuel(GameTestHelper helper, Container inventory, int slot, Item item) {
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(inventory.canPlaceItem(slot, stack), "Installed pack did not accept expected core regulator fuel " + stack);
        inventory.setItem(slot, stack);
    }

    private static boolean mode(BlockState state, String name) {
        if (state.getBlock().getStateDefinition().getProperty(name) instanceof BooleanProperty property) {
            return state.getValue(property);
        }
        throw new IllegalStateException("Installed TAN thermoregulator lacks mode " + name);
    }

    private static void assertSupports(GameTestHelper helper, BlockPos machine, CropClimate climate, boolean expected) {
        boolean actual = ThermoregulatorDetector.canSupportProfile(helper.getLevel(), machine.east(2),
                new CropProfile(Set.of(climate)));
        helper.assertTrue(actual == expected, "Actual regulator ticker mode incorrectly supports " + climate + "; expected " + expected);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickMachine(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        T entity = (T) level.getBlockEntity(pos);
        if (entity == null) {
            throw new IllegalStateException("Missing thermoregulator block entity at " + pos);
        }
        BlockState state = level.getBlockState(pos);
        BlockEntityTicker<T> ticker = state.getTicker(level, (BlockEntityType<T>) entity.getType());
        helper.assertTrue(ticker != null, "TAN disabled its regulator ticker when player temperature was disabled");
        ticker.tick(level, pos, state, entity);
    }

    private static void withPackTemperatureDisabled(GameTestHelper helper, Runnable test) {
        if (!ModList.get().isLoaded("toughasnails") || !ModList.get().isLoaded("create_new_world_core")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional pack thermoregulator test: Tough As Nails and Create New World Core are required");
            helper.succeed();
            return;
        }

        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        int radius = Config.THERMOREGULATOR_RADIUS.get();
        try {
            Object tanConfig = Class.forName("toughasnails.init.ModConfig").getField("temperature").get(null);
            Field enabledField = tanConfig.getClass().getField("enableTemperature");
            boolean playerTemperature = enabledField.getBoolean(tanConfig);
            try {
                Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
                Config.THERMOREGULATOR_RADIUS.set(4);
                enabledField.setBoolean(tanConfig, false);
                helper.assertFalse(enabledField.getBoolean(tanConfig), "TAN player temperature control remained enabled");
                test.run();
            } finally {
                enabledField.setBoolean(tanConfig, playerTemperature);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot control the installed TAN player-temperature flag for this compatibility test", exception);
        } finally {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            Config.THERMOREGULATOR_RADIUS.set(radius);
        }
        helper.succeed();
    }
}
