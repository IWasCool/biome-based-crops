package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
class CropClimateRegistryTest {
    private static final Set<String> CLIMATE_TAG_PATHS = Set.of("icy_biomes", "cold_biomes", "neutral_biomes",
            "warm_biomes", "hot_biomes");

    @Test
    void actualLoadedBiomeDatapacksHaveExclusiveAndConsistentClimates(MinecraftServer server) {
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        int vanillaCount = 0;
        int bopCount = 0;
        for (Holder.Reference<Biome> biome : biomes.holders().toList()) {
            String namespace = biome.key().location().getNamespace();
            CropClimate resolved = CropClimate.at(biome, BlockPos.ZERO);
            assertEquals(1L, Arrays.stream(CropClimate.values()).filter(c -> c.matches(biome, BlockPos.ZERO)).count(),
                    () -> "Overlapping or missing climate: " + biome.key().location());
            EnumSet<CropClimate> exactTags = biome.tags().map(tag -> tag.location())
                    .filter(id -> id.getNamespace().equals("toughasnails") && CLIMATE_TAG_PATHS.contains(id.getPath()))
                    .map(id -> CropClimate.byId(id.getPath().replace("_biomes", "")).orElseThrow())
                    .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(CropClimate.class)));
            assertTrue(exactTags.size() <= 1, () -> "Conflicting shipped climate tags: " + biome.key().location());
            if (!exactTags.isEmpty()) {
                assertEquals(exactTags.iterator().next(), resolved, biome.key().location().toString());
            }
            if (namespace.equals("minecraft")) {
                vanillaCount++;
                if (ModList.get().isLoaded("toughasnails")) {
                    assertEquals(1, exactTags.size(), () -> "TAN climate missing: " + biome.key().location());
                }
            } else if (namespace.equals("biomesoplenty")) {
                bopCount++;
                assertEquals(1, exactTags.size(), () -> "BOP climate missing: " + biome.key().location());
            }
        }
        assertEquals(64, vanillaCount);
        if (ModList.get().isLoaded("biomesoplenty")) {
            assertTrue(bopCount > 0, "BOP biomes were not loaded");
        }
    }

    @Test
    void realNativeBiomeHoldersAllowDefaultPlants(MinecraftServer server) {
        assumeFalse(ModList.get().isLoaded("create_new_world_core"),
                "The pack deliberately remaps vanilla/BOP climates; its behavior has dedicated pack tests");
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        CropClimateRules.CompiledRules rules = new CropClimateRules.CompiledRules(
                Config.CROP_CLIMATE_RULES.getDefault(), Config.DEFAULT_CROP_CLIMATES.getDefault());
        Map<String, List<String>> vanillaNatives = Map.ofEntries(
                Map.entry("glow_berries", List.of("lush_caves")),
                Map.entry("sugar_cane", List.of("plains", "swamp", "jungle", "desert")),
                Map.entry("brown_mushroom", List.of("mushroom_fields", "dark_forest", "taiga", "nether_wastes")),
                Map.entry("red_mushroom", List.of("mushroom_fields", "dark_forest", "taiga", "nether_wastes")),
                Map.entry("spruce_sapling", List.of("snowy_taiga", "taiga", "windswept_forest")),
                Map.entry("oak_sapling", List.of("forest", "swamp", "sparse_jungle")),
                Map.entry("birch_sapling", List.of("birch_forest")),
                Map.entry("cherry_sapling", List.of("cherry_grove")),
                Map.entry("dark_oak_sapling", List.of("dark_forest")),
                Map.entry("jungle_sapling", List.of("jungle", "bamboo_jungle")),
                Map.entry("acacia_sapling", List.of("savanna")),
                Map.entry("mangrove_propagule", List.of("mangrove_swamp")));
        assertNativeProfiles(biomes, rules, "minecraft", vanillaNatives);
        if (ModList.get().isLoaded("biomesoplenty")) {
            Map<String, List<String>> bopNatives = Map.ofEntries(
                    Map.entry("rainbow_birch_sapling", List.of("auroral_garden")),
                    Map.entry("snowblossom_sapling", List.of("snowblossom_grove", "jacaranda_glade")),
                    Map.entry("red_maple_sapling", List.of("snowy_maple_woods", "maple_woods", "tundra", "bog")),
                    Map.entry("orange_maple_sapling", List.of("seasonal_forest", "pumpkin_patch")),
                    Map.entry("yellow_maple_sapling", List.of("aspen_glade", "seasonal_forest")),
                    Map.entry("pine_sapling", List.of("hot_springs", "jade_cliffs", "dryland")),
                    Map.entry("fir_sapling", List.of("coniferous_forest", "snowy_coniferous_forest", "snowy_fir_clearing")),
                    Map.entry("redwood_sapling", List.of("redwood_forest")),
                    Map.entry("willow_sapling", List.of("wetland", "bayou")),
                    Map.entry("flowering_oak_sapling", List.of("orchard", "mystic_grove", "tropics")),
                    Map.entry("jacaranda_sapling", List.of("jacaranda_glade", "lavender_field")),
                    Map.entry("magic_sapling", List.of("mystic_grove")),
                    Map.entry("origin_sapling", List.of("origin_valley", "wintry_origin_valley")),
                    Map.entry("umbran_sapling", List.of("ominous_woods")),
                    Map.entry("dead_sapling", List.of("muskeg", "dead_forest", "wasteland")),
                    Map.entry("cypress_sapling", List.of("aspen_glade", "mediterranean_forest")),
                    Map.entry("palm_sapling", List.of("tropics", "lush_desert")),
                    Map.entry("mahogany_sapling", List.of("rainforest", "rocky_rainforest")),
                    Map.entry("hellbark_sapling", List.of("undergrowth")),
                    Map.entry("empyreal_sapling", List.of("end_wilds")));
            assertNativeProfiles(biomes, rules, "biomesoplenty", bopNatives);
        }
    }

    private static void assertNativeProfiles(Registry<Biome> biomes, CropClimateRules.CompiledRules rules,
            String namespace, Map<String, List<String>> natives) {
        natives.forEach((plant, homes) -> {
            ResourceLocation plantId = ResourceLocation.fromNamespaceAndPath(namespace, plant);
            CropProfile profile = rules.profileFor(plantId, plantId);
            for (String home : homes) {
                ResourceLocation biomeId = ResourceLocation.fromNamespaceAndPath(namespace, home);
                Holder.Reference<Biome> biome = biomes.getHolder(biomeId).orElseThrow();
                CropClimate climate = CropClimate.at(biome, BlockPos.ZERO);
                assertTrue(profile.climates().contains(climate), () -> plantId + " cannot grow in native " + biomeId + " (" + climate + ")");
            }
        });
    }
}
