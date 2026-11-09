package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class ClimateMachineGameTests {
    private ClimateMachineGameTests() { }

    @GameTest(template = "empty", skyAccess = true, batch = "climate_machines")
    public static void actualNorthstarRegulatorExposesSupportedApiButRequiresRealKineticPower(GameTestHelper helper)
            throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("northstar")) {
            BiomeBasedCrops.LOGGER.info("Skipping Northstar machine contract GameTest: Northstar not installed");
            helper.succeed();
            return;
        }
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        try {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(true);
            Block regulator = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("northstar:temperature_regulator"))
                    .orElseThrow(() -> new IllegalStateException("Installed Northstar is missing its temperature regulator"));
            BlockPos source = helper.absolutePos(new BlockPos(6, 2, 6));
            var level = helper.getLevel();
            level.setBlock(source, regulator.defaultBlockState(), Block.UPDATE_ALL);
            var unit = level.getBlockEntity(source);
            helper.assertTrue(unit != null, "Northstar regulator did not create its actual block entity");
            var sealed = unit.getClass().getMethod("isSealed", Vec3i.class);
            var temperature = unit.getClass().getMethod("getTemperature");
            var speed = unit.getClass().getMethod("getSpeed");
            helper.assertTrue(sealed.getReturnType() == boolean.class && temperature.getReturnType() == float.class,
                    "Installed Northstar does not expose the verified climate provider API");
            float rpm = ((Number) speed.invoke(unit)).floatValue();
            float actualTemperature = ((Number) temperature.invoke(unit)).floatValue();
            helper.assertTrue(rpm == 0, "Unconnected regulator unexpectedly had kinetic power");
            helper.assertTrue(Float.isFinite(actualTemperature), "Northstar returned a non-finite temperature");
            helper.assertTrue(ThermoregulatorDetector.northstarClimate(20F) == CropClimate.NEUTRAL,
                    "Northstar's ambient 20 C reference did not map to neutral");
            BlockPos crop = source.east();
            helper.assertFalse((Boolean) sealed.invoke(unit, crop), "Unpowered regulator claimed an active sealed volume");
            for (CropClimate climate : CropClimate.values()) {
                helper.assertFalse(ThermoregulatorDetector.supportsClimateAt(level, source, level.getBlockState(source), crop, climate),
                        "Adapter accepted an unpowered Northstar regulator for " + climate);
            }
            BiomeBasedCrops.LOGGER.info("Verified real Northstar regulator API: {} C target, {} RPM; unpowered provider rejected",
                    actualTemperature, rpm);
            helper.succeed();
        } finally {
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            GreenhouseEnvironment.invalidateClimate();
        }
    }
}
