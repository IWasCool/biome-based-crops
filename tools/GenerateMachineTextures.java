import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Hand-authored integer-grid artwork. Run from the project root with Java 21. */
class GenerateMachineTextures {
    static final Path ROOT = Path.of("src/main/resources/assets/biome_based_crops/textures");
    // Muted green-gray metal and charcoal sampled from Power Grid's electric machines.
    static final int BODY = 0x8F9691, EDGE = 0x505351, LIGHT = 0xA8B3AB,
            SHADE = 0x707570, DARK = 0x353438,
            BLADE = 0x767C78, HUB = BODY;
    static final int ICY = 0x93E8E8, COLD = 0x5D86DE, NEUTRAL = 0xC4CBC6,
            WARM = 0xE9D063, HOT = 0xE9934D;

    static void rect(BufferedImage image, int x, int y, int w, int h, int color) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++)
            image.setRGB(i, j, 0xFF000000 | color);
    }

    static BufferedImage casing() {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        rect(image, 0, 0, 16, 16, EDGE);
        rect(image, 1, 1, 14, 14, BODY);
        rect(image, 1, 1, 14, 1, LIGHT);
        rect(image, 1, 14, 14, 1, SHADE);
        return image;
    }

    static BufferedImage front(int indicator) {
        var image = casing();
        rect(image, 2, 3, 12, 6, DARK);
        rect(image, 2, 4, 12, 1, SHADE);
        rect(image, 2, 6, 12, 1, SHADE);
        rect(image, 2, 8, 12, 1, SHADE);
        rect(image, 2, 11, 3, 2, EDGE);
        rect(image, 9, 11, 5, 2, DARK);
        rect(image, 10, 11, 1, 1, indicator);
        rect(image, 12, 11, 1, 1, indicator);
        rect(image, 11, 12, 2, 1, indicator);
        return image;
    }

    static BufferedImage fanOpening() {
        var image = casing();
        // A 12-pixel opening leaves a consistent two-pixel casing on every side.
        rect(image, 2, 2, 12, 12, EDGE);
        rect(image, 3, 3, 10, 10, DARK);
        return image;
    }

    static BufferedImage fan(int frame) {
        var image = fanOpening();
        int[][] shapes = frame % 2 == 0
                ? new int[][] {{7, 3, 2, 4}, {9, 7, 4, 2}, {7, 9, 2, 4}, {3, 7, 4, 2}}
                : new int[][] {{4, 4, 3, 2}, {5, 6, 2, 1}, {10, 4, 2, 3}, {9, 5, 1, 2},
                        {9, 10, 3, 2}, {9, 9, 2, 1}, {4, 9, 2, 3}, {6, 9, 1, 2}};
        for (int[] r : shapes) rect(image, r[0], r[1], r[2], r[3], BLADE);
        rect(image, 6, 6, 4, 4, EDGE);
        rect(image, 7, 7, 2, 2, HUB);
        return image;
    }

    static BufferedImage rotor(int frame) {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        int[][] shapes = frame % 2 == 0
                ? new int[][] {{3, 0, 2, 3}, {5, 3, 3, 2}, {3, 5, 2, 3}, {0, 3, 3, 2}}
                : new int[][] {{1, 1, 2, 2}, {5, 1, 2, 2}, {5, 5, 2, 2}, {1, 5, 2, 2}};
        for (int[] r : shapes) rect(image, r[0], r[1], r[2], r[3], BLADE);
        rect(image, 2, 2, 4, 4, EDGE);
        rect(image, 3, 3, 2, 2, HUB);
        return image;
    }

    static BufferedImage activeFanPreview(int frame) {
        var image = fanOpening();
        var g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(rotor(frame), 3, 3, 10, 10, null);
        g.dispose();
        return image;
    }

    static BufferedImage energyGauge(boolean filled) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // Preserve the vanilla-style bevel and red/gray striped fill at the same
        // sixteen-pixel source resolution as the rest of the instruments.
        rect(image, 0, 0, 16, 16, 0x8B8B8B);
        rect(image, 0, 0, 15, 1, 0x373737);
        rect(image, 0, 1, 1, 14, 0x373737);
        rect(image, 15, 1, 1, 15, 0xFFFFFF);
        rect(image, 1, 15, 14, 1, 0xFFFFFF);
        for (int y = 1; y < 15; y++) {
            int color = filled ? (y % 2 == 1 ? 0xFB3B3B : 0xF82727)
                    : (y % 2 == 1 ? 0xC6C6C6 : 0x8B8B8B);
            rect(image, 1, y, 14, 1, color);
        }
        return image;
    }

    static void save(String name, BufferedImage image) throws IOException {
        var path = ROOT.resolve(name + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }

    static void saveFronts() throws IOException {
        save("block/air_conditioner_front_icy", front(ICY));
        save("block/air_conditioner_front", front(COLD));
        save("block/air_conditioner_front_neutral", front(NEUTRAL));
        save("block/air_conditioner_front_warm", front(WARM));
        save("block/air_conditioner_front_hot", front(HOT));
    }

    public static void main(String[] args) throws IOException {
        saveFronts();
        // Recolor the indicator without regenerating hand-edited casing/fan assets.
        if (args.length == 1 && args[0].equals("--fronts-only")) return;
        var side = casing();
        rect(side, 3, 3, 10, 10, SHADE);
        rect(side, 4, 4, 8, 8, BODY);
        for (int y = 5; y <= 9; y += 2) rect(side, 9, y, 2, 1, EDGE);
        save("block/air_conditioner_side", side);
        var top = casing();
        rect(top, 3, 11, 10, 1, SHADE);
        save("block/air_conditioner_top", top);
        var bottom = casing();
        rect(bottom, 3, 3, 2, 2, EDGE);
        rect(bottom, 11, 3, 2, 2, EDGE);
        rect(bottom, 3, 11, 2, 2, EDGE);
        rect(bottom, 11, 11, 2, 2, EDGE);
        save("block/air_conditioner_bottom", bottom);
        save("block/air_conditioner_back", fan(0));
        // Only the rotor animates. Two 8-pixel frames fit in a 16x8 file, while
        // the casing retains the same 16-pixel texture in both running states.
        save("block/air_conditioner_back_on", fanOpening());
        var animation = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < 2; frame++) {
            var face = rotor(frame);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                animation.setRGB(x + frame * 8, y, face.getRGB(x, y));
        }
        save("block/air_conditioner_fan", animation);
        Files.writeString(ROOT.resolve("block/air_conditioner_fan.png.mcmeta"),
                "{\n  \"animation\": {\n    \"width\": 8,\n    \"height\": 8,\n    \"frametime\": 3,\n    \"frames\": [0, 1],\n    \"interpolate\": false\n  }\n}\n");
        Files.deleteIfExists(ROOT.resolve("block/air_conditioner_back_on.png.mcmeta"));

        save("gui/energy_empty", energyGauge(false));
        save("gui/energy_filled", energyGauge(true));
        Files.deleteIfExists(ROOT.resolve("gui/temperature_needle.png"));

        // File-only preview: nearest-neighbor scaling retains the actual pixel grid.
        var preview = new BufferedImage(1120, 470, BufferedImage.TYPE_INT_ARGB);
        var g = preview.createGraphics();
        g.setColor(new java.awt.Color(0x252A30));
        g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] names = {"front", "front_warm", "side", "top", "bottom", "back"};
        g.setColor(java.awt.Color.WHITE);
        for (int i = 0; i < names.length; i++) {
            var face = ImageIO.read(ROOT.resolve("block/air_conditioner_" + names[i] + ".png").toFile());
            g.drawImage(face, 24 + i * 182, 36, 160, 160, null);
            g.drawString(names[i], 24 + i * 182, 222);
        }
        g.setColor(java.awt.Color.WHITE);
        g.drawString("16px faces / shared casing palette / 12px fan", 24, 300);
        g.drawImage(activeFanPreview(0), 720, 272, 160, 160, null);
        g.drawImage(activeFanPreview(1), 906, 272, 160, 160, null);
        g.dispose();
        Files.createDirectories(Path.of("build"));
        ImageIO.write(preview, "PNG", Path.of("build/machine-textures-preview.png").toFile());

    }
}
