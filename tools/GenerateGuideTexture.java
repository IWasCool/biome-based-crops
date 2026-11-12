import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Reference-style leather artwork inside the exact vanilla 16-pixel book layout. */
class GenerateGuideTexture {
    public static void main(String[] args) throws Exception {
        BufferedImage vanilla;
        try (var jar = new ZipFile(args[0]);
             var input = jar.getInputStream(jar.getEntry("assets/minecraft/textures/item/book.png"))) {
            vanilla = ImageIO.read(input);
        }
        if (vanilla.getWidth() != 16 || vanilla.getHeight() != 16)
            throw new IllegalArgumentException("Expected the vanilla 16x16 book sprite");
        // Preserve every native pixel's position and alpha, including padding.
        // Only recolor the leather and pages, then stamp the cover and binding.
        var guide = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int source = vanilla.getRGB(x, y);
                int color = switch (source & 0xFFFFFF) {
                    case 0x312104 -> 0x211609;
                    case 0x522E10 -> 0x4E2D16;
                    case 0x654B17 -> 0x6A401E;
                    case 0x543E13 -> 0x5B3318;
                    case 0x44250A -> 0x3F2312;
                    case 0x5B5B5B -> 0x6D6153;
                    case 0xB7B7B7 -> 0xBFB7AC;
                    case 0x999999 -> 0x9F968C;
                    case 0x161005 -> 0x19130B;
                    case 0xD6D6D6 -> 0xE0D9CC;
                    default -> source & 0xFFFFFF;
                };
                guide.setRGB(x, y, (source & 0xFF000000) | color);
            }
        }
        // A recessed angular cover mark, rather than a separate raised emblem.
        int[][] mark = {{8, 3}, {9, 4}, {10, 5}, {8, 5}, {7, 6}, {8, 6}};
        for (int[] pixel : mark) guide.setRGB(pixel[0], pixel[1], 0xFF432612);
        // Keep the red ribbon and clasp entirely within the existing binding.
        guide.setRGB(2, 8, 0xFFDEBF3D);
        guide.setRGB(2, 9, 0xFFB42A26);
        guide.setRGB(2, 10, 0xFF941D1D);
        guide.setRGB(2, 11, 0xFF741917);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            if ((vanilla.getRGB(x, y) >>> 24) != (guide.getRGB(x, y) >>> 24))
                throw new IllegalStateException("Book outline or padding changed at " + x + "," + y);
        }
        var target = Path.of("src/main/resources/assets/biome_based_crops/textures/item/farmer_field_guide.png");
        Files.createDirectories(target.getParent());
        ImageIO.write(guide, "png", target.toFile());

        var preview = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 512; x++)
            preview.setRGB(x, y, (x < 256 ? vanilla : guide).getRGB((x % 256) / 16, y / 16));
        ImageIO.write(preview, "png", Path.of("build/book-reference/guide-texture-comparison.png").toFile());
    }
}
