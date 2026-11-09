package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GreenhouseScalingTest {
    @Test
    void smallRoomsKeepBaseCountsAndLargerRoomsRoundEachMaterialUp() {
        assertEquals(8, GreenhouseRecipes.scaledCount(8, 0, 512));
        assertEquals(8, GreenhouseRecipes.scaledCount(8, 1, 512));
        assertEquals(8, GreenhouseRecipes.scaledCount(8, 512, 512));
        assertEquals(9, GreenhouseRecipes.scaledCount(8, 513, 512));
        assertEquals(2, GreenhouseRecipes.scaledCount(1, 513, 512));
        assertEquals(16, GreenhouseRecipes.scaledCount(8, 1024, 512));
        assertEquals(2048, GreenhouseRecipes.scaledCount(8, 131072, 512));
    }

    @Test
    void maximumRoomAndConfigurableReferenceUseSafeIntegerCounts() {
        assertEquals(131072, GreenhouseRecipes.scaledCount(64, 1048576, 512));
        assertEquals(1048576, GreenhouseRecipes.scaledCount(64, 1048576, 64));
        int allMaterialTypes = 0;
        for (int material = 0; material < 32; material++) {
            allMaterialTypes = Math.addExact(allMaterialTypes, GreenhouseRecipes.scaledCount(64, 1048576, 64));
        }
        assertEquals(33554432, allMaterialTypes);
        assertEquals(64, GreenhouseRecipes.scaledCount(64, 1048576, 1048576));
    }

    @Test
    void invalidScalingInputsCannotOverflowOrCreateUnboundedCounts() {
        assertThrows(IllegalArgumentException.class, () -> GreenhouseRecipes.scaledCount(0, 512, 512));
        assertThrows(IllegalArgumentException.class, () -> GreenhouseRecipes.scaledCount(65, 512, 512));
        assertThrows(IllegalArgumentException.class, () -> GreenhouseRecipes.scaledCount(4, -1, 512));
        assertThrows(IllegalArgumentException.class, () -> GreenhouseRecipes.scaledCount(4, Integer.MAX_VALUE, 512));
        assertThrows(IllegalArgumentException.class, () -> GreenhouseRecipes.scaledCount(4, 512, 0));
        assertThrows(IllegalArgumentException.class, () -> GreenhouseRecipes.scaledCount(4, 512, Integer.MAX_VALUE));
    }

    @Test
    void syncedGuideReferenceCannotChangeAuthoritativeServerScaling() {
        int serverReference = GreenhouseSettings.materialReferenceVolume();
        try {
            GreenhouseRecipes.acceptServerRecipes(Map.of(), 64);
            assertEquals(64, GreenhouseRecipes.referenceVolume());
            assertEquals(GreenhouseRecipes.scaledCount(8, 1024, serverReference), GreenhouseRecipes.scaledCount(8, 1024));
        } finally {
            GreenhouseRecipes.clearClientRecipes();
        }
        assertEquals(serverReference, GreenhouseRecipes.referenceVolume());
    }

    @Test
    void habitatVegetationUsesTheLargestMinimumVolumeAndMeasuredFootprintQuota() {
        var cactus = new GreenhouseRecipes.MaterialRequirement(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cactus"), 3, 16, 1);
        assertEquals(3, GreenhouseRecipes.scaledCount(cactus, 64, 16, 512));
        assertEquals(4, GreenhouseRecipes.scaledCount(cactus, 512, 64, 512));
        assertEquals(6, GreenhouseRecipes.scaledCount(cactus, 404, 81, 512));
        assertEquals(12, GreenhouseRecipes.scaledCount(cactus, 2048, 64, 512));
        var canopy = new GreenhouseRecipes.MaterialRequirement(net.minecraft.resources.ResourceLocation.withDefaultNamespace("oak_leaves"), 64, 2, 2);
        assertEquals(64, GreenhouseRecipes.scaledCount(canopy, 512, 64, 512));
        assertEquals(100, GreenhouseRecipes.scaledCount(canopy, 512, 200, 512));
        assertEquals(512, GreenhouseRecipes.scaledCount(canopy, 4096, 64, 512));
    }

    @Test
    void nativeFloorPercentagesRoundUpAndOverrideParserIsBounded() {
        var soil = GreenhouseSettings.parseFloor("90:minecraft:grass_block,minecraft:dirt");
        assertEquals(58, soil.required(64));
        assertEquals(73, soil.required(81));
        assertEquals(64, GreenhouseSettings.parseFloor("100:minecraft:sand").required(64));
        for (String invalid : java.util.List.of("minecraft:forest=101:minecraft:dirt", "minecraft:forest=-1:minecraft:dirt",
                "minecraft:forest=90:", "minecraft:forest=90:minecraft:dirt,minecraft:dirt")) {
            org.junit.jupiter.api.Assertions.assertFalse(GreenhouseSettings.validateFloorRule(invalid), invalid);
        }
    }
}
