package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class ThermoregulatorDetectorTest {
    @Test
    void inactiveRegulatorsDoNotProvideFreeNeutralClimates() {
        assertSupportedClimates(true, false, false, Set.of());
    }

    @Test
    void redstoneDisabledRegulatorsCannotSupportAnyClimate() {
        for (boolean cooling : new boolean[]{false, true}) {
            for (boolean heating : new boolean[]{false, true}) {
                assertSupportedClimates(false, cooling, heating, Set.of());
            }
        }
    }

    @Test
    void coolingSupportsColdAndNeutralCrops() {
        assertSupportedClimates(true, true, false,
                Set.of(CropClimate.ICY, CropClimate.COLD, CropClimate.NEUTRAL));
    }

    @Test
    void heatingSupportsWarmAndNeutralCrops() {
        assertSupportedClimates(true, false, true,
                Set.of(CropClimate.NEUTRAL, CropClimate.WARM, CropClimate.HOT));
    }

    @Test
    void bothFuelsNeutralizeInsteadOfUnlockingEveryClimate() {
        assertSupportedClimates(true, true, true, Set.of(CropClimate.NEUTRAL));
    }

    @Test
    void profilesCanMatchAnyOfTheirAllowedClimates() {
        assertTrue(ThermoregulatorDetector.supportsClimates(true, true, false,
                Set.of(CropClimate.COLD, CropClimate.HOT)));
        assertTrue(ThermoregulatorDetector.supportsClimates(true, false, true,
                Set.of(CropClimate.COLD, CropClimate.HOT)));
        assertFalse(ThermoregulatorDetector.supportsClimates(true, true, true,
                Set.of(CropClimate.COLD, CropClimate.HOT)));
    }

    @Test
    void unknownStatesAndEmptyProfilesDoNotProvideArtificialClimates() {
        assertFalse(ThermoregulatorDetector.supportsClimates(Blocks.STONE.defaultBlockState(),
                Set.of(CropClimate.NEUTRAL)));
        assertFalse(ThermoregulatorDetector.supportsClimates(true, true, true, Set.of()));
    }

    private static void assertSupportedClimates(boolean enabled, boolean cooling, boolean heating, Set<CropClimate> expected) {
        for (CropClimate climate : CropClimate.values()) {
            assertEquals(expected.contains(climate),
                    ThermoregulatorDetector.supportsClimates(enabled, cooling, heating, Set.of(climate)),
                    () -> "enabled=" + enabled + ", cooling=" + cooling + ", heating=" + heating
                            + " should support " + expected + ", checked " + climate);
        }
    }
}
