package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class AirConditionerArtworkTest {
    private static final Path ASSETS = Path.of(System.getProperty("biomeBasedCrops.projectDir", "."))
            .resolve("src/main/resources/assets/biome_based_crops");
    private static final List<String> CLIMATES = List.of("icy", "cold", "neutral", "warm", "hot");
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    @Test
    void everyFacingAndRunningStateDisplaysItsSelectedClimateAndKeepsTheFan() throws IOException {
        JsonObject variants = json(ASSETS.resolve("blockstates/air_conditioner.json")).getAsJsonObject("variants");
        assertEquals(CLIMATES.size() * FACINGS.size() * 2, variants.size());
        var coveredStates = new HashSet<Map<String, String>>();
        var frontByClimate = new HashMap<String, String>();

        for (var entry : variants.entrySet()) {
            Map<String, String> state = properties(entry.getKey());
            assertEquals(Set.of("climate", "facing", "active"), state.keySet(), entry.getKey());
            assertTrue(CLIMATES.contains(state.get("climate")), entry.getKey());
            assertTrue(FACINGS.contains(state.get("facing")), entry.getKey());
            assertTrue(Set.of("true", "false").contains(state.get("active")), entry.getKey());
            assertTrue(coveredStates.add(state), "Duplicate state: " + entry.getKey());

            JsonObject variant = entry.getValue().getAsJsonObject();
            int rotation = variant.has("y") ? variant.get("y").getAsInt() : 0;
            assertEquals(FACINGS.indexOf(state.get("facing")) * 90, rotation, entry.getKey());
            ResolvedModel model = resolveModel(variant.get("model").getAsString(), new HashSet<>());
            String front = model.textures().get("front");
            assertNotNull(front, entry.getKey());
            String previous = frontByClimate.putIfAbsent(state.get("climate"), front);
            if (previous != null) assertEquals(previous, front, "Climate changed appearance with facing or power");
            assertTrue(usesTexture(model.elements(), "#front"), "Front texture is not rendered: " + entry.getKey());

            boolean active = Boolean.parseBoolean(state.get("active"));
            assertEquals(active, usesTexture(model.elements(), "#fan"), entry.getKey());
            if (active) {
                assertEquals("biome_based_crops:block/air_conditioner_fan", model.textures().get("fan"));
                readTexture(model.textures().get("fan"));
            }
        }

        var colors = new HashSet<Integer>();
        BufferedImage casing = null;
        for (String climate : CLIMATES) {
            BufferedImage image = readTexture(frontByClimate.get(climate));
            assertEquals(16, image.getWidth(), climate);
            assertEquals(16, image.getHeight(), climate);
            int indicatorColor = image.getRGB(10, 11);
            assertEquals(255, indicatorColor >>> 24, climate);
            assertTrue(colors.add(indicatorColor), "Two climates share an indicator color: " + climate);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (indicatorPixel(x, y)) {
                        assertEquals(indicatorColor, image.getRGB(x, y), climate + " indicator");
                    } else if (casing != null) {
                        assertEquals(casing.getRGB(x, y), image.getRGB(x, y), climate + " casing at " + x + "," + y);
                    }
                }
            }
            casing = image;
        }
    }

    private static boolean indicatorPixel(int x, int y) {
        return y == 11 && (x == 10 || x == 12) || y == 12 && (x == 11 || x == 12);
    }

    private static Map<String, String> properties(String selector) {
        var result = new HashMap<String, String>();
        for (String property : selector.split(",")) {
            String[] pair = property.split("=");
            assertEquals(2, pair.length, selector);
            assertTrue(result.put(pair[0], pair[1]) == null, selector);
        }
        return Map.copyOf(result);
    }

    private static ResolvedModel resolveModel(String id, Set<String> ancestry) throws IOException {
        assertTrue(ancestry.add(id), "Cyclic model inheritance: " + id);
        assertTrue(id.startsWith("biome_based_crops:"), id);
        JsonObject model = json(ASSETS.resolve("models/" + id.substring(id.indexOf(':') + 1) + ".json"));
        var textures = new HashMap<String, String>();
        JsonArray elements = null;
        if (model.has("parent") && model.get("parent").getAsString().startsWith("biome_based_crops:")) {
            ResolvedModel parent = resolveModel(model.get("parent").getAsString(), ancestry);
            textures.putAll(parent.textures());
            elements = parent.elements();
        }
        if (model.has("textures")) {
            for (var entry : model.getAsJsonObject("textures").entrySet())
                textures.put(entry.getKey(), entry.getValue().getAsString());
        }
        if (model.has("elements")) elements = model.getAsJsonArray("elements");
        assertNotNull(elements, "Missing geometry: " + id);
        return new ResolvedModel(textures, elements);
    }

    private static boolean usesTexture(JsonArray elements, String texture) {
        for (JsonElement element : elements) {
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                if (texture.equals(face.getValue().getAsJsonObject().get("texture").getAsString())) return true;
            }
        }
        return false;
    }

    private static BufferedImage readTexture(String id) throws IOException {
        assertNotNull(id);
        assertTrue(id.startsWith("biome_based_crops:"), id);
        Path path = ASSETS.resolve("textures/" + id.substring(id.indexOf(':') + 1) + ".png");
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, path.toString());
        return image;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private record ResolvedModel(Map<String, String> textures, JsonArray elements) { }
}
