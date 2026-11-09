package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

class GreenhouseEnvironmentTest {
    @Test
    void heatRequiresLitFuelAndCoolingHasUsefulStrengths() {
        assertEquals(8, GreenhouseEnvironment.heatStrength(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true)));
        assertEquals(0, GreenhouseEnvironment.heatStrength(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false)));
        assertEquals(8, GreenhouseEnvironment.heatStrength(Blocks.FIRE.defaultBlockState()));
        assertEquals(8, GreenhouseEnvironment.heatStrength(Blocks.LAVA.defaultBlockState()));
        assertEquals(4, GreenhouseEnvironment.heatStrength(Blocks.MAGMA_BLOCK.defaultBlockState()));
        assertEquals(4, GreenhouseEnvironment.coolingStrength(Blocks.ICE.defaultBlockState()));
        assertEquals(8, GreenhouseEnvironment.coolingStrength(Blocks.PACKED_ICE.defaultBlockState()));
        assertEquals(8, GreenhouseEnvironment.coolingStrength(Blocks.BLUE_ICE.defaultBlockState()));
        assertEquals(1, GreenhouseEnvironment.coolingStrength(Blocks.SNOW.defaultBlockState()));
    }

    @Test
    void heatAndCoolingCancelAndOnlyEnclosedHousesCanSpanEveryClimate() {
        assertEquals(CropClimate.NEUTRAL, GreenhouseEnvironment.shifted(CropClimate.NEUTRAL, 16, 16, 2));
        assertEquals(CropClimate.WARM, GreenhouseEnvironment.shifted(CropClimate.NEUTRAL, 8, 0, 2));
        assertEquals(CropClimate.COLD, GreenhouseEnvironment.shifted(CropClimate.NEUTRAL, 0, 8, 2));
        assertEquals(CropClimate.NEUTRAL, GreenhouseEnvironment.shifted(CropClimate.ICY, 128, 0, 2));
        assertEquals(CropClimate.HOT, GreenhouseEnvironment.shifted(CropClimate.ICY, 32, 0, 4));
        assertEquals(CropClimate.ICY, GreenhouseEnvironment.shifted(CropClimate.HOT, 0, 32, 4));
    }

    @Test
    void materialOverridesRejectMalformedAndUnboundedInput() {
        assertTrue(GreenhouseSettings.validateMaterialRule("example:biome=minecraft:stone,example:flower"));
        for (String value : new String[]{"", "plains=stone", "minecraft:plains=", "minecraft:plains=minecraft:",
                "minecraft:plains=minecraft:stone,", "minecraft:plains=#c:stone", "minecraft:plains=" + "minecraft:stone,".repeat(32) + "minecraft:stone"}) {
            assertFalse(GreenhouseSettings.validateMaterialRule(value), value);
        }
    }
}
