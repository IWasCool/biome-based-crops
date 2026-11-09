package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public final class GreenhouseRecipes {
    private static final Map<Holder<Biome>, Recipe> RECIPES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Map<ResourceLocation, Recipe> clientRecipes = Map.of();

    private GreenhouseRecipes() {
    }

    public static Recipe forBiome(Holder<Biome> biome) {
        ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(null);
        Recipe synced = id == null ? null : clientRecipes.get(id);
        return synced != null ? synced : forServerBiome(biome);
    }

    public static Recipe forServerBiome(Holder<Biome> biome) {
        return RECIPES.computeIfAbsent(biome, GreenhouseRecipes::derive);
    }

    public static void acceptServerRecipes(Map<ResourceLocation, Recipe> recipes) {
        clientRecipes = Map.copyOf(recipes);
    }

    public static void clearClientRecipes() {
        clientRecipes = Map.of();
    }

    public static void clearCache() {
        RECIPES.clear();
    }

    private static Recipe derive(Holder<Biome> biome) {
        CropClimate climate = CropClimate.at(biome, BlockPos.ZERO);
        ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(ResourceLocation.withDefaultNamespace("plains"));
        List<? extends String> overrides = GreenhouseSettings.SPEC.isLoaded()
                ? GreenhouseSettings.BIOME_MATERIAL_RULES.get() : GreenhouseSettings.BIOME_MATERIAL_RULES.getDefault();
        List<ResourceLocation> override = null;
        for (String raw : overrides) {
            if (GreenhouseSettings.validateMaterialRule(raw)) {
                String[] pair = raw.split("=", 2);
                if (id.equals(ResourceLocation.parse(pair[0].trim()))) {
                    override = java.util.Arrays.stream(pair[1].split(",")).map(String::trim).map(ResourceLocation::parse).toList();
                }
            }
        }
        if (override != null) {
            return new Recipe(climate, override, GreenhouseSettings.requiredSpecimens());
        }

        Set<ResourceLocation> nativeBlocks = new LinkedHashSet<>();
        if (id.getNamespace().equals("minecraft")) {
            addVanillaMaterials(id.getPath(), nativeBlocks);
        }
        biome.value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .flatMap(feature -> feature.value().getFeatures()).forEach(feature -> {
                    if (feature.config() instanceof TreeConfiguration tree) {
                        sample(tree.trunkProvider, nativeBlocks);
                        sample(tree.foliageProvider, nativeBlocks);
                    } else if (feature.config() instanceof SimpleBlockConfiguration simple) {
                        sample(simple.toPlace(), nativeBlocks);
                    }
                });
        if (nativeBlocks.isEmpty()) {
            addVanillaMaterials(id.getPath(), nativeBlocks);
        }
        if (nativeBlocks.isEmpty()) {
            add(nativeBlocks, "stone", "moss_block");
        }
        List<ResourceLocation> materials = new ArrayList<>(nativeBlocks);
        // Prefer the biome mod's own flora while keeping selection deterministic.
        if (!id.getNamespace().equals("minecraft")) {
            materials.sort(java.util.Comparator.comparing((ResourceLocation block) -> !block.getNamespace().equals(id.getNamespace()))
                    .thenComparing(ResourceLocation::toString));
        }
        return new Recipe(climate, materials.stream().limit(12).toList(), GreenhouseSettings.requiredSpecimens());
    }

    private static void sample(BlockStateProvider provider, Set<ResourceLocation> output) {
        for (int seed = 0; seed < 4; seed++) {
            try {
                Block block = provider.getState(RandomSource.create(seed), BlockPos.ZERO).getBlock();
                if (block.asItem() != Items.AIR) {
                    output.add(BuiltInRegistries.BLOCK.getKey(block));
                }
            } catch (RuntimeException exception) {
                BiomeBasedCrops.LOGGER.debug("Could not sample greenhouse specimen provider", exception);
                return;
            }
        }
    }

    private static void addVanillaMaterials(String biome, Set<ResourceLocation> materials) {
        if (biome.contains("crimson")) add(materials, "crimson_nylium", "crimson_stem", "nether_wart_block");
        else if (biome.contains("warped")) add(materials, "warped_nylium", "warped_stem", "warped_wart_block");
        else if (biome.contains("soul")) add(materials, "soul_sand", "soul_soil", "bone_block");
        else if (biome.contains("basalt")) add(materials, "basalt", "blackstone", "magma_block");
        else if (biome.contains("nether")) add(materials, "netherrack", "nether_quartz_ore", "magma_block");
        else if (biome.equals("the_end") || biome.startsWith("end_") || biome.equals("small_end_islands")) add(materials, "end_stone", "chorus_plant", "chorus_flower");
        else if (biome.contains("mushroom")) add(materials, "mycelium", "red_mushroom", "brown_mushroom");
        else if (biome.contains("badlands")) add(materials, "red_sand", "terracotta", "red_sandstone");
        else if (biome.contains("desert") || biome.contains("beach")) add(materials, "sand", "sandstone", "cactus");
        else if (biome.contains("frozen") || biome.contains("snow") || biome.contains("ice") || biome.equals("grove")) add(materials, "snow_block", "packed_ice", "spruce_log");
        else if (biome.contains("ocean") || biome.contains("river")) add(materials, "sand", "gravel", "kelp", "seagrass");
        else if (biome.contains("dripstone")) add(materials, "dripstone_block", "pointed_dripstone");
        else if (biome.contains("lush")) add(materials, "moss_block", "azalea", "clay");
        else if (biome.contains("deep_dark")) add(materials, "sculk", "sculk_vein", "deepslate");
        else if (biome.contains("jungle")) add(materials, "jungle_log", "jungle_leaves", "bamboo");
        else if (biome.contains("cherry")) add(materials, "cherry_log", "cherry_leaves", "pink_petals");
        else if (biome.contains("birch")) add(materials, "birch_log", "birch_leaves");
        else if (biome.contains("dark_forest")) add(materials, "dark_oak_log", "dark_oak_leaves");
        else if (biome.contains("taiga")) add(materials, "spruce_log", "spruce_leaves", "podzol");
        else if (biome.contains("mangrove")) add(materials, "mangrove_log", "mangrove_leaves", "mud");
        else if (biome.contains("swamp")) add(materials, "mud", "lily_pad", "oak_log");
        else if (biome.contains("savanna")) add(materials, "acacia_log", "acacia_leaves");
        else if (biome.contains("forest")) add(materials, "oak_log", "oak_leaves", "birch_log");
        else if (biome.contains("peak") || biome.contains("stony") || biome.contains("windswept")) add(materials, "stone", "gravel", "cobblestone");
        else if (biome.contains("plains") || biome.contains("meadow")) add(materials, "grass_block", "dandelion", "poppy");
    }

    private static void add(Set<ResourceLocation> materials, String... blocks) {
        for (String block : blocks) {
            materials.add(ResourceLocation.withDefaultNamespace(block));
        }
    }

    public record Recipe(CropClimate climate, List<ResourceLocation> specimens, int count) {
        public Recipe {
            specimens = List.copyOf(specimens);
            if (specimens.isEmpty() || count < 1) {
                throw new IllegalArgumentException("A greenhouse recipe needs specimens and a positive count");
            }
        }
    }
}
