package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertTrue(english.keySet().containsAll(Set.of(
                "biome_based_crops.guide.title", "biome_based_crops.guide.find_biome_hint",
                "biome_based_crops.guide.required_climate", "biome_based_crops.guide.floor_blocks",
                "biome_based_crops.guide.interior_requirements", "biome_based_crops.guide.setup_text",
                "biome_based_crops.ac.previous_climate", "biome_based_crops.ac.next_climate",
                "biome_based_crops.ac.selector_usage")), "Guide and climate controls need translatable text");
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
                int count = arguments(text).stream().mapToInt(Integer::intValue).max().orElse(0);
                String.format(Locale.ROOT, text, Collections.nCopies(count, "value").toArray());
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

    @Test
    void recentGuideAndClimateMessagesUseLocalizedText() throws IOException {
        JsonObject english = read("en_us");
        for (String locale : List.of("de_de", "es_es", "fr_fr", "it_it", "pt_br", "ru_ru",
                "ja_jp", "ko_kr", "zh_cn", "ar_sa", "hi_in", "tr_tr")) {
            JsonObject language = read(locale);
            for (String key : List.of("biome_based_crops.guide.setup_text", "biome_based_crops.ac.selector_usage",
                    "biome_based_crops.ac.capacity_hint", "biome_based_crops.message.blocked")) {
                assertNotEquals(english.get(key).getAsString(), language.get(key).getAsString(),
                        locale + ": " + key + " should not fall back to English");
            }
        }
    }

    @Test
    void growthAndInstrumentMessagesUseClimateCategoriesWithoutDependencyHints() throws IOException {
        try (var paths = Files.list(LANG)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var language = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                assertFalse(language.has("biome_based_crops.message.blocked_with_thermoregulator"), path.toString());
                assertFalse(language.has("biome_based_crops.message.thermoregulator_nearby"), path.toString());
                assertFalse(language.has("biome_based_crops.message.temperature_reading"), path.toString());
                String reading = language.get("biome_based_crops.message.climate_reading").getAsString();
                assertFalse(reading.contains("Celsius") || reading.contains("Fahrenheit") || reading.contains("\u00B0"), path.toString());
            }
        }
        JsonObject english = read("en_us");
        for (String key : List.of("biome_based_crops.message.blocked", "biome_based_crops.message.blocked_biomes")) {
            String text = english.get(key).getAsString().toLowerCase(Locale.ROOT);
            assertTrue(text.contains("greenhouse"), key);
            assertFalse(text.contains("glass") || text.contains("sky"), key);
            assertFalse(text.contains("thermoregulator") || text.contains("tough as nails"), key);
        }
        assertEquals("Needs %s climate or a greenhouse.", english.get("biome_based_crops.message.blocked").getAsString());
        assertEquals("Needs a matching biome or greenhouse. See Field Guide.",
                english.get("biome_based_crops.message.blocked_biomes").getAsString());
        assertTrue(arguments(english.get("biome_based_crops.message.blocked_biomes").getAsString()).isEmpty());
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
