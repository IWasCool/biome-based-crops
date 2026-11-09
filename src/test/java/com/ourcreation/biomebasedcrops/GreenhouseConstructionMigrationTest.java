package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class GreenhouseConstructionMigrationTest {
    @Test
    void previousFactoryGlassRatiosUpgradeOnceAndLaterCustomChoicesSurvive(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(0);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.5D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.1D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(1.0D, GreenhouseSettings.roofGlassRatio());
            assertEquals(1.0D, GreenhouseSettings.wallGlassRatio());
            assertEquals(1, GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get());
            assertFalse(GreenhouseSettings.migrateConstructionRules());

            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.5D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.1D);
            assertFalse(GreenhouseSettings.migrateConstructionRules());
            assertEquals(0.5D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.1D, GreenhouseSettings.wallGlassRatio());
        });
    }

    @Test
    void existingNondefaultConstructionRulesStayUnderOperatorControl(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(0);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.85D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.75D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(0.85D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.75D, GreenhouseSettings.wallGlassRatio());
            assertFalse(GreenhouseSettings.migrateConstructionRules());
        });
    }

    private static void withSettings(Runnable test) {
        int version = GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get();
        double roof = GreenhouseSettings.MIN_ROOF_GLASS_RATIO.get();
        double walls = GreenhouseSettings.MIN_WALL_GLASS_RATIO.get();
        try {
            test.run();
        } finally {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(version);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(roof);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(walls);
        }
    }
}
