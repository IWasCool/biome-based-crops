package com.ourcreation.biomebasedcrops;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class CropMatcher {
    private static final Set<String> PRODUCE_NAMES = Set.of(
            "wheat", "rice", "cabbage", "tomato", "onion", "beetroot", "potato", "carrot",
            "pumpkin", "melon", "cocoa", "tea", "coffee", "grape", "corn", "pepper",
            "barley", "oat", "rye", "soybean", "garlic", "lettuce", "flax", "hop", "hops",
            "cucumber", "cotton", "eggplant", "peanut", "pineapple", "chili", "strawberry",
            "cranberry", "blueberry", "blackberry", "raspberry", "sweet_berry", "glow_berry");
    private static final Set<String> PRODUCE_PARTS = Set.of(
            "seed", "seeds", "bean", "beans", "leaf", "leaves", "grain", "grains", "fruit", "pod", "slice");
    private static final Set<String> CROP_PARTS = Set.of(
            "crop", "crops", "seed", "seeds", "plant", "plants", "bush", "stem", "berry", "berries");
    // These cultivable Northstar plants use custom block classes and names that do
    // not identify them as crops. Keep the exception exact to avoid decorative blocks.
    private static final Set<String> NORTHSTAR_CROPS = Set.of(
            "mars_palm", "mars_sprout", "mars_sprout_big", "mars_tulip", "venus_vines", "glowing_venus_vines");
    private static volatile DetectionCache detectionCache;

    private CropMatcher() {
    }

    public static Optional<CropProfile> profileForItem(ItemStack stack) {
        if (stack.isEmpty() || !Config.SPEC.isLoaded()) {
            return Optional.empty();
        }

        DetectionCache cache = cache();
        return cache.items.computeIfAbsent(stack.getItem(), item -> detectItem(item, cache))
                .filter(plant -> isCategoryEnabled(plant.category()))
                .map(plant -> CropClimateRules.profileFor(plant.blockId(), plant.itemId()));
    }

    public static Optional<CropProfile> profileForState(BlockState state) {
        if (!Config.SPEC.isLoaded()) {
            return Optional.empty();
        }
        return categoryForState(state).filter(CropMatcher::isCategoryEnabled).map(category -> {
            Block block = state.getBlock();
            // The body of a cave vine has no item, but shares the head's glow-berry climate rules.
            Item item = block instanceof CaveVines ? Items.GLOW_BERRIES : block.asItem();
            return CropClimateRules.profileFor(BuiltInRegistries.BLOCK.getKey(block), BuiltInRegistries.ITEM.getKey(item));
        });
    }

    public static boolean canGrowAt(Level level, BlockPos pos, BlockState state) {
        return profileForState(state)
                .map(profile -> CommonEvents.canGrow(level, pos, profile))
                .orElse(true);
    }

    public static boolean isCropBlock(BlockState state) {
        return categoryForState(state).orElse(null) == PlantCategory.CROP;
    }

    public static boolean isSaplingBlock(BlockState state) {
        return categoryForState(state).orElse(null) == PlantCategory.SAPLING;
    }

    public static boolean isThermometer(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("toughasnails") && id.getPath().contains("thermometer");
    }

    public static boolean isPlantDependencyEnabled(BlockState state) {
        return Config.SPEC.isLoaded() && categoryForState(state).map(CropMatcher::isCategoryEnabled).orElse(false);
    }

    public static Optional<PlantCategory> categoryForState(BlockState state) {
        DetectionCache cache = cache();
        return cache.blocks.computeIfAbsent(state.getBlock(), block -> detectBlock(block, cache));
    }

    /** Discard namespace-dependent classifications when the server config changes. */
    public static synchronized void clearCaches() {
        detectionCache = null;
    }

    private static Optional<PlantIdentity> detectItem(Item item, DetectionCache cache) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            Optional<PlantCategory> category = cache.blocks.computeIfAbsent(block, value -> detectBlock(value, cache));
            if (category.isPresent()) {
                return Optional.of(new PlantIdentity(category.get(), BuiltInRegistries.BLOCK.getKey(block), itemId));
            }
        }

        // Only raw produce/seed names qualify here. Substrings such as "tea" in steak,
        // "oat" in boats, or "rice" in prepared meals are not evidence of a crop.
        if (cache.isSupportedNamespace(itemId.getNamespace()) && looksRawCropItemLike(itemId.getPath())) {
            return Optional.of(new PlantIdentity(PlantCategory.CROP, itemId, itemId));
        }

        return Optional.empty();
    }

    private static Optional<PlantCategory> detectBlock(Block block, DetectionCache cache) {
        if (block instanceof SaplingBlock
                || block instanceof BambooSaplingBlock
                || block instanceof BambooStalkBlock
                || block instanceof CactusBlock
                || block instanceof SugarCaneBlock
                || block instanceof MushroomBlock
                || block instanceof FungusBlock
                || block instanceof AzaleaBlock) {
            return Optional.of(PlantCategory.SAPLING);
        }

        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SweetBerryBushBlock
                || block instanceof CocoaBlock || block instanceof NetherWartBlock || block instanceof CaveVines) {
            return Optional.of(PlantCategory.CROP);
        }

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (!cache.isSupportedNamespace(id.getNamespace())) {
            return Optional.empty();
        }

        // Require growable behavior: bamboo planks and huge mushroom blocks are not saplings.
        if (block instanceof BonemealableBlock && looksSaplingLike(id.getPath())) {
            return Optional.of(PlantCategory.SAPLING);
        }

        // Custom rice/tomatoes need not extend CropBlock; wild crops can duplicate
        // with bonemeal even when they have no age property.
        boolean bonemealablePlant = block instanceof BonemealableBlock && block instanceof BushBlock;
        boolean agingPlant = (block instanceof BonemealableBlock || block instanceof BushBlock)
                && hasAgeProperty(block.defaultBlockState());
        boolean knownCustomCrop = id.getNamespace().equals("northstar") && NORTHSTAR_CROPS.contains(id.getPath());
        if ((bonemealablePlant || agingPlant) && (knownCustomCrop || looksCropLike(id.getPath()))) {
            return Optional.of(PlantCategory.CROP);
        }

        return Optional.empty();
    }

    private static boolean hasAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty && property.getName().equals("age")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCategoryEnabled(PlantCategory category) {
        return switch (category) {
            case CROP -> Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
            case SAPLING -> Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        };
    }

    static boolean looksRawCropItemLike(String path) {
        if (isProduceName(path) || path.equals("nether_wart")) {
            return true;
        }
        int separator = path.lastIndexOf('_');
        if (separator < 0) {
            return false;
        }
        String part = path.substring(separator + 1);
        String crop = path.substring(0, separator);
        return PRODUCE_PARTS.contains(part) && isProduceName(crop);
    }

    private static boolean looksCropLike(String path) {
        for (String word : path.split("_")) {
            if (CROP_PARTS.contains(word) || isProduceName(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProduceName(String name) {
        return PRODUCE_NAMES.contains(name)
                || (name.endsWith("ies") && PRODUCE_NAMES.contains(name.substring(0, name.length() - 3) + "y"))
                || (name.endsWith("oes") && PRODUCE_NAMES.contains(name.substring(0, name.length() - 2)))
                || (name.endsWith("s") && PRODUCE_NAMES.contains(name.substring(0, name.length() - 1)));
    }

    private static boolean looksSaplingLike(String path) {
        return path.contains("sapling") || path.contains("bamboo") || path.contains("cactus")
                || path.contains("sugar_cane") || path.contains("mushroom") || path.contains("fungus")
                || path.contains("azalea") || path.contains("tree_seed");
    }

    private static DetectionCache cache() {
        DetectionCache cache = detectionCache;
        if (cache == null) {
            synchronized (CropMatcher.class) {
                cache = detectionCache;
                if (cache == null) {
                    cache = new DetectionCache();
                    detectionCache = cache;
                }
            }
        }
        return cache;
    }

    private static final class DetectionCache {
        private final Map<Block, Optional<PlantCategory>> blocks = new ConcurrentHashMap<>();
        private final Map<Item, Optional<PlantIdentity>> items = new ConcurrentHashMap<>();
        private final Set<String> namespaces = new HashSet<>();

        private DetectionCache() {
            for (String namespace : Config.SPEC.isLoaded()
                    ? Config.SUPPORTED_CROP_NAMESPACES.get() : Config.SUPPORTED_CROP_NAMESPACES.getDefault()) {
                namespaces.add(namespace.trim().toLowerCase(Locale.ROOT));
            }
        }

        private boolean isSupportedNamespace(String namespace) {
            return namespace.contains("delight") || namespaces.contains(namespace);
        }
    }

    private record PlantIdentity(PlantCategory category, ResourceLocation blockId, ResourceLocation itemId) {
    }
}
