package com.ourcreation.biomebasedcrops;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

public enum CropClimate {
    ICY("icy", "Icy", ChatFormatting.AQUA),
    COLD("cold", "Cold", ChatFormatting.BLUE),
    NEUTRAL("neutral", "Neutral", ChatFormatting.GRAY),
    WARM("warm", "Warm", ChatFormatting.GREEN),
    HOT("hot", "Hot", ChatFormatting.GOLD);

    // Also fixes the order when a datapack accidentally assigns conflicting climate tags.
    private static final CropClimate[] TAG_PRIORITY = {ICY, COLD, HOT, WARM, NEUTRAL};

    private final String id;
    private final String label;
    private final ChatFormatting color;
    private final TagKey<Biome> biomeTag;

    CropClimate(String id, String label, ChatFormatting color) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.biomeTag = TagKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("toughasnails", id + "_biomes"));
    }

    public static Optional<CropClimate> byId(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CropClimate climate : TAG_PRIORITY) {
            if (climate.id.equals(normalized)) {
                return Optional.of(climate);
            }
        }
        return Optional.empty();
    }

    public static CropClimate at(Holder<Biome> biome, BlockPos pos) {
        // BOP supplies these compatibility tags even when Tough As Nails is absent.
        for (CropClimate climate : TAG_PRIORITY) {
            if (biome.is(climate.biomeTag)) {
                return climate;
            }
        }

        // Use biome temperature rather than altitude/season, so tooltips and growth agree.
        float temperature = biome.value().getBaseTemperature();
        if (biome.is(Tags.Biomes.IS_SNOWY) || biome.is(Tags.Biomes.IS_ICY) || temperature < 0.15F) {
            return ICY;
        }
        if (biome.is(Tags.Biomes.IS_COLD) || temperature < 0.5F) {
            return COLD;
        }
        if (biome.is(Tags.Biomes.IS_DESERT)) {
            return HOT;
        }
        if (temperature <= 0.8F) {
            return NEUTRAL;
        }
        // c:is_hot means > 0.8, encompassing both our warm and hot bands.
        // Dryness is independent of temperature and must never imply heat.
        return temperature <= 1.0F ? WARM : HOT;
    }

    public boolean matches(Holder<Biome> biome, BlockPos pos) {
        return at(biome, pos) == this;
    }

    public MutableComponent component() {
        return Component.translatable("biome_based_crops.climate." + id).withStyle(color);
    }

    public String label() {
        return label;
    }
}
