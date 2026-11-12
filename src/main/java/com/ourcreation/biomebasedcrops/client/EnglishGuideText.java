package com.ourcreation.biomebasedcrops.client;

import java.util.Locale;

import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Resolves guide names through Minecraft's selected language, with readable ID fallbacks. */
public final class EnglishGuideText {
    private EnglishGuideText() {
    }

    public static Component biomeName(ResourceLocation id) {
        return name("biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.getPath());
    }

    public static Component blockName(ResourceLocation id) {
        String key = BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.getDescriptionId())
                .orElse("block." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        return name(key, id.getPath());
    }

    public static Component climateName(CropClimate climate) {
        return climate.component();
    }

    public static String requiredClimate(CropClimate climate) {
        return climate.component().getString();
    }

    public static void clearCache() {
        // Names resolve through Minecraft's language manager; there is no private language cache.
    }

    private static Component name(String key, String fallback) {
        return Component.translatableWithFallback(key, titleCase(fallback));
    }

    private static String titleCase(String path) {
        StringBuilder result = new StringBuilder();
        for (String word : path.toLowerCase(Locale.ROOT).replace('/', '_').split("_")) {
            if (!word.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }
}
