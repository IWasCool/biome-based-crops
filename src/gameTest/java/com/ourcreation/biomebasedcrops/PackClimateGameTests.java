package com.ourcreation.biomebasedcrops;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Audits the installed pack's actual loaded registries on a normal GameTest server. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class PackClimateGameTests {
    private static final Set<String> CLIMATE_TAG_PATHS = Set.of("icy_biomes", "cold_biomes", "neutral_biomes",
            "warm_biomes", "hot_biomes");

    private PackClimateGameTests() {
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void allPackBiomeHoldersHaveOneConsistentClimate(GameTestHelper helper) {
        withPack(helper, biomes -> {
            helper.assertTrue(biomes.size() == 175, "Expected 175 registered pack biomes, found " + biomes.size());
            for (Holder.Reference<Biome> holder : biomes.holders().toList()) {
                CropClimate resolved = CropClimate.at(holder, BlockPos.ZERO);
                long matches = Arrays.stream(CropClimate.values()).filter(c -> c.matches(holder, BlockPos.ZERO)).count();
                helper.assertTrue(matches == 1 && resolved.matches(holder, BlockPos.ZERO),
                        "Climate resolution disagrees for " + holder.key().location());
                EnumSet<CropClimate> exactTags = holder.tags().map(tag -> tag.location())
                        .filter(id -> id.getNamespace().equals("toughasnails") && CLIMATE_TAG_PATHS.contains(id.getPath()))
                        .map(id -> CropClimate.byId(id.getPath().replace("_biomes", "")).orElseThrow())
                        .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(CropClimate.class)));
                helper.assertTrue(exactTags.size() <= 1, "Conflicting shipped climate tags for " + holder.key().location());
                if (!exactTags.isEmpty()) {
                    helper.assertTrue(exactTags.contains(resolved), "Compatibility tag was ignored for " + holder.key().location());
                }
            }
        });
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void coreClimateReplacementsOverrideSnowAndBaseTemperature(GameTestHelper helper) {
        withPack(helper, biomes -> {
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
            expected.forEach((id, expectedClimate) -> assertClimate(helper, biomes, id, expectedClimate));
        });
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void additionalBiomeModsUseTheirFormalTagsAndBaseTemperatures(GameTestHelper helper) {
        withPack(helper, biomes -> {
            // Northstar's independent planetary temperature is not a Minecraft biome tag.
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
            expected.forEach((id, expectedClimate) -> assertClimate(helper, biomes, id, expectedClimate));
        });
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void allBiomeModsLoadWithGeophilicsVanillaReplacements(GameTestHelper helper) {
        withPack(helper, biomes -> {
            Map<String, Long> expected = Map.ofEntries(
                    Map.entry("minecraft", 64L), Map.entry("biomesoplenty", 69L), Map.entry("northstar", 24L),
                    Map.entry("spawn", 10L), Map.entry("create_new_world_core", 2L), Map.entry("quark", 1L),
                    Map.entry("createnuclear", 1L), Map.entry("createteleporters", 1L), Map.entry("create_abyss", 1L),
                    Map.entry("streamsreflowing", 1L), Map.entry("terrablender", 1L));
            expected.forEach((namespace, count) -> {
                long actual = biomes.keySet().stream().filter(id -> id.getNamespace().equals(namespace)).count();
                helper.assertTrue(actual == count, "Biome count for " + namespace + ": expected " + count + ", found " + actual);
            });
        });
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void additionalSaplingsAndPlanetaryPlantsHaveSuitableDefaults(GameTestHelper helper) {
        withPack(helper, biomes -> {
            CropClimateRules.CompiledRules rules = defaults();
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
                helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Missing pack plant " + id);
                CropProfile profile = rules.profileFor(id, id);
                for (String home : nativeBiomes) {
                    helper.assertTrue(profile.allows(climate(biomes, home)), plant + " cannot grow in " + home);
                }
            });
        });
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void configuredHotOnlyPreferencesStillRequireAHotClimate(GameTestHelper helper) {
        withPack(helper, biomes -> {
            CropClimateRules.CompiledRules rules = defaults();
            CropClimate scorched = climate(biomes, "create_new_world_core:scorched_earth");
            Map<String, String> warmHomes = Map.of(
                    "minecraft:cactus", "minecraft:desert",
                    "minecraft:nether_wart", "minecraft:nether_wastes",
                    "minecraft:crimson_fungus", "minecraft:crimson_forest",
                    "minecraft:warped_fungus", "minecraft:warped_forest");
            warmHomes.forEach((plant, home) -> {
                ResourceLocation id = ResourceLocation.parse(plant);
                CropProfile profile = rules.profileFor(id, id);
                helper.assertFalse(profile.allows(climate(biomes, home)), plant + " lost its hot-only preference");
                helper.assertTrue(profile.allows(scorched), plant + " cannot use the pack's hot biome");
            });
        });
    }

    @GameTest(template = "empty", batch = "pack_climates")
    public static void customPackPlantFamiliesAreRecognizedAndBuildingBlocksAreExcluded(GameTestHelper helper) {
        withPack(helper, biomes -> {
            for (String path : List.of("martian_strawberry_bush", "mars_palm", "mars_sprout", "mars_sprout_big",
                    "mars_tulip", "venus_vines", "glowing_venus_vines")) {
                assertCategory(helper, "northstar:" + path, PlantCategory.CROP);
            }
            for (String path : List.of("bloom_fungus", "plate_fungus", "spike_fungus", "tower_fungus")) {
                assertCategory(helper, "northstar:" + path, PlantCategory.SAPLING);
            }
            assertCategory(helper, "spawn:date_sapling", PlantCategory.SAPLING);
            assertCategory(helper, "spawn:sunflower_plant", PlantCategory.CROP);
            assertCategory(helper, "supplementaries:flax", PlantCategory.CROP);
            assertCategory(helper, "supplementaries:wild_flax", PlantCategory.CROP);
            for (String id : List.of("northstar:bloom_fungus_block", "northstar:bloom_fungus_stem_block",
                    "northstar:plate_fungus_cap_block", "northstar:tower_fungus_stem_block",
                    "spawn:date_planks", "spawn:potted_date_sapling")) {
                ResourceLocation key = ResourceLocation.parse(id);
                helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(key), "Missing pack control block " + id);
                helper.assertTrue(CropMatcher.categoryForState(BuiltInRegistries.BLOCK.get(key).defaultBlockState()).isEmpty(),
                        "Non-growing building block was classified as a plant: " + id);
            }
        });
    }

    private static CropClimateRules.CompiledRules defaults() {
        return new CropClimateRules.CompiledRules(Config.CROP_CLIMATE_RULES.getDefault(), Config.DEFAULT_CROP_CLIMATES.getDefault());
    }

    private static void assertCategory(GameTestHelper helper, String id, PlantCategory expected) {
        ResourceLocation key = ResourceLocation.parse(id);
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(key), "Missing pack plant " + id);
        helper.assertTrue(CropMatcher.categoryForState(BuiltInRegistries.BLOCK.get(key).defaultBlockState()).orElse(null) == expected,
                "Incorrect plant category for " + id + "; expected " + expected);
    }

    private static CropClimate climate(Registry<Biome> biomes, String id) {
        return CropClimate.at(biomes.getHolder(ResourceLocation.parse(id)).orElseThrow(), BlockPos.ZERO);
    }

    private static void assertClimate(GameTestHelper helper, Registry<Biome> biomes, String id, CropClimate expected) {
        helper.assertTrue(climate(biomes, id) == expected, "Incorrect biome climate for " + id + "; expected " + expected);
    }

    private static void withPack(GameTestHelper helper, Consumer<Registry<Biome>> test) {
        if (!ModList.get().isLoaded("create_new_world_core")) {
            BiomeBasedCrops.LOGGER.info("Skipping optional pack climate GameTest: Create New World Core is not installed");
            helper.succeed();
            return;
        }
        test.accept(helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME));
        helper.succeed();
    }
}
