package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** Loads the real mod and datapacks in NeoForge's disposable, network-free test server. */
@ExtendWith(EphemeralTestServerProvider.class)
public class CompatibilitySmokeTest {
    @Test
    void itemsAndControllerRegister(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        assertEquals(id("farmer_field_guide"), BuiltInRegistries.ITEM.getKey(GreenhouseContent.GUIDE.get()));
        assertEquals(id("thermometer"), BuiltInRegistries.ITEM.getKey(GreenhouseContent.THERMOMETER.get()));
        assertEquals(id("air_conditioner"), BuiltInRegistries.ITEM.getKey(GreenhouseContent.AIR_CONDITIONER_ITEM.get()));
        var block = GreenhouseContent.AIR_CONDITIONER.get();
        var unit = block.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        assertInstanceOf(AirConditionerBlockEntity.class, unit);
        assertSame(GreenhouseContent.AIR_CONDITIONER_ENTITY.get(), unit.getType());
        assertNotNull(AirConditionerMenu.TYPE.get());
    }

    @Test
    void craftingRecipesLoad(MinecraftServer server) throws ReflectiveOperationException {
        // Recipe keys changed from ResourceLocation to ResourceKey during 1.21.
        // Query the real manager using the signature of this target, not a duplicate JSON parser.
        Object manager = server.getRecipeManager();
        Method lookup = null;
        for (Method candidate : manager.getClass().getMethods()) {
            if (candidate.getName().equals("byKey") && candidate.getParameterCount() == 1
                    && (candidate.getParameterTypes()[0] == ResourceLocation.class
                    || candidate.getParameterTypes()[0] == ResourceKey.class)) {
                lookup = candidate;
                break;
            }
        }
        assertNotNull(lookup, "No supported recipe lookup on this Minecraft target");
        for (String path : new String[]{"farmer_field_guide", "thermometer", "air_conditioner"}) {
            Object key = lookup.getParameterTypes()[0] == ResourceLocation.class
                    ? id(path) : ResourceKey.create(Registries.RECIPE, id(path));
            Object result = lookup.invoke(manager, key);
            assertTrue(result instanceof Optional<?> optional && optional.isPresent(), "Missing crafting recipe: " + path);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, path);
    }
}
