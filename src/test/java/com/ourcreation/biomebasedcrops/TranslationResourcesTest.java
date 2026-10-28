package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class TranslationResourcesTest {
    private static final Path PROJECT = Path.of(System.getProperty("biomeBasedCrops.projectDir", "."));
    private static final Path LANG = PROJECT.resolve("src/main/resources/assets/biome_based_crops/lang");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s");

    @Test
    void everyMinecraftLanguageHasCompleteUsableTranslations() throws IOException {
        // Minecraft 1.21.1 asset index 17, plus the bundled en_us language.
        var manifest = JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/test/resources/minecraft-1.21.1-locales.json"), StandardCharsets.UTF_8)).getAsJsonArray();
        Set<String> expected = manifest.asList().stream().map(value -> value.getAsString()).collect(Collectors.toSet());
        assertEquals(137, expected.size());
        assertEquals(expected.size(), manifest.size(), "Locale list must not contain duplicates");
        try (var paths = Files.list(LANG)) {
            Set<String> actual = paths.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .collect(Collectors.toSet());
            assertEquals(expected, actual, "Language files must match the Minecraft language list");
        }

        JsonObject english = read("en_us");
        for (String locale : expected) {
            JsonObject translated = read(locale);
            assertEquals(english.keySet(), translated.keySet(), locale + " translation keys");
            for (String key : english.keySet()) {
                var value = translated.get(key);
                assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), locale + ": " + key);
                String text = value.getAsString();
                String context = locale + ": " + key;
                assertFalse(text.isBlank(), context);
                assertFalse(text.contains("\uFFFD"), context + " contains a corrupt character");
                assertFalse(text.matches("(?s).*(?:ZXQ\\d|\\d{3}@@|99999[12]|__BBC_).*"), context + " contains a translation marker");
                assertEquals(arguments(english.get(key).getAsString()), arguments(text), context + " format arguments");
                assertFalse(ARGUMENT.matcher(text).replaceAll("").contains("%"), context + " invalid percent escape");
                // Formatting must accept both ordinary %s and reordered %1$s/%2$s.
                String.format(Locale.ROOT, text, "first", "second");
            }
            Set<String> climateNames = Set.of("icy", "cold", "neutral", "warm", "hot").stream()
                    .map(name -> translated.get("biome_based_crops.climate." + name).getAsString())
                    .collect(Collectors.toSet());
            assertEquals(5, climateNames.size(), locale + " must distinguish the five climates");
        }
    }

    private static JsonObject read(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Integer> arguments(String text) {
        var result = new ArrayList<Integer>();
        var matcher = ARGUMENT.matcher(text);
        int next = 1;
        while (matcher.find()) {
            result.add(matcher.group(1) == null ? next++ : Integer.parseInt(matcher.group(1)));
        }
        Collections.sort(result);
        return result;
    }
}
