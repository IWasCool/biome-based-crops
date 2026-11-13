package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual block entity serializer across the 1.21 NBT API transition. */
@ExtendWith(EphemeralTestServerProvider.class)
public class ControllerStateSmokeTest {
    @Test
    void controllerStateSurvivesSaveAndLoad(MinecraftServer server) throws ReflectiveOperationException {
        AirConditionerBlockEntity original = newController();
        original.setClimate(CropClimate.HOT);
        original.setEnabled(false);
        CompoundTag saved = original.getUpdateTag(server.registryAccess());
        // Seed an existing-world energy balance, then verify it survives both load and save.
        saved.putInt("Energy", 4321);

        AirConditionerBlockEntity restored = newController();
        load(restored, saved, server);
        assertState(restored);

        AirConditionerBlockEntity reloaded = newController();
        load(reloaded, restored.getUpdateTag(server.registryAccess()), server);
        assertState(reloaded);
    }

    private static AirConditionerBlockEntity newController() {
        return new AirConditionerBlockEntity(BlockPos.ZERO, GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState());
    }

    private static void assertState(AirConditionerBlockEntity controller) {
        assertEquals(CropClimate.HOT, controller.selectedClimate());
        assertFalse(controller.enabled());
        assertEquals(4321, controller.energyStored());
    }

    private static void load(AirConditionerBlockEntity controller, CompoundTag saved, MinecraftServer server)
            throws ReflectiveOperationException {
        for (Method method : controller.getClass().getMethods()) {
            if (!method.getName().equals("loadWithComponents")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == CompoundTag.class) {
                method.invoke(controller, saved, server.registryAccess());
                return;
            }
            if (parameters.length == 1 && parameters[0].getName().equals("net.minecraft.world.level.storage.ValueInput")) {
                Class<?> inputClass = Class.forName("net.minecraft.world.level.storage.TagValueInput");
                Class<?> reporterClass = Class.forName("net.minecraft.util.ProblemReporter");
                Object reporter = reporterClass.getField("DISCARDING").get(null);
                for (Method factory : inputClass.getMethods()) {
                    if (factory.getName().equals("create") && Modifier.isStatic(factory.getModifiers())
                            && factory.getParameterCount() == 3 && factory.getParameterTypes()[2] == CompoundTag.class) {
                        Object input = factory.invoke(null, reporter, server.registryAccess(), saved);
                        method.invoke(controller, input);
                        return;
                    }
                }
                fail("No supported TagValueInput factory on this Minecraft target");
            }
        }
        fail("No supported block entity loader on this Minecraft target");
    }
}
