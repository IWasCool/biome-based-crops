package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GreenhouseEnvironmentTest {
    @Test
    void materialOverridesRejectMalformedAndUnboundedInput() {
        assertTrue(GreenhouseSettings.validateMaterialRule("example:biome=minecraft:stone,example:flower"));
        for (String value : new String[]{"", "plains=stone", "minecraft:plains=", "minecraft:plains=minecraft:",
                "minecraft:plains=minecraft:stone,", "minecraft:plains=#c:stone", "minecraft:plains=" + "minecraft:stone,".repeat(32) + "minecraft:stone"}) {
            assertFalse(GreenhouseSettings.validateMaterialRule(value), value);
        }
    }
}
