package com.ourcreation.biomebasedcrops.client;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonParser;
import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Uses the installed mods' English names regardless of the player's selected language. */
public final class EnglishGuideText {
    private static final Map<String, Map<String, String>> LANGUAGES = new HashMap<>();

    private EnglishGuideText() {
    }

    public static Component biomeName(ResourceLocation id) {
        return name(id.getNamespace(), "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.getPath());
    }

    public static Component blockName(ResourceLocation id) {
        String key = BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.getDescriptionId())
                .orElse("block." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        return name(id.getNamespace(), key, id.getPath());
    }

    public static Component climateName(CropClimate climate) {
        return Component.literal(titleCase(climate.name()));
    }

    public static String targetTemperatures(CropClimate climate) {
        return switch (climate) {
            case ICY -> "0\u00B0C or colder";
            case COLD -> "1-10\u00B0C";
            case NEUTRAL -> "11-25\u00B0C";
            case WARM -> "26-35\u00B0C";
            case HOT -> "36\u00B0C or warmer";
        };
    }

    public static void clearCache() {
        LANGUAGES.clear();
    }

    private static Component name(String namespace, String key, String fallback) {
        return Component.literal(LANGUAGES.computeIfAbsent(namespace, EnglishGuideText::load)
                .getOrDefault(key, titleCase(fallback)));
    }

    private static Map<String, String> load(String namespace) {
        Map<String, String> names = new HashMap<>();
        var resources = Minecraft.getInstance().getResourceManager();
        for (var resource : resources.getResourceStack(ResourceLocation.fromNamespaceAndPath(namespace, "lang/en_us.json"))) {
            try (Reader reader = resource.openAsReader()) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                        names.put(entry.getKey(), entry.getValue().getAsString());
                    }
                });
            } catch (IOException | RuntimeException exception) {
                com.ourcreation.biomebasedcrops.BiomeBasedCrops.LOGGER.debug("Could not read English guide names for {}", namespace, exception);
            }
        }
        return Map.copyOf(names);
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
