package com.ourcreation.biomebasedcrops;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class BiomeSelector {
    private final String value;
    private final Predicate<Holder<Biome>> matcher;

    private BiomeSelector(String value, Predicate<Holder<Biome>> matcher) {
        this.value = value;
        this.matcher = matcher;
    }

    public static Optional<BiomeSelector> parse(String raw) {
        String value = raw.trim();
        if (value.equals("*")) {
            return Optional.of(new BiomeSelector(value, biome -> true));
        }
        boolean tag = value.startsWith("#");
        String name = tag ? value.substring(1) : value;
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (name.indexOf(':') < 1 || id == null || id.getPath().isEmpty()) {
            return Optional.empty();
        }
        Predicate<Holder<Biome>> matcher;
        if (tag) {
            TagKey<Biome> key = TagKey.create(Registries.BIOME, id);
            matcher = biome -> biome.is(key);
        } else {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
            matcher = biome -> biome.is(key);
        }
        return Optional.of(new BiomeSelector(value, matcher));
    }

    public boolean matches(Holder<Biome> biome) {
        return matcher.test(biome);
    }

    public Component component() {
        if (value.equals("*")) {
            return Component.translatable("biome_based_crops.biome.any");
        }
        if (value.startsWith("#")) {
            return Component.literal(value);
        }
        ResourceLocation id = ResourceLocation.parse(value);
        return Component.translatableWithFallback(
                "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'), value);
    }

    @Override
    public String toString() {
        return value;
    }
}
