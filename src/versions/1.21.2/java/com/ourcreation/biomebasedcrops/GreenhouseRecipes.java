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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public final class GreenhouseRecipes {
    public static final int REFERENCE_VOLUME = 512;
    // Retain the existing wire field while distinguishing ground accents from
    // the ordinary positive flags used for room-volume habitat percentages.
    private static final Map<Holder<Biome>, Recipe> RECIPES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Registry<Biome>, Map<ResourceLocation, Recipe>> CATALOGS = new WeakHashMap<>();
    private static final Map<String, List<MaterialRequirement>> VANILLA = vanillaRecipes();
    private static final Map<String, List<MaterialRequirement>> BOP = biomesOPlentyRecipes();
    private static final Set<String> UNCOLLECTIBLE = Set.of("bedrock", "barrier", "spawner", "budding_amethyst",
            "command_block", "chain_command_block", "repeating_command_block", "structure_block", "structure_void",
            "jigsaw", "light", "end_portal_frame", "reinforced_deepslate", "chorus_plant");
    private static volatile Map<ResourceLocation, Recipe> clientRecipes = Map.of();
    private static volatile Integer clientReferenceVolume;

    private GreenhouseRecipes() {
    }

    public static Recipe forBiome(Holder<Biome> biome) {
        ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(null);
        Recipe synced = id == null ? null : clientRecipes.get(id);
        return synced != null ? synced : RECIPES.computeIfAbsent(biome, GreenhouseRecipes::derive);
    }

    public static Recipe forServerBiome(Registry<Biome> biomes, Holder<Biome> biome) {
        ResourceLocation id = biome.unwrapKey().orElseThrow().location();
        return Objects.requireNonNull(serverCatalog(biomes).get(id), "Biome is not in this registry: " + id);
    }

    /** Resolve palettes together so generated biomes cannot silently share an identical building plan. */
    public static synchronized Map<ResourceLocation, Recipe> serverCatalog(Registry<Biome> biomes) {
        return CATALOGS.computeIfAbsent(biomes, GreenhouseRecipes::buildCatalog);
    }

    private static Map<ResourceLocation, Recipe> buildCatalog(Registry<Biome> biomes) {
        Set<ResourceLocation> custom = new LinkedHashSet<>();
        List<? extends String> rules = GreenhouseSettings.SPEC.isLoaded()
                ? GreenhouseSettings.BIOME_MATERIAL_RULES.get() : GreenhouseSettings.BIOME_MATERIAL_RULES.getDefault();
        for (String rule : rules) if (GreenhouseSettings.validateMaterialRule(rule)
                && GreenhouseSettings.parseRequirements(rule.split("=", 2)[1], GreenhouseSettings.requiredSpecimens())
                        .stream().allMatch(material -> usable(material.block())))
            custom.add(ResourceLocation.parse(rule.split("=", 2)[0].trim()));
        var ordered = biomes.listElements().sorted(Comparator
                .comparing((Holder.Reference<Biome> biome) -> !custom.contains(biome.key().location()))
                .thenComparing(biome -> !biome.key().location().getNamespace().equals("minecraft"))
                .thenComparing(biome -> biome.key().location().toString())).toList();
        Map<ResourceLocation, Recipe> catalog = new LinkedHashMap<>();
        Set<MaterialChoices> used = new LinkedHashSet<>();
        for (var biome : ordered) {
            ResourceLocation id = biome.key().location();
            Recipe recipe = RECIPES.computeIfAbsent(biome, GreenhouseRecipes::derive);
            if (!custom.contains(id) && used.contains(choices(recipe))) recipe = distinguish(biome, recipe, used);
            catalog.put(id, recipe);
            used.add(choices(recipe));
        }
        return Map.copyOf(catalog);
    }

    private static MaterialChoices choices(Recipe recipe) {
        // Quantities and temperature alone do not distinguish what players have to build.
        return new MaterialChoices(Set.copyOf(recipe.specimens()), Set.copyOf(recipe.floor().blocks()));
    }

    private static Recipe distinguish(Holder<Biome> biome, Recipe base, Set<MaterialChoices> used) {
        Set<ResourceLocation> nativeBlocks = new LinkedHashSet<>();
        biome.value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .flatMap(feature -> feature.value().getFeatures()).forEach(feature -> {
                    if (feature.config() instanceof TreeConfiguration tree) {
                        sample(tree.trunkProvider, nativeBlocks);
                        sample(tree.foliageProvider, nativeBlocks);
                    } else if (feature.config() instanceof SimpleBlockConfiguration simple) sample(simple.toPlace(), nativeBlocks);
                });
        ResourceLocation id = biome.unwrapKey().orElseThrow().location();
        String planet = nativePlanet(id);
        if (planet != null) {
            nativeBlocks.removeIf(block -> !block.getNamespace().equals(id.getNamespace())
                    || !block.getPath().startsWith(planet + "_"));
            BuiltInRegistries.BLOCK.keySet().stream().filter(block -> block.getNamespace().equals(id.getNamespace())
                    && block.getPath().startsWith(planet + "_")
                    && (block.getPath().contains("ore") || block.getPath().endsWith("_stone")
                    || block.getPath().endsWith("_gravel") || block.getPath().endsWith("_sand")))
                    .sorted().forEach(nativeBlocks::add);
        }
        Recipe nativeChoice = addDistinctMaterials(base, nativeBlocks, used);
        if (nativeChoice != null) return nativeChoice;
        // Some datapack biomes expose exactly the same vegetation. Use obtainable garden/rock accents,
        // never arbitrary machines, unplaceable blocks, or different amounts of the same material.
        if (planet == null) for (String block : List.of("mossy_cobblestone", "andesite", "diorite", "granite", "tuff",
                "calcite", "cobblestone", "moss_block", "azalea", "flowering_azalea", "allium", "cornflower",
                "oxeye_daisy", "blue_orchid", "lily_of_the_valley", "fern", "sunflower", "rose_bush", "lilac", "peony"))
            nativeBlocks.add(ResourceLocation.withDefaultNamespace(block));
        Recipe fallback = addDistinctMaterials(base, nativeBlocks, used);
        if (fallback == null) throw new IllegalStateException("No distinct greenhouse materials available for " + id);
        return fallback;
    }

    private static Recipe addDistinctMaterials(Recipe base, Set<ResourceLocation> candidates, Set<MaterialChoices> used) {
        var available = candidates.stream().filter(GreenhouseRecipes::usable)
                .filter(block -> !base.specimens().contains(block) && !base.floor().blocks().contains(block)).toList();
        for (int count = 1; count <= 2; count++) for (int i = 0; i < available.size(); i++) {
            for (int j = count == 1 ? i : i + 1; j < (count == 1 ? i + 1 : available.size()); j++) {
                List<MaterialRequirement> materials = new ArrayList<>(base.requirements());
                for (ResourceLocation block : count == 1 ? List.of(available.get(i)) : List.of(available.get(i), available.get(j)))
                    materials.add(new MaterialRequirement(block, 8, 16,
                            BuiltInRegistries.BLOCK.getValue(block) instanceof BushBlock ? 1 : 0));
                Recipe candidate = new Recipe(base.climate(), materials, base.floor());
                if (!used.contains(choices(candidate))) return candidate;
            }
        }
        return null;
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

    public static synchronized void clearCache() {
        RECIPES.clear();
        CATALOGS.clear();
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

    public static int scaledCount(MaterialRequirement material, int roomVolume, int floorArea, int referenceVolume) {
        if (floorArea < 0 || floorArea > 1048576) throw new IllegalArgumentException("Invalid greenhouse floor area");
        validateScale(material.count(), roomVolume, referenceVolume);
        if (!isPercentageBased(material)) return scaledCount(material.count(), roomVolume, referenceVolume);
        int volume = roomVolume == 0 ? referenceVolume : roomVolume;
        long percent = Math.round(materialPercent(material, referenceVolume));
        return (int) ((percent * volume + 99) / 100);
    }

    public static boolean isPercentageBased(MaterialRequirement material) { return material.floorDivisor() > 0; }

    public static double materialPercent(MaterialRequirement material, int referenceVolume) {
        validateScale(material.count(), 0, referenceVolume);
        return Math.max(1L, Math.round(material.count() * 100D / referenceVolume));
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
        if (override != null) {
            if (override.stream().allMatch(material -> usable(material.block())))
                return new Recipe(climate, override, floorFor(id, climate, true));
            BiomeBasedCrops.LOGGER.warn("Ignoring greenhouse material override for {}: it contains an unavailable player placement item", id);
        }
        String planet = nativePlanet(id);
        if (planet != null) {
            List<MaterialRequirement> nativeRock = new ArrayList<>();
            for (String kind : List.of("stone", "deep_stone", "gravel")) {
                ResourceLocation block = ResourceLocation.fromNamespaceAndPath("northstar", planet + "_" + kind);
                if (usable(block)) nativeRock.add(new MaterialRequirement(block, 16));
            }
            if (!nativeRock.isEmpty()) return habitat(climate, nativeRock, id);
        }
        if (id.getNamespace().equals("minecraft") && VANILLA.containsKey(id.getPath())) {
            List<MaterialRequirement> materials = VANILLA.get(id.getPath());
            return habitat(climate, materials, id);
        }
        if (id.getNamespace().equals("biomesoplenty") && BOP.containsKey(id.getPath())) {
            var materials = BOP.get(id.getPath());
            if (materials.stream().allMatch(material -> usable(material.block()))) return habitat(climate, materials, id);
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
                .limit(2).forEach(selected::add);
        selected.add(substrate(id.getPath(), climate));
        for (String fallback : List.of("stone", "dirt", "moss_block", "gravel")) {
            if (selected.size() >= 3) break;
            selected.add(ResourceLocation.withDefaultNamespace(fallback));
        }
        int count = GreenhouseSettings.requiredSpecimens();
        return habitat(climate, selected.stream().limit(5).map(block -> new MaterialRequirement(block, count)).toList(), id);
    }

    private static Recipe habitat(CropClimate climate, List<MaterialRequirement> materials, ResourceLocation biome) {
        FloorRequirement floor = floorFor(biome, climate, false);
        List<MaterialRequirement> abundant = new ArrayList<>();
        boolean firstLog = true;
        for (MaterialRequirement material : materials) {
            if (floor.blocks().contains(material.block())) continue;
            String path = material.block().getPath();
            boolean log = path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae");
            boolean canopy = (path.endsWith("_leaves") && !path.equals("dead_leaves")) || path.endsWith("wart_block");
            boolean cactus = path.equals("cactus");
            boolean plant = BuiltInRegistries.BLOCK.getValue(material.block()) instanceof BushBlock
                    || cactus || path.contains("flower") || path.contains("sapling") || path.contains("grass") && !path.endsWith("_block")
                    || path.contains("fern") || path.contains("mushroom") || path.contains("fungus") || path.contains("vine")
                    || path.contains("petals") || Set.of("dandelion", "poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
                            "lily_pad", "cocoa", "bamboo", "sugar_cane", "dead_bush", "kelp", "seagrass", "sea_pickle", "dead_leaves").contains(path);
            int minimum = canopy ? 64 : log ? (firstLog ? 16 : 8) : cactus ? 3 : plant ? 8 : 16;
            abundant.add(new MaterialRequirement(material.block(), Math.max(material.count(), minimum),
                    canopy ? 2 : log ? 8 : 16,
                    canopy ? 2 : log || plant ? 1 : 0));
            if (log) firstLog = false;
        }
        if (abundant.isEmpty()) abundant.add(new MaterialRequirement(ResourceLocation.withDefaultNamespace("gravel"), 16, 8, 0));
        return new Recipe(climate, abundant, floor);
    }

    private static String nativePlanet(ResourceLocation biome) {
        if (!biome.getNamespace().equals("northstar")) return null;
        String path = biome.getPath();
        if (path.startsWith("lunar_")) return "moon";
        if (path.startsWith("martian_")) return "mars";
        if (path.startsWith("mercury_")) return "mercury";
        if (path.startsWith("venusian_") || path.startsWith("venus_")) return "venus";
        return null;
    }

    private static FloorRequirement floorFor(ResourceLocation biome, CropClimate climate, boolean customMaterials) {
        FloorRequirement configured = GreenhouseSettings.floorOverride(biome);
        if (configured != null) {
            var available = configured.blocks().stream().filter(GreenhouseRecipes::usable).toList();
            if (!available.isEmpty()) return new FloorRequirement(available, configured.coveragePercent());
            BiomeBasedCrops.LOGGER.warn("Ignoring greenhouse floor override for {}: no obtainable placement blocks remain", biome);
        }
        if (customMaterials) return FloorRequirement.none();
        String path = biome.getPath();
        if (biome.toString().equals("biomesoplenty:visceral_heap")) return floor(90, "biomesoplenty:flesh");
        if (biome.toString().equals("biomesoplenty:end_wilds")) return floor(100, "biomesoplenty:algal_end_stone");
        if (biome.toString().equals("biomesoplenty:end_corruption")) return floor(100, "biomesoplenty:unmapped_end_stone");
        String nativePlanet = switch (biome.toString()) {
            case "northstar:lunar_plains", "northstar:lunar_hills", "northstar:lunar_crater_fields" -> "northstar:moon_sand";
            case "northstar:martian_dunes" -> "northstar:mars_sand";
            case "northstar:martian_highlands" -> "northstar:mars_soil,northstar:mars_gravel";
            case "northstar:martian_peaks" -> "minecraft:snow_block";
            case "northstar:mercury_basins" -> "northstar:mercury_stone";
            case "northstar:venusian_plains" -> "northstar:venus_stone";
            case "northstar:venusian_wastes" -> "northstar:volcanic_ash";
            default -> null;
        };
        if (nativePlanet != null) return floor(100, nativePlanet);
        String planet = nativePlanet(biome);
        if (planet != null) return floor(100, "northstar:" + planet + (path.contains("cave") ? "_deep_stone" : "_stone"));
        if (path.equals("desert") || path.contains("dune") || path.contains("desert")) return floor(100, "sand");
        if (path.contains("badland")) return floor(90, "red_sand,terracotta,orange_terracotta,yellow_terracotta,white_terracotta,coarse_dirt");
        if (path.equals("warped_forest")) return floor(90, "warped_nylium");
        if (path.equals("crimson_forest")) return floor(90, "crimson_nylium");
        if (path.equals("soul_sand_valley")) return floor(100, "soul_sand,soul_soil");
        if (path.equals("basalt_deltas")) return floor(90, "basalt,blackstone");
        if (path.contains("nether") || path.contains("hell")) return floor(100, "netherrack");
        if (path.equals("the_end") || path.startsWith("end_") || path.equals("small_end_islands")) return floor(100, "end_stone");
        if (path.contains("mushroom")) return floor(90, "mycelium");
        if (path.equals("lush_caves")) return floor(90, "moss_block,clay,dirt");
        if (path.equals("deep_dark")) return floor(90, "sculk,deepslate");
        if (path.contains("cave") || path.contains("peak") || path.contains("mountain") || path.contains("stony") || path.equals("the_void"))
            return floor(90, "stone,deepslate,dripstone_block,calcite,snow_block");
        if (path.contains("ocean") || path.contains("river")) return floor(90, "sand,gravel,clay,stone");
        if (path.contains("beach")) return floor(90, "sand,gravel");
        if (path.contains("mangrove")) return floor(90, "mud,muddy_mangrove_roots,clay");
        if (path.contains("swamp") || path.contains("bog") || path.contains("bayou") || path.contains("wetland")) return floor(90, "mud,grass_block,dirt,clay");
        if (path.contains("old_growth")) return floor(90, "podzol,coarse_dirt,grass_block");
        if (path.contains("taiga")) return floor(90, "podzol,grass_block,coarse_dirt,snow_block");
        if (climate == CropClimate.ICY) return floor(90, "snow_block,packed_ice,grass_block,dirt");
        return floor(90, "grass_block,dirt,rooted_dirt,coarse_dirt");
    }

    private static FloorRequirement floor(int percent, String blocks) {
        return new FloorRequirement(java.util.Arrays.stream(blocks.split(","))
                .map(block -> ResourceLocation.parse(block.contains(":") ? block : "minecraft:" + block)).toList(), percent);
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
        for (int seed = 0; seed < 32; seed++) {
            try {
                ResourceLocation block = BuiltInRegistries.BLOCK.getKey(provider.getState(RandomSource.create(seed),
                        new BlockPos(seed * 37, 64, seed * -53)).getBlock());
                if (block.equals(ResourceLocation.withDefaultNamespace("tall_grass"))) block = ResourceLocation.withDefaultNamespace("short_grass");
                if (block.equals(ResourceLocation.withDefaultNamespace("large_fern"))) block = ResourceLocation.withDefaultNamespace("fern");
                if (usable(block)) output.add(block);
            } catch (RuntimeException exception) {
                BiomeBasedCrops.LOGGER.debug("Could not sample greenhouse specimen provider", exception);
                return;
            }
        }
    }

    static boolean usable(ResourceLocation id) {
        if (id.getNamespace().equals("minecraft") && UNCOLLECTIBLE.contains(id.getPath())) return false;
        return BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.asItem() instanceof BlockItem item
                && item.getBlock() == block).orElse(false);
    }

    /** Distinct native vegetation and geology from BOP's generated biome features. */
    private static Map<String, List<MaterialRequirement>> biomesOPlentyRecipes() {
        Map<String, List<MaterialRequirement>> recipes = new LinkedHashMap<>();
        bop(recipes, "aspen_glade", "minecraft:birch_log*8,yellow_maple_leaves*4,reed*4");
        bop(recipes, "auroral_garden", "minecraft:birch_log*8,rainbow_birch_leaves*4,icy_iris*4,violet*4");
        bop(recipes, "bayou", "willow_log*8,willow_leaves*4,cattail*4");
        bop(recipes, "bog", "minecraft:mangrove_log*8,bush*4,reed*4");
        bop(recipes, "cold_desert", "dead_grass*4,desert_grass*4,minecraft:gravel*4");
        bop(recipes, "coniferous_forest", "fir_log*8,fir_leaves*4,toadstool*4");
        bop(recipes, "crag", "minecraft:andesite*8,minecraft:mossy_cobblestone*4,minecraft:short_grass*4");
        bop(recipes, "crystalline_chasm", "rose_quartz_block*8,rose_quartz_cluster*4,minecraft:blackstone*4");
        bop(recipes, "dead_forest", "dead_log*8,dead_leaves*4,dead_branch*4");
        bop(recipes, "dryland", "pine_log*8,pine_leaves*4,tiny_cactus*4");
        bop(recipes, "dune_beach", "dune_grass*4,sea_oats*4");
        bop(recipes, "end_corruption", "null_block*8,null_leaves*4,null_plant*4");
        bop(recipes, "end_reef", "minecraft:dead_brain_coral_block*8,barnacles*4,wispjelly*4");
        bop(recipes, "end_wilds", "empyreal_log*8,empyreal_leaves*4,endbloom*4,enderphyte*4");
        bop(recipes, "erupting_inferno", "brimstone*8,brimstone_cluster*4,minecraft:magma_block*4");
        bop(recipes, "field", "white_lavender*4,wildflower*4,minecraft:rose_bush*4");
        bop(recipes, "fir_clearing", "fir_log*8,fir_leaves*4,minecraft:fern*4");
        bop(recipes, "floodplain", "minecraft:oak_log*8,waterlily*4,watergrass*4,orange_cosmos*4");
        bop(recipes, "forested_field", "minecraft:oak_log*8,minecraft:oak_leaves*4,wildflower*4,sprout*4");
        bop(recipes, "fungal_jungle", "minecraft:jungle_log*8,minecraft:jungle_leaves*4,toadstool*4,toadstool_block*4");
        bop(recipes, "glowing_grotto", "glowing_moss_block*8,glowshroom*4,glowworm_silk*4");
        bop(recipes, "grassland", "minecraft:short_grass*8,minecraft:dandelion*4");
        bop(recipes, "gravel_beach", "minecraft:stone*8,minecraft:andesite*4,minecraft:cobblestone*4");
        bop(recipes, "highland", "minecraft:cobblestone*8,minecraft:short_grass*4,minecraft:granite*4");
        bop(recipes, "hot_springs", "pine_log*8,pine_leaves*4,thermal_calcite*4");
        bop(recipes, "jacaranda_glade", "jacaranda_log*8,jacaranda_leaves*4,minecraft:lily_of_the_valley*4");
        bop(recipes, "jade_cliffs", "pine_log*8,pine_leaves*4,minecraft:spruce_log*4");
        bop(recipes, "lavender_field", "jacaranda_log*8,jacaranda_leaves*4,lavender*4,tall_lavender*4");
        bop(recipes, "lush_desert", "palm_log*8,palm_leaves*4,desert_grass*4,wildflower*4");
        bop(recipes, "lush_savanna", "minecraft:poppy*4,minecraft:rose_bush*4,sprout*4");
        bop(recipes, "maple_woods", "maple_log*8,red_maple_leaves*4,violet*4");
        bop(recipes, "marsh", "reed*4,watergrass*8");
        bop(recipes, "mediterranean_forest", "minecraft:spruce_log*8,cypress_leaves*4,minecraft:peony*4");
        bop(recipes, "moor", "violet*4,minecraft:allium*4,minecraft:short_grass*4");
        bop(recipes, "muskeg", "dead_log*8,dead_branch*4,minecraft:brown_mushroom*4");
        bop(recipes, "mystic_grove", "magic_log*8,magic_leaves*4,glowflower*4,blue_hydrangea*4");
        bop(recipes, "old_growth_dead_forest", "dead_log*8,dead_leaves*4,dead_branch*4,minecraft:vine*4");
        bop(recipes, "old_growth_woodland", "minecraft:oak_log*8,minecraft:oak_leaves*4,minecraft:rose_bush*4");
        bop(recipes, "ominous_woods", "umbran_log*8,umbran_leaves*8,dead_leaves*4,umbran_wood*4");
        bop(recipes, "orchard", "minecraft:oak_log*8,flowering_oak_leaves*4,clover*4");
        bop(recipes, "origin_valley", "minecraft:oak_log*8,origin_leaves*4,rose*4");
        bop(recipes, "overgrown_greens", "clover*8,pink_daffodil*4,minecraft:azure_bluet*4");
        bop(recipes, "pasture", "barley*8,minecraft:short_grass*4");
        bop(recipes, "prairie", "minecraft:oak_log*8,goldenrod*4,minecraft:cornflower*4");
        bop(recipes, "pumpkin_patch", "maple_log*8,orange_maple_leaves*4,minecraft:pumpkin*4,toadstool*4");
        bop(recipes, "rainforest", "mahogany_log*8,mahogany_leaves*4,orange_cosmos*4,minecraft:big_dripleaf*4");
        bop(recipes, "redwood_forest", "redwood_log*8,redwood_leaves*4,minecraft:fern*4");
        bop(recipes, "rocky_rainforest", "mahogany_log*8,mahogany_leaves*4,orange_cosmos*4,minecraft:jungle_log*4,minecraft:andesite*4");
        bop(recipes, "rocky_shrubland", "minecraft:oak_log*8,bush*4,minecraft:allium*4,minecraft:andesite*4");
        bop(recipes, "scrubland", "minecraft:acacia_log*8,minecraft:acacia_leaves*4,desert_grass*4,wildflower*4");
        bop(recipes, "seasonal_forest", "maple_log*8,orange_maple_leaves*4,red_maple_leaves*4,yellow_maple_leaves*4");
        bop(recipes, "shrubland", "minecraft:oak_log*8,bush*4,minecraft:allium*4,minecraft:lilac*4");
        bop(recipes, "snowblossom_grove", "minecraft:cherry_log*8,snowblossom_leaves*4,white_petals*4");
        bop(recipes, "snowy_coniferous_forest", "fir_log*8,fir_leaves*4,toadstool*4,violet*4");
        bop(recipes, "snowy_fir_clearing", "fir_log*8,fir_leaves*4,minecraft:fern*4,minecraft:ice*4");
        bop(recipes, "snowy_maple_woods", "maple_log*8,red_maple_leaves*4,violet*4,minecraft:ice*4");
        bop(recipes, "spider_nest", "minecraft:cobweb*8,spider_egg*4,webbing*4");
        bop(recipes, "tropics", "palm_log*8,palm_leaves*4,pink_hibiscus*4,blue_hydrangea*4");
        bop(recipes, "tundra", "dead_log*8,tundra_shrub*4,reed*4");
        bop(recipes, "undergrowth", "hellbark_log*8,hellbark_leaves*4,burning_blossom*4");
        bop(recipes, "visceral_heap", "minecraft:bone_block*8,porous_flesh*4,eyebulb*4,hair*4");
        bop(recipes, "volcanic_plains", "minecraft:dark_oak_log*8,mossy_black_sand*4,sprout*4");
        bop(recipes, "volcano", "black_sandstone*8,minecraft:smooth_basalt*4,minecraft:magma_block*4");
        bop(recipes, "wasteland", "dead_log*8,dead_grass*4,wilted_lily*4");
        bop(recipes, "wasteland_steppe", "dead_grass*4,desert_grass*4");
        bop(recipes, "wetland", "willow_log*8,willow_leaves*4,reed*4,toadstool*4");
        bop(recipes, "wintry_origin_valley", "minecraft:oak_log*8,origin_leaves*4,rose*4,minecraft:ice*4");
        bop(recipes, "withered_abyss", "blackstone_spines*8,blackstone_bulb*4,minecraft:obsidian*4");
        bop(recipes, "woodland", "minecraft:oak_log*8,minecraft:oxeye_daisy*4,minecraft:poppy*4");
        return Map.copyOf(recipes);
    }

    private static void bop(Map<String, List<MaterialRequirement>> recipes, String biome, String raw) {
        put(recipes, "biomesoplenty", biome, raw);
    }

    private static Map<String, List<MaterialRequirement>> vanillaRecipes() {
        Map<String, List<MaterialRequirement>> recipes = new LinkedHashMap<>();
        put(recipes, "the_void", "stone*8,smooth_stone*4,cobblestone*2");
        put(recipes, "plains", "grass_block*8,dandelion*2,poppy*2");
        put(recipes, "sunflower_plains", "grass_block*8,sunflower*2,poppy*2");
        put(recipes, "snowy_plains", "snow_block*8,grass_block*4,ice*2");
        put(recipes, "ice_spikes", "packed_ice*8,snow_block*4,blue_ice*2");
        put(recipes, "desert", "sand*8,sandstone*4,cactus*2,dead_bush*4");
        put(recipes, "swamp", "mud*8,oak_log*4,lily_pad*2,brown_mushroom*2");
        put(recipes, "mangrove_swamp", "mangrove_log*8,mangrove_leaves*4,mud*4,mangrove_roots*2");
        put(recipes, "forest", "oak_log*8,oak_leaves*4,birch_log*2");
        put(recipes, "flower_forest", "oak_log*4,allium*2,azure_bluet*2,oxeye_daisy*2");
        put(recipes, "birch_forest", "birch_log*8,birch_leaves*4,grass_block*4");
        put(recipes, "dark_forest", "dark_oak_log*8,dark_oak_leaves*4,red_mushroom_block*2");
        put(recipes, "old_growth_birch_forest", "birch_log*8,birch_leaves*4,short_grass*4,fern*2");
        put(recipes, "old_growth_pine_taiga", "spruce_log*8,podzol*8,mossy_cobblestone*4,brown_mushroom*2");
        put(recipes, "old_growth_spruce_taiga", "spruce_log*8,spruce_leaves*8,podzol*8,fern*2,brown_mushroom*2");
        put(recipes, "taiga", "spruce_log*8,spruce_leaves*4,fern*4");
        put(recipes, "snowy_taiga", "spruce_log*8,spruce_leaves*4,snow_block*4");
        put(recipes, "savanna", "acacia_log*8,acacia_leaves*4,short_grass*4");
        put(recipes, "savanna_plateau", "acacia_log*8,acacia_leaves*4,coarse_dirt*4,short_grass*2");
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
        put(recipes, "lukewarm_ocean", "sand*8,seagrass*4,kelp*2,granite*4");
        put(recipes, "deep_lukewarm_ocean", "sand*8,stone*4,seagrass*4,kelp*4,prismarine*4");
        put(recipes, "ocean", "sand*4,gravel*8,kelp*4,tuff*4");
        put(recipes, "deep_ocean", "stone*8,gravel*4,kelp*4,prismarine*4");
        put(recipes, "cold_ocean", "gravel*8,kelp*4,seagrass*2,andesite*4");
        put(recipes, "deep_cold_ocean", "stone*8,gravel*8,kelp*4,seagrass*2,dark_prismarine*4");
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
        put(recipes, "end_highlands", "end_stone*8,chorus_flower*4,purpur_block*4");
        put(recipes, "end_midlands", "end_stone*8,chorus_flower*2,end_stone_bricks*4");
        put(recipes, "small_end_islands", "end_stone*8,obsidian*2,end_stone_brick_slab*4");
        put(recipes, "end_barrens", "end_stone*8,end_stone_bricks*4,purpur_pillar*2");
        return Map.copyOf(recipes);
    }

    private static void put(Map<String, List<MaterialRequirement>> recipes, String biome, String raw) {
        put(recipes, "minecraft", biome, raw);
    }

    private static void put(Map<String, List<MaterialRequirement>> recipes, String namespace, String biome, String raw) {
        List<MaterialRequirement> requirements = new ArrayList<>();
        for (String token : raw.split(",")) {
            String[] pair = token.split("\\*");
            requirements.add(requirement(pair[0].contains(":") ? pair[0] : namespace + ":" + pair[0], Integer.parseInt(pair[1])));
        }
        recipes.put(biome, List.copyOf(requirements));
    }

    private static MaterialRequirement requirement(String block, int count) {
        return new MaterialRequirement(ResourceLocation.parse(block), count);
    }

    private record MaterialChoices(Set<ResourceLocation> features, Set<ResourceLocation> floor) {}

    public record MaterialRequirement(ResourceLocation block, int count, int floorDivisor, int minHeight) {
        public MaterialRequirement {
            Objects.requireNonNull(block, "block");
            if (count < 1 || count > 64) throw new IllegalArgumentException("A material requirement must contain 1 to 64 blocks");
            if (floorDivisor < 0 || floorDivisor > 256 || minHeight < 0 || minHeight > 2)
                throw new IllegalArgumentException("Invalid greenhouse material placement");
        }
        public MaterialRequirement(ResourceLocation block, int count) { this(block, count, 0, 0); }
        public MaterialRequirement(ResourceLocation block, int count, int floorDivisor) { this(block, count, floorDivisor, 0); }
    }

    public record FloorRequirement(List<ResourceLocation> blocks, int coveragePercent) {
        public FloorRequirement {
            blocks = List.copyOf(new LinkedHashSet<>(blocks));
            if (blocks.size() > 16 || coveragePercent < 0 || coveragePercent > 100 || coveragePercent > 0 && blocks.isEmpty())
                throw new IllegalArgumentException("Invalid greenhouse native floor");
        }
        public static FloorRequirement none() { return new FloorRequirement(List.of(), 0); }
        public int required(int area) { return (int) (((long) area * coveragePercent + 99) / 100); }
    }

    public record Recipe(CropClimate climate, List<ResourceLocation> specimens, int count,
                         List<MaterialRequirement> requirements, FloorRequirement floor) {
        public Recipe {
            Objects.requireNonNull(climate, "climate");
            Objects.requireNonNull(floor, "floor");
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
            this(climate, requirements, FloorRequirement.none());
        }

        public Recipe(CropClimate climate, List<MaterialRequirement> requirements, FloorRequirement floor) {
            this(climate, List.of(), 0, requirements, floor);
        }

        public Recipe(CropClimate climate, List<ResourceLocation> specimens, int count, List<MaterialRequirement> requirements) {
            this(climate, specimens, count, requirements, FloorRequirement.none());
        }

        public Recipe(CropClimate climate, List<ResourceLocation> specimens, int count) {
            this(climate, specimens, count, specimens.stream().map(block -> new MaterialRequirement(block, count)).toList(), FloorRequirement.none());
        }
    }
}
