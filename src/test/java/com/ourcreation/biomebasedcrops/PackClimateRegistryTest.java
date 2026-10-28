package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class PackClimateRegistryTest {
    @Test
    void packClimateTagReplacementsTakePriorityOverSnowAndBaseTemperature(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("create_new_world_core"));
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, CropClimate> expected = Map.ofEntries(
                Map.entry("create_new_world_core:deep_freeze", CropClimate.COLD),
                Map.entry("create_new_world_core:scorched_earth", CropClimate.HOT),
                Map.entry("minecraft:desert", CropClimate.WARM),
                Map.entry("minecraft:nether_wastes", CropClimate.WARM),
                Map.entry("minecraft:crimson_forest", CropClimate.WARM),
                Map.entry("minecraft:warped_forest", CropClimate.WARM),
                Map.entry("minecraft:snowy_plains", CropClimate.COLD),
                Map.entry("minecraft:frozen_peaks", CropClimate.COLD),
                Map.entry("biomesoplenty:dryland", CropClimate.WARM),
                Map.entry("biomesoplenty:undergrowth", CropClimate.WARM),
                Map.entry("biomesoplenty:cold_desert", CropClimate.COLD),
                Map.entry("biomesoplenty:snowblossom_grove", CropClimate.COLD));
        expected.forEach((id, climate) -> assertClimate(biomes, id, climate));
    }

    @Test
    void additionalInstalledBiomeModsResolveTheirActualTagsAndTemperatures(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("create_new_world_core"));
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        // Northstar models planetary heat independently; its biome JSONs mostly use 0.8.
        // Names such as "icy" or "lava" are not temperature tags.
        Map<String, CropClimate> expected = Map.ofEntries(
                Map.entry("northstar:martian_peaks", CropClimate.ICY),
                Map.entry("northstar:martian_dunes", CropClimate.NEUTRAL),
                Map.entry("northstar:mercury_icy_caverns", CropClimate.NEUTRAL),
                Map.entry("northstar:venus_lava_caves", CropClimate.NEUTRAL),
                Map.entry("northstar:void", CropClimate.WARM),
                Map.entry("spawn:cold_island", CropClimate.NEUTRAL),
                Map.entry("spawn:tropical_island", CropClimate.NEUTRAL),
                Map.entry("spawn:deep_warm_ocean", CropClimate.NEUTRAL),
                Map.entry("quark:glimmering_weald", CropClimate.NEUTRAL),
                Map.entry("createnuclear:irradiated_land", CropClimate.HOT),
                Map.entry("createteleporters:pd_biome", CropClimate.NEUTRAL),
                Map.entry("create_abyss:abyss", CropClimate.COLD),
                Map.entry("streamsreflowing:stream", CropClimate.NEUTRAL));
        expected.forEach((id, climate) -> assertClimate(biomes, id, climate));
    }

    @Test
    void installedBiomeResourcesLoadAlongsideGeophilicsVanillaReplacements(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("create_new_world_core"));
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, Long> expectedCounts = Map.ofEntries(
                Map.entry("minecraft", 64L), Map.entry("biomesoplenty", 69L), Map.entry("northstar", 24L),
                Map.entry("spawn", 10L), Map.entry("create_new_world_core", 2L), Map.entry("quark", 1L),
                Map.entry("createnuclear", 1L), Map.entry("createteleporters", 1L), Map.entry("create_abyss", 1L),
                Map.entry("streamsreflowing", 1L));
        expectedCounts.forEach((namespace, count) -> assertEquals(count.longValue(), biomes.keySet().stream()
                .filter(id -> id.getNamespace().equals(namespace)).count(), "Biome count for " + namespace));
        assertTrue(biomes.getHolder(ResourceLocation.parse("minecraft:plains")).isPresent());
    }

    @Test
    void verifiedAdditionalSaplingsAndPlanetaryPlantsHaveSuitableDefaults(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("create_new_world_core"));
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        CropClimateRules.CompiledRules rules = new CropClimateRules.CompiledRules(
                Config.CROP_CLIMATE_RULES.getDefault(), Config.DEFAULT_CROP_CLIMATES.getDefault());
        Map<String, List<String>> homes = Map.ofEntries(
                Map.entry("biomesoplenty:pine_sapling", List.of("biomesoplenty:dryland")),
                Map.entry("biomesoplenty:dead_sapling", List.of("biomesoplenty:wasteland")),
                Map.entry("biomesoplenty:hellbark_sapling", List.of("biomesoplenty:undergrowth")),
                Map.entry("quark:blue_blossom_sapling", List.of("minecraft:snowy_plains", "biomesoplenty:snowblossom_grove")),
                Map.entry("quark:lavender_blossom_sapling", List.of("minecraft:swamp", "minecraft:mangrove_swamp")),
                Map.entry("quark:orange_blossom_sapling", List.of("minecraft:savanna")),
                Map.entry("quark:yellow_blossom_sapling", List.of("minecraft:plains")),
                Map.entry("quark:red_blossom_sapling", List.of("minecraft:badlands")),
                Map.entry("quark:ancient_sapling", List.of("quark:glimmering_weald")),
                Map.entry("spawn:date_sapling", List.of("spawn:dodo_island", "spawn:tropical_island")),
                Map.entry("spawn:sunflower_plant", List.of("minecraft:sunflower_plains")),
                Map.entry("northstar:argyre_sapling", List.of("northstar:martian_overgrown_caverns")),
                Map.entry("northstar:martian_strawberry_bush", List.of("northstar:martian_overgrown_caverns")),
                Map.entry("northstar:mars_sprout", List.of("northstar:martian_overgrown_caverns")),
                Map.entry("northstar:mars_palm", List.of("northstar:martian_overgrown_caverns")),
                Map.entry("northstar:mars_tulip", List.of("northstar:martian_overgrown_caverns")),
                Map.entry("northstar:venus_vines", List.of("northstar:venus_fungal_caverns")),
                Map.entry("northstar:bloom_fungus", List.of("northstar:venus_fungal_caverns", "northstar:venus_fungal_forest")),
                Map.entry("northstar:plate_fungus", List.of("northstar:venus_fungal_caverns", "northstar:venus_fungal_forest")),
                Map.entry("northstar:spike_fungus", List.of("northstar:venus_fungal_caverns", "northstar:venus_fungal_forest")),
                Map.entry("northstar:tower_fungus", List.of("northstar:venus_fungal_caverns", "northstar:venus_fungal_forest")));
        homes.forEach((plant, nativeBiomes) -> {
            ResourceLocation id = ResourceLocation.parse(plant);
            CropProfile profile = rules.profileFor(id, id);
            for (String home : nativeBiomes) {
                var biome = biomes.getHolder(ResourceLocation.parse(home)).orElseThrow();
                assertTrue(profile.allows(CropClimate.at(biome, BlockPos.ZERO)), () -> plant + " cannot grow in " + home);
            }
        });
    }

    @Test
    void existingHotOnlyPreferencesRemainRestrictedByThePacksRemappedBiomes(MinecraftServer server) {
        assumeTrue(ModList.get().isLoaded("create_new_world_core"));
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        CropClimateRules.CompiledRules rules = new CropClimateRules.CompiledRules(
                Config.CROP_CLIMATE_RULES.getDefault(), Config.DEFAULT_CROP_CLIMATES.getDefault());
        CropClimate scorched = CropClimate.at(biomes.getHolder(ResourceLocation.parse("create_new_world_core:scorched_earth"))
                .orElseThrow(), BlockPos.ZERO);
        Map<String, String> warmHomes = Map.of(
                "minecraft:cactus", "minecraft:desert",
                "minecraft:nether_wart", "minecraft:nether_wastes",
                "minecraft:crimson_fungus", "minecraft:crimson_forest",
                "minecraft:warped_fungus", "minecraft:warped_forest");
        warmHomes.forEach((plant, home) -> {
            ResourceLocation id = ResourceLocation.parse(plant);
            CropProfile profile = rules.profileFor(id, id);
            var homeBiome = biomes.getHolder(ResourceLocation.parse(home)).orElseThrow();
            assertFalse(profile.allows(CropClimate.at(homeBiome, BlockPos.ZERO)), plant + " lost its configured hot-only preference");
            assertTrue(profile.allows(scorched), plant + " cannot use the pack's hot biome");
        });
    }

    private static void assertClimate(Registry<Biome> biomes, String id, CropClimate expected) {
        var holder = biomes.getHolder(ResourceLocation.parse(id)).orElseThrow();
        assertEquals(expected, CropClimate.at(holder, BlockPos.ZERO), id);
    }
}
