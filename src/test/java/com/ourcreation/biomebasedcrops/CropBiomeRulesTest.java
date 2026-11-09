package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CropBiomeRulesTest {
    @Test
    void acceptsBiomeIdsTagsAndWildcardWithWhitespace() {
        for (String rule : List.of("wheat=minecraft:plains", "rice=#minecraft:is_jungle",
                " wheat = minecraft:plains, #c:is_cold ", "minecraft:wheat=*", "rice=example:custom_biome")) {
            assertTrue(Config.validateBiomeRule(rule), rule);
        }
    }

    @Test
    void rejectsMalformedRulesAndEmptySelectors() {
        for (String rule : List.of("", "wheat", "=minecraft:plains", "wheat=", "wheat=plains",
                "wheat=#is_jungle", "wheat=#", "wheat=minecraft:", "wheat=#minecraft:", "wheat=:plains",
                "wheat=minecraft:Plains", "wheat=minecraft:plains,",
                "wheat=,minecraft:plains", "wheat=minecraft:plains,,minecraft:forest", "wheat=*=")) {
            assertFalse(Config.validateBiomeRule(rule), rule);
        }
        assertFalse(Config.validateBiomeRule(null));
        assertFalse(Config.validateBiomeRule(1));
    }

    @Test
    void biomeRulesUseMostSpecificBlockOrItemTokenAndKeepFirstTie() {
        var rules = new CropClimateRules.CompiledRules(List.of("wheat=hot"), "cold", List.of(
                "wheat=minecraft:desert", "minecraft:wheat=minecraft:plains", "minecraft:wheat=minecraft:forest"));
        CropProfile block = rules.profileFor(id("minecraft:wheat"), id("minecraft:wheat_seeds"));
        assertEquals(List.of("minecraft:plains"), selectors(block));
        CropProfile item = rules.profileFor(id("example:plant"), id("minecraft:wheat_seeds"));
        assertEquals(selectors(block), selectors(item));
        assertEquals(List.of("minecraft:desert"), selectors(rules.profileFor(id("other:wheat"), id("minecraft:air"))));
        assertFalse(rules.profileFor(id("wheat_mod:rice"), id("minecraft:air")).hasBiomeRule());
    }

    @Test
    void absentOrInvalidBiomeRulesKeepClimateDefaultsAndProfilesAreImmutable() {
        var rules = new CropClimateRules.CompiledRules(List.of("rice=hot"), "cold", List.of(
                "broken", "rice=plains", "wheat=*"));
        CropProfile rice = rules.profileFor(id("example:rice"), id("minecraft:air"));
        assertFalse(rice.hasBiomeRule());
        assertTrue(rice.allows(CropClimate.HOT));
        CropProfile fallback = rules.profileFor(id("example:unknown"), id("minecraft:air"));
        assertFalse(fallback.hasBiomeRule());
        assertTrue(fallback.allows(CropClimate.COLD));
        CropProfile wheat = rules.profileFor(id("minecraft:wheat"), id("minecraft:wheat_seeds"));
        assertSame(wheat, rules.profileFor(id("minecraft:wheat"), id("minecraft:wheat_seeds")));
        assertThrows(UnsupportedOperationException.class, () -> wheat.biomes().clear());
    }

    private static List<String> selectors(CropProfile profile) {
        return profile.biomes().stream().map(BiomeSelector::toString).toList();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
