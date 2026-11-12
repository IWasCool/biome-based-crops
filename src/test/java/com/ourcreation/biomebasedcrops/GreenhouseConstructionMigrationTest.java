package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class GreenhouseConstructionMigrationTest {
    @Test
    void originalFactoryGlassRatiosUpgradeToGlassRoofOnce(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(0);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.5D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.1D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(1.0D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.0D, GreenhouseSettings.MIN_WALL_GLASS_RATIO.get());
            assertEquals(0.0D, GreenhouseSettings.wallGlassRatio());
            assertEquals(4, GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get());
            assertFalse(GreenhouseSettings.migrateConstructionRules());

            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.5D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.1D);
            assertFalse(GreenhouseSettings.migrateConstructionRules());
            assertEquals(0.5D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.0D, GreenhouseSettings.wallGlassRatio());
        });
    }

    @Test
    void fullGlassFactoryDefaultsKeepTheirRoofAndDropWallQuotas(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(1);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(1.0D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(1.0D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(1.0D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.0D, GreenhouseSettings.MIN_WALL_GLASS_RATIO.get());
            assertEquals(0.0D, GreenhouseSettings.wallGlassRatio());
            assertEquals(4, GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get());
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(1.0D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(1.0D);
            assertFalse(GreenhouseSettings.migrateConstructionRules());
            assertEquals(1.0D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.0D, GreenhouseSettings.wallGlassRatio());
        });
    }

    @Test
    void sixtyPercentFactoryDefaultsUpgradeExistingVersionTwoConfigs(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(2);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.6D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.6D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(1.0D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.0D, GreenhouseSettings.MIN_WALL_GLASS_RATIO.get());
            assertEquals(4, GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get());
            assertFalse(GreenhouseSettings.migrateConstructionRules());
        });
    }

    @Test
    void customizedRoofRatiosSurviveButLegacyWallSettingsAreIgnored(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(0);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.85D);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(0.75D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(0.85D, GreenhouseSettings.roofGlassRatio());
            assertEquals(0.0D, GreenhouseSettings.MIN_WALL_GLASS_RATIO.get());
            assertEquals(0.0D, GreenhouseSettings.wallGlassRatio());
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(1.0D);
            assertEquals(0.0D, GreenhouseSettings.wallGlassRatio());
            assertFalse(GreenhouseSettings.migrateConstructionRules());
        });
    }

    @Test
    void previousVolumeDefaultUpgradesWithoutChangingCustomizedRoof(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(3);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(262144);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(0.85D);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(1048576, GreenhouseSettings.maxRoomBlocks());
            assertEquals(0.85D, GreenhouseSettings.roofGlassRatio());
            assertEquals(4, GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get());
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(262144);
            assertFalse(GreenhouseSettings.migrateConstructionRules());
            assertEquals(262144, GreenhouseSettings.maxRoomBlocks());
        });
    }

    @Test
    void customizedVolumeLimitSurvivesCapacityMigration(MinecraftServer server) {
        withSettings(() -> {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(3);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(65536);
            assertTrue(GreenhouseSettings.migrateConstructionRules());
            assertEquals(65536, GreenhouseSettings.maxRoomBlocks());
        });
    }

    private static void withSettings(Runnable test) {
        int version = GreenhouseSettings.CONSTRUCTION_RULES_VERSION.get();
        int volume = GreenhouseSettings.MAX_ROOM_BLOCKS.get();
        double roof = GreenhouseSettings.MIN_ROOF_GLASS_RATIO.get();
        double walls = GreenhouseSettings.MIN_WALL_GLASS_RATIO.get();
        try {
            test.run();
        } finally {
            GreenhouseSettings.CONSTRUCTION_RULES_VERSION.set(version);
            GreenhouseSettings.MAX_ROOM_BLOCKS.set(volume);
            GreenhouseSettings.MIN_ROOF_GLASS_RATIO.set(roof);
            GreenhouseSettings.MIN_WALL_GLASS_RATIO.set(walls);
        }
    }
}
