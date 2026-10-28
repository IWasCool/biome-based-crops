package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CropClimateRulesTest {
    @Test
    void mostSpecificTokenWinsForBothBlockAndItemIds() {
        var rules = new CropClimateRules.CompiledRules(List.of(
                "oak=warm", "dark_oak=cold", "dark_oak_sapling=neutral"), "hot");
        assertEquals(Set.of(CropClimate.NEUTRAL), rules.profileFor(
                id("minecraft:dark_oak_sapling"), id("minecraft:air")).climates());
        assertEquals(Set.of(CropClimate.NEUTRAL), rules.profileFor(
                id("example:plant"), id("example:dark_oak_sapling")).climates());
    }

    @Test
    void equalSpecificityPreservesConfiguredOrder() {
        var rules = new CropClimateRules.CompiledRules(List.of("rice=cold", "rice=hot"), "neutral");
        assertEquals(Set.of(CropClimate.COLD), rules.profileFor(id("farmersdelight:rice"), id("minecraft:air")).climates());
    }

    @Test
    void bareCropNamesDoNotMatchTheNamespace() {
        var rules = new CropClimateRules.CompiledRules(List.of("tea=hot"), "cold");
        assertEquals(Set.of(CropClimate.COLD), rules.profileFor(id("tea_mod:rice"), id("tea_mod:rice")).climates());
        assertEquals(Set.of(CropClimate.HOT), rules.profileFor(id("example:tea_crop"), id("minecraft:air")).climates());
    }

    @Test
    void namespacedRulesCanTargetOneModsPlant() {
        var rules = new CropClimateRules.CompiledRules(List.of("rice=warm", "example:rice=hot"), "cold");
        assertEquals(Set.of(CropClimate.HOT), rules.profileFor(id("example:rice"), id("minecraft:air")).climates());
        assertEquals(Set.of(CropClimate.WARM), rules.profileFor(id("other:rice"), id("minecraft:air")).climates());
    }

    @Test
    void rulesNormalizeWhitespaceAndCaseAndUseDefaultWhenUnmatched() {
        var rules = new CropClimateRules.CompiledRules(List.of("  RICE = HOT, Warm "), "icy,cold");
        assertEquals(Set.of(CropClimate.HOT, CropClimate.WARM),
                rules.profileFor(id("example:rice_panicles"), id("minecraft:air")).climates());
        assertEquals(Set.of(CropClimate.ICY, CropClimate.COLD),
                rules.profileFor(id("example:unknown_crop"), id("minecraft:air")).climates());
    }

    @Test
    void malformedRulesCannotCrashResolutionAndInvalidClimatesUseSafeFallback() {
        var rules = new CropClimateRules.CompiledRules(List.of("broken", "=hot", "rice=typo"), "also_a_typo");
        assertEquals(Set.of(CropClimate.NEUTRAL, CropClimate.WARM),
                rules.profileFor(id("example:rice"), id("minecraft:air")).climates());
        assertEquals(Set.of(CropClimate.NEUTRAL, CropClimate.WARM),
                rules.profileFor(id("example:unknown"), id("minecraft:air")).climates());
    }

    @Test
    void cachedProfilesAreImmutableAndReuseResolvedProfiles() {
        var rules = new CropClimateRules.CompiledRules(List.of("rice=warm,hot"), "cold");
        var block = id("farmersdelight:rice");
        var item = id("farmersdelight:rice_panicle");
        CropProfile profile = rules.profileFor(block, item);
        assertSame(profile, rules.profileFor(block, item));
        assertThrows(UnsupportedOperationException.class, () -> profile.climates().clear());

        EnumSet<CropClimate> mutable = EnumSet.of(CropClimate.HOT);
        CropProfile copied = new CropProfile(mutable);
        mutable.clear();
        assertTrue(copied.allows(CropClimate.HOT));
        assertThrows(IllegalArgumentException.class, () -> new CropProfile(Set.of()));
    }

    private static ResourceLocation id(String id) {
        return ResourceLocation.parse(id);
    }
}
