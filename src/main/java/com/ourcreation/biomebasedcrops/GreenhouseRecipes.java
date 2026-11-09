package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public static final int REFERENCE_VOLUME = 512;
    private static final Map<Holder<Biome>, Recipe> RECIPES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, List<MaterialRequirement>> VANILLA = vanillaRecipes();
    private static volatile Map<ResourceLocation, Recipe> clientRecipes = Map.of();
    private static volatile Integer clientReferenceVolume;

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
        acceptServerRecipes(recipes, GreenhouseSettings.materialReferenceVolume());
    }

    public static void acceptServerRecipes(Map<ResourceLocation, Recipe> recipes, int referenceVolume) {
        validateScale(1, 0, referenceVolume);
        clientRecipes = Map.copyOf(recipes);
        clientReferenceVolume = referenceVolume;
    }

    public static void clearClientRecipes() {
        clientRecipes = Map.of();
        clientReferenceVolume = null;
    }

    public static void clearCache() {
        RECIPES.clear();
    }

    /** Reference volume advertised by the guide's authoritative recipe catalog. */
    public static int referenceVolume() {
        Integer synced = clientReferenceVolume;
        return synced == null ? GreenhouseSettings.materialReferenceVolume() : synced;
    }

    /** Server-side scaling always uses the active server config, never the client guide snapshot. */
    public static int scaledCount(int baseCount, int roomVolume) {
        return scaledCount(baseCount, roomVolume, GreenhouseSettings.materialReferenceVolume());
    }

    public static int scaledCount(int baseCount, int roomVolume, int referenceVolume) {
        validateScale(baseCount, roomVolume, referenceVolume);
        long proportional = ((long) baseCount * roomVolume + referenceVolume - 1) / referenceVolume;
        return Math.toIntExact(Math.max(baseCount, proportional));
    }

    private static void validateScale(int baseCount, int roomVolume, int referenceVolume) {
        if (baseCount < 1 || baseCount > 64 || roomVolume < 0 || roomVolume > 1048576
                || referenceVolume < 64 || referenceVolume > 1048576) {
            throw new IllegalArgumentException("Invalid greenhouse material scaling input");
        }
    }

    private static Recipe derive(Holder<Biome> biome) {
        CropClimate climate = CropClimate.at(biome, BlockPos.ZERO);
        ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(ResourceLocation.withDefaultNamespace("plains"));
        List<? extends String> overrides = GreenhouseSettings.SPEC.isLoaded()
                ? GreenhouseSettings.BIOME_MATERIAL_RULES.get() : GreenhouseSettings.BIOME_MATERIAL_RULES.getDefault();
        List<MaterialRequirement> override = null;
        for (String raw : overrides) {
            if (GreenhouseSettings.validateMaterialRule(raw)) {
                String[] pair = raw.split("=", 2);
                if (id.equals(ResourceLocation.parse(pair[0].trim()))) {
                    override = GreenhouseSettings.parseRequirements(pair[1], GreenhouseSettings.requiredSpecimens());
                }
            }
        }
        if (override != null) return new Recipe(climate, override);
        if (id.getNamespace().equals("minecraft") && VANILLA.containsKey(id.getPath())) {
            List<MaterialRequirement> materials = VANILLA.get(id.getPath());
            // A datapack may classify a snowy biome as Cold rather than Icy. Its
            // mandatory scenery must not force an extra temperature reduction.
            if (climate == CropClimate.COLD && materials.stream().anyMatch(material -> coolingMaterial(material.block()))) {
                materials = coldTerrain(id.getPath(), materials);
            }
            if (climate != CropClimate.HOT && id.getPath().equals("nether_wastes")) {
                materials = List.of(requirement("minecraft:netherrack", 8), requirement("minecraft:nether_quartz_ore", 4),
                        requirement("minecraft:soul_sand", 2));
            } else if (climate != CropClimate.HOT && id.getPath().equals("basalt_deltas")) {
                materials = List.of(requirement("minecraft:basalt", 8), requirement("minecraft:blackstone", 8),
                        requirement("minecraft:polished_basalt", 4));
            }
            return new Recipe(climate, materials);
        }
        // These registered BOP materials also have survival crafting/loot definitions.
        if (id.equals(ResourceLocation.parse("biomesoplenty:ominous_woods"))) {
            List<MaterialRequirement> ominous = List.of(
                    requirement("biomesoplenty:umbran_log", 8), requirement("biomesoplenty:umbran_leaves", 8),
                    requirement("biomesoplenty:dead_leaves", 4), requirement("biomesoplenty:umbran_wood", 4));
            if (ominous.stream().allMatch(material -> usable(material.block()))) return new Recipe(climate, ominous);
        }
        return deriveModRecipe(biome, id, climate);
    }

    private static Recipe deriveModRecipe(Holder<Biome> biome, ResourceLocation id, CropClimate climate) {
        Set<ResourceLocation> trunks = new LinkedHashSet<>();
        Set<ResourceLocation> foliage = new LinkedHashSet<>();
        Set<ResourceLocation> plants = new LinkedHashSet<>();
        biome.value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .flatMap(feature -> feature.value().getFeatures()).forEach(feature -> {
                    if (feature.config() instanceof TreeConfiguration tree) {
                        sample(tree.trunkProvider, trunks);
                        sample(tree.foliageProvider, foliage);
                    } else if (feature.config() instanceof SimpleBlockConfiguration simple) {
                        sample(simple.toPlace(), plants);
                    }
                });
        Comparator<ResourceLocation> nativeFirst = Comparator
                .comparing((ResourceLocation block) -> !block.getNamespace().equals(id.getNamespace()))
                .thenComparing(ResourceLocation::toString);
        LinkedHashSet<ResourceLocation> selected = new LinkedHashSet<>();
        trunks.stream().sorted(nativeFirst).limit(1).forEach(selected::add);
        foliage.stream().sorted(nativeFirst).limit(1).forEach(selected::add);
        // Keep actual native flowers/ground cover rather than choosing unrelated building blocks.
        plants.stream().sorted(nativeFirst).filter(block -> !selected.contains(block))
                .filter(block -> climate != CropClimate.COLD || !coolingMaterial(block)).limit(2).forEach(selected::add);
        selected.add(substrate(id.getPath(), climate));
        for (String fallback : List.of("stone", "dirt", "moss_block", "gravel")) {
            if (selected.size() >= 3) break;
            selected.add(ResourceLocation.withDefaultNamespace(fallback));
        }
        int count = GreenhouseSettings.requiredSpecimens();
        return new Recipe(climate, selected.stream().limit(5).map(block -> new MaterialRequirement(block, count)).toList());
    }

    private static ResourceLocation substrate(String path, CropClimate climate) {
        if (path.contains("nether") || path.contains("hell") || path.contains("undergrowth")) return ResourceLocation.withDefaultNamespace("netherrack");
        if (path.startsWith("end_") || path.equals("the_end")) return ResourceLocation.withDefaultNamespace("end_stone");
        if (path.contains("desert") || path.contains("dune") || path.contains("beach")) return ResourceLocation.withDefaultNamespace("sand");
        if (path.contains("badland") || path.contains("wasteland")) return ResourceLocation.withDefaultNamespace("terracotta");
        if (path.contains("swamp") || path.contains("bayou") || path.contains("wetland") || path.contains("bog")) return ResourceLocation.withDefaultNamespace("mud");
        if (path.contains("cave") || path.contains("crag") || path.contains("peak") || path.contains("mountain")) return ResourceLocation.withDefaultNamespace("stone");
        if (climate == CropClimate.ICY) return ResourceLocation.withDefaultNamespace("snow_block");
        return ResourceLocation.withDefaultNamespace("grass_block");
    }

    private static void sample(BlockStateProvider provider, Set<ResourceLocation> output) {
        for (int seed = 0; seed < 4; seed++) {
            try {
                ResourceLocation block = BuiltInRegistries.BLOCK.getKey(provider.getState(RandomSource.create(seed), BlockPos.ZERO).getBlock());
                if (usable(block)) output.add(block);
            } catch (RuntimeException exception) {
                BiomeBasedCrops.LOGGER.debug("Could not sample greenhouse specimen provider", exception);
                return;
            }
        }
    }

    private static boolean usable(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.asItem() != Items.AIR).orElse(false);
    }

    private static boolean coolingMaterial(ResourceLocation id) {
        return id.getNamespace().equals("minecraft") && Set.of("snow", "snow_block", "ice", "packed_ice", "blue_ice").contains(id.getPath());
    }

    private static List<MaterialRequirement> coldTerrain(String biome, List<MaterialRequirement> fallback) {
        String recipe = switch (biome) {
            case "snowy_plains" -> "grass_block*8,spruce_log*4,short_grass*4";
            case "ice_spikes" -> "stone*8,calcite*4,diorite*4";
            case "snowy_taiga" -> "spruce_log*8,spruce_leaves*4,podzol*4";
            case "grove" -> "spruce_log*4,spruce_leaves*4,dirt*8";
            case "snowy_slopes" -> "stone*8,dirt*4,gravel*4";
            case "frozen_peaks" -> "stone*8,calcite*4,andesite*4";
            case "jagged_peaks" -> "stone*8,calcite*8,tuff*4";
            case "frozen_river" -> "gravel*8,clay*4,stone*4";
            case "snowy_beach" -> "sand*8,gravel*4,stone*2";
            case "frozen_ocean" -> "gravel*8,kelp*4,clay*4";
            case "deep_frozen_ocean" -> "stone*8,gravel*4,clay*4,kelp*4";
            default -> null;
        };
        if (recipe == null) return fallback;
        Map<String, List<MaterialRequirement>> parsed = new LinkedHashMap<>();
        put(parsed, biome, recipe);
        return parsed.get(biome);
    }

    private static Map<String, List<MaterialRequirement>> vanillaRecipes() {
        Map<String, List<MaterialRequirement>> recipes = new LinkedHashMap<>();
        put(recipes, "the_void", "stone*8,smooth_stone*4,cobblestone*2");
        put(recipes, "plains", "grass_block*8,dandelion*2,poppy*2");
        put(recipes, "sunflower_plains", "grass_block*8,sunflower*2,poppy*2");
        put(recipes, "snowy_plains", "snow_block*8,grass_block*4,ice*2");
        put(recipes, "ice_spikes", "packed_ice*8,snow_block*4,blue_ice*2");
        put(recipes, "desert", "sand*8,sandstone*4,cactus*2");
        put(recipes, "swamp", "mud*8,oak_log*4,lily_pad*2,brown_mushroom*2");
        put(recipes, "mangrove_swamp", "mangrove_log*8,mangrove_leaves*4,mud*4,mangrove_roots*2");
        put(recipes, "forest", "oak_log*8,oak_leaves*4,birch_log*2");
        put(recipes, "flower_forest", "oak_log*4,allium*2,azure_bluet*2,oxeye_daisy*2");
        put(recipes, "birch_forest", "birch_log*8,birch_leaves*4,grass_block*4");
        put(recipes, "dark_forest", "dark_oak_log*8,dark_oak_leaves*4,red_mushroom_block*2");
        put(recipes, "old_growth_birch_forest", "birch_log*8,birch_leaves*4,tall_grass*4,fern*2");
        put(recipes, "old_growth_pine_taiga", "spruce_log*8,podzol*8,mossy_cobblestone*4,brown_mushroom*2");
        put(recipes, "old_growth_spruce_taiga", "spruce_log*8,spruce_leaves*8,podzol*8,large_fern*2");
        put(recipes, "taiga", "spruce_log*8,spruce_leaves*4,fern*4");
        put(recipes, "snowy_taiga", "spruce_log*8,spruce_leaves*4,snow_block*4");
        put(recipes, "savanna", "acacia_log*8,acacia_leaves*4,short_grass*4");
        put(recipes, "savanna_plateau", "acacia_log*8,acacia_leaves*4,coarse_dirt*4,tall_grass*2");
        put(recipes, "windswept_hills", "stone*8,grass_block*4,spruce_log*2");
        put(recipes, "windswept_gravelly_hills", "gravel*8,stone*8,grass_block*2");
        put(recipes, "windswept_forest", "oak_log*4,spruce_log*4,stone*8,grass_block*4");
        put(recipes, "windswept_savanna", "acacia_log*4,coarse_dirt*8,stone*8");
        put(recipes, "jungle", "jungle_log*8,jungle_leaves*8,vine*4,cocoa*2");
        put(recipes, "sparse_jungle", "jungle_log*4,jungle_leaves*4,grass_block*8,melon*2");
        put(recipes, "bamboo_jungle", "bamboo*8,jungle_log*4,jungle_leaves*4,podzol*4");
        put(recipes, "badlands", "red_sand*8,terracotta*8,red_sandstone*4");
        put(recipes, "eroded_badlands", "red_sand*8,orange_terracotta*8,yellow_terracotta*4,white_terracotta*2");
        put(recipes, "wooded_badlands", "red_sand*8,terracotta*4,oak_log*4,coarse_dirt*4");
        put(recipes, "meadow", "grass_block*8,oxeye_daisy*2,cornflower*2,allium*2");
        put(recipes, "cherry_grove", "cherry_log*8,cherry_leaves*4,pink_petals*4");
        put(recipes, "grove", "spruce_log*4,snow_block*8,snow*2");
        put(recipes, "snowy_slopes", "snow_block*8,stone*4,dirt*4");
        put(recipes, "frozen_peaks", "packed_ice*8,snow_block*4,stone*8");
        put(recipes, "jagged_peaks", "stone*8,snow_block*8,calcite*4");
        put(recipes, "stony_peaks", "stone*8,calcite*4,granite*4");
        put(recipes, "river", "gravel*8,clay*4,sugar_cane*2");
        put(recipes, "frozen_river", "ice*8,gravel*4,snow_block*4");
        put(recipes, "beach", "sand*8,gravel*4,seagrass*2");
        put(recipes, "snowy_beach", "sand*8,snow_block*4,ice*4");
        put(recipes, "stony_shore", "stone*8,gravel*4,andesite*4");
        put(recipes, "warm_ocean", "sand*8,brain_coral_block*4,sea_pickle*2");
        put(recipes, "lukewarm_ocean", "sand*8,seagrass*4,kelp*2");
        put(recipes, "deep_lukewarm_ocean", "sand*8,stone*4,seagrass*4,kelp*4");
        put(recipes, "ocean", "sand*4,gravel*8,kelp*4");
        put(recipes, "deep_ocean", "stone*8,gravel*4,kelp*4");
        put(recipes, "cold_ocean", "gravel*8,kelp*4,seagrass*2");
        put(recipes, "deep_cold_ocean", "stone*8,gravel*8,kelp*4,seagrass*2");
        put(recipes, "frozen_ocean", "ice*8,gravel*4,kelp*2");
        put(recipes, "deep_frozen_ocean", "packed_ice*8,gravel*4,kelp*4,stone*4");
        put(recipes, "mushroom_fields", "mycelium*8,red_mushroom_block*4,brown_mushroom_block*4");
        put(recipes, "dripstone_caves", "dripstone_block*8,pointed_dripstone*4,copper_ore*2");
        put(recipes, "lush_caves", "moss_block*8,clay*4,azalea*2,spore_blossom*2");
        put(recipes, "deep_dark", "sculk*8,sculk_vein*4,deepslate*8");
        put(recipes, "nether_wastes", "netherrack*8,nether_quartz_ore*4,magma_block*2");
        put(recipes, "warped_forest", "warped_nylium*8,warped_stem*4,warped_wart_block*4,warped_fungus*2");
        put(recipes, "crimson_forest", "crimson_nylium*8,crimson_stem*4,nether_wart_block*4,crimson_fungus*2");
        put(recipes, "soul_sand_valley", "soul_sand*8,soul_soil*8,bone_block*4");
        put(recipes, "basalt_deltas", "basalt*8,blackstone*8,magma_block*4");
        put(recipes, "the_end", "end_stone*8,obsidian*4,purpur_block*4");
        put(recipes, "end_highlands", "end_stone*8,chorus_plant*4,chorus_flower*2");
        put(recipes, "end_midlands", "end_stone*8,chorus_plant*2,end_stone_bricks*4");
        put(recipes, "small_end_islands", "end_stone*8,obsidian*2,end_stone_brick_slab*4");
        put(recipes, "end_barrens", "end_stone*8,end_stone_bricks*4,purpur_pillar*2");
        return Map.copyOf(recipes);
    }

    private static void put(Map<String, List<MaterialRequirement>> recipes, String biome, String raw) {
        List<MaterialRequirement> requirements = new ArrayList<>();
        for (String token : raw.split(",")) {
            String[] pair = token.split("\\*");
            requirements.add(requirement("minecraft:" + pair[0], Integer.parseInt(pair[1])));
        }
        recipes.put(biome, List.copyOf(requirements));
    }

    private static MaterialRequirement requirement(String block, int count) {
        return new MaterialRequirement(ResourceLocation.parse(block), count);
    }

    public record MaterialRequirement(ResourceLocation block, int count) {
        public MaterialRequirement {
            Objects.requireNonNull(block, "block");
            if (count < 1 || count > 64) throw new IllegalArgumentException("A material requirement must contain 1 to 64 blocks");
        }
    }

    public record Recipe(CropClimate climate, List<ResourceLocation> specimens, int count,
                         List<MaterialRequirement> requirements) {
        public Recipe {
            Objects.requireNonNull(climate, "climate");
            requirements = List.copyOf(requirements);
            if (requirements.isEmpty() || requirements.size() > 32) {
                throw new IllegalArgumentException("A greenhouse recipe needs 1 to 32 distinct materials");
            }
            LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
            int total = 0;
            for (MaterialRequirement requirement : requirements) {
                if (!ids.add(requirement.block())) throw new IllegalArgumentException("Duplicate greenhouse material " + requirement.block());
                total += requirement.count();
            }
            specimens = List.copyOf(ids);
            count = total;
        }

        public Recipe(CropClimate climate, List<MaterialRequirement> requirements) {
            this(climate, List.of(), 0, requirements);
        }

        public Recipe(CropClimate climate, List<ResourceLocation> specimens, int count) {
            this(climate, specimens, count, specimens.stream().map(block -> new MaterialRequirement(block, count)).toList());
        }
    }
}
