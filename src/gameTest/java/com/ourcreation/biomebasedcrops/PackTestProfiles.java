package com.ourcreation.biomebasedcrops;

import java.io.File;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Test servers omit the offline profile cache that OpenPAC expects on normal servers. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID)
public final class PackTestProfiles {
    private PackTestProfiles() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void provideCache(ServerAboutToStartEvent event) throws ReflectiveOperationException {
        MinecraftServer server = event.getServer();
        boolean testServer = server instanceof GameTestServer
                || server.getClass().getName().equals("net.neoforged.testframework.junit.EphemeralTestServerProvider$JUnitServer");
        if (!testServer || server.getProfileCache() != null) {
            return;
        }
        var field = MinecraftServer.class.getDeclaredField("services");
        field.setAccessible(true);
        Services services = (Services) field.get(server);
        GameProfileCache cache = new GameProfileCache(services.profileRepository(), new File("biome-crops-test-usercache.json"));
        cache.setExecutor(server);
        field.set(server, new Services(services.sessionService(), services.servicesKeySet(), services.profileRepository(), cache));
    }
}
