package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class InstrumentArtworkTest {
    private static final Path ASSETS = Path.of(System.getProperty("biomeBasedCrops.projectDir", "."))
            .resolve("src/main/resources/assets/biome_based_crops");

    @Test
    void thermometerBulbAlwaysRendersOpaqueBeforeTheGlass() throws IOException {
        JsonObject body = model("thermometer_geometry");
        assertEquals("minecraft:solid", body.get("render_type").getAsString());
        var elements = body.getAsJsonArray("elements").asList();
        assertEquals(1, elements.stream().filter(e -> name(e).equals("mercury_bulb")).count());
        assertEquals(7, elements.stream().filter(e -> name(e).startsWith("scale_mark_")).count());
        assertFalse(elements.stream().anyMatch(e -> name(e).startsWith("glass_")));

        JsonObject glass = model("thermometer_glass");
        assertEquals("minecraft:translucent", glass.get("render_type").getAsString());
        assertTrue(glass.getAsJsonArray("elements").asList().stream()
                .allMatch(e -> name(e).startsWith("glass_")));

        var overrides = model("thermometer").getAsJsonArray("overrides");
        assertEquals(5, overrides.size());
        int[] levels = { 0, 2, 5, 7, 10 };
        for (int i = 0; i < levels.length; i++) {
            int level = levels[i];
            JsonObject state = model("thermometer_" + level);
            assertEquals("neoforge:composite", state.get("loader").getAsString());
            assertEquals(i == 0 ? List.of("body", "glass") : List.of("body", "mercury", "glass"),
                    state.getAsJsonArray("item_render_order").asList().stream().map(JsonElement::getAsString).toList());
            var children = state.getAsJsonObject("children");
            assertEquals("biome_based_crops:item/thermometer_geometry", children.getAsJsonObject("body").get("parent").getAsString());
            assertEquals("minecraft:solid", children.getAsJsonObject("body").get("render_type").getAsString());
            assertEquals("minecraft:translucent", children.getAsJsonObject("glass").get("render_type").getAsString());
            if (i > 0) {
                var mercury = children.getAsJsonObject("mercury");
                assertEquals("minecraft:solid", mercury.get("render_type").getAsString());
                assertEquals("mercury_column", name(mercury.getAsJsonArray("elements").get(0)));
                assertEquals(level + 3, mercury.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to").get(1).getAsInt());
            }
            assertEquals("biome_based_crops:item/thermometer_" + level,
                    overrides.get(i).getAsJsonObject().get("model").getAsString());
            assertEquals(i / 4.0, overrides.get(i).getAsJsonObject().getAsJsonObject("predicate").get("biome_based_crops:temperature").getAsDouble());
        }
    }

    @Test
    void heldThermometerIsRaisedWithoutMovingSideways() throws IOException {
        var display = model("thermometer_geometry").getAsJsonObject("display");
        for (String hand : List.of("firstperson_righthand", "firstperson_lefthand")) {
            var transform = display.getAsJsonObject(hand);
            assertEquals(List.of(1.0, 5.0, 0.0), transform.getAsJsonArray("translation").asList().stream().map(JsonElement::getAsDouble).toList());
            assertTrue(transform.getAsJsonArray("scale").asList().stream().allMatch(v -> v.getAsDouble() == 0.75));
        }
    }

    @Test
    void instrumentTexturesStayAtSixteenAndObsoleteDialArtworkIsRemoved() throws IOException {
        try (var files = Files.walk(ASSETS.resolve("textures"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".png")).toList()) checkSize(path);
        }
        assertFalse(Files.exists(ASSETS.resolve("textures/gui/temperature_needle.png")),
                "The climate selector no longer uses a dial or separate needle texture");
        for (String name : List.of("temperature_dial", "temperature_dial_icy", "temperature_dial_cold",
                "temperature_dial_neutral", "temperature_dial_warm", "temperature_dial_hot", "temperature_hub"))
            assertFalse(Files.exists(ASSETS.resolve("textures/gui/" + name + ".png")),
                    "The climate selector no longer uses " + name);
        assertFalse(Files.exists(ASSETS.resolve("textures/block/air_conditioner_back_on.png.mcmeta")),
                "The fixed casing must not retain the old oversized animation metadata");
    }

    private static void checkSize(Path path) throws IOException {
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, path.toString());
        if (path.equals(ASSETS.resolve("textures/gui/field_guide_book.png"))) {
            assertEquals(256, image.getWidth(), path.toString());
            assertEquals(256, image.getHeight(), path.toString());
        } else {
            assertTrue(image.getWidth() <= 16 && image.getHeight() <= 16, path.toString());
        }
    }

    private static JsonObject model(String name) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/" + name + ".json"))).getAsJsonObject();
    }

    private static String name(JsonElement element) {
        return element.getAsJsonObject().get("name").getAsString();
    }
}
