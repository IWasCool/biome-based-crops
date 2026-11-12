package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class ClimateSettingsMigrationTest {
    @Test
    void oldFactoryCoverageUpgradesOnceAndCustomCoverageSurvives(MinecraftServer server) {
        int radius = Config.THERMOREGULATOR_RADIUS.get();
        int version = Config.CLIMATE_RULES_VERSION.get();
        try {
            Config.CLIMATE_RULES_VERSION.set(0);
            Config.THERMOREGULATOR_RADIUS.set(4);
            assertTrue(Config.migrateClimateRules());
            assertEquals(16, Config.THERMOREGULATOR_RADIUS.get());
            Config.THERMOREGULATOR_RADIUS.set(4);
            assertFalse(Config.migrateClimateRules());
            assertEquals(4, Config.THERMOREGULATOR_RADIUS.get());
            Config.CLIMATE_RULES_VERSION.set(0);
            Config.THERMOREGULATOR_RADIUS.set(24);
            assertTrue(Config.migrateClimateRules());
            assertEquals(24, Config.THERMOREGULATOR_RADIUS.get());
        } finally {
            Config.THERMOREGULATOR_RADIUS.set(radius);
            Config.CLIMATE_RULES_VERSION.set(version);
        }
    }
}
