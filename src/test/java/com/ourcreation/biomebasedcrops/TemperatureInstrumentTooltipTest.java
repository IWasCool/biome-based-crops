package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TemperatureInstrumentTooltipTest {
    @Test
    void tanInstrumentsReplaceTheOldReadingWithSharedInstructionsAndPreserveMetadata() {
        for (String item : List.of("toughasnails:thermometer", "toughasnails:thermoregulator")) {
            Component name = Component.translatable("item." + item.replace(':', '.'));
            Component oldReading = Component.translatable("biome_based_crops.tooltip.local_climate", "Cold");
            Component itemId = Component.literal(item);
            Component category = Component.translatable("itemGroup.toughasnails");
            Component unrelated = Component.translatable("other_mod.tooltip.detail");
            var lines = new ArrayList<>(List.of(name, oldReading, category, unrelated, itemId));

            assertTrue(TemperatureInstrumentCompatibility.updateTooltip(ResourceLocation.parse(item), lines));
            assertEquals(List.of(name, ThermometerItem.usageTooltip(), category, unrelated, itemId), lines);
            // Tooltip rebuilds and other handlers must not leave two copies of the instructions.
            assertTrue(TemperatureInstrumentCompatibility.updateTooltip(ResourceLocation.parse(item), lines));
            assertEquals(List.of(name, ThermometerItem.usageTooltip(), category, unrelated, itemId), lines);
        }
    }

    @Test
    void otherInstrumentsKeepTheirExistingTooltips() {
        for (String item : List.of("northstar:thermometer", "biome_based_crops:thermometer", "minecraft:compass")) {
            var original = List.<Component>of(Component.literal(item),
                    Component.translatable("biome_based_crops.tooltip.local_climate", "Warm"));
            var lines = new ArrayList<>(original);
            assertFalse(TemperatureInstrumentCompatibility.updateTooltip(ResourceLocation.parse(item), lines));
            assertEquals(original, lines);
        }
    }
}
