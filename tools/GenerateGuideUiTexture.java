import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Recolors only the vanilla open book's leather frame, preserving its entire layout. */
class GenerateGuideUiTexture {
    // Exact native leather colors, including the frame's antialiased highlight shades.
    // Paper, paper-edge browns, and red binding stitches use separate untouched colors.
    private static final Set<Integer> LEATHER = Set.of(
            0x1C0F00, 0x4C1A0B, 0x4F1C0C, 0x501C0D, 0x531E0E, 0x541E0E, 0x541E0F,
            0x541F0F, 0x551F0F, 0x561F0F, 0x56200F, 0x562010, 0x572010, 0x572110,
            0x582110, 0x582111, 0x592111, 0x592211, 0x5A2211, 0x5A2212,
            0x5A2312, 0x5B2312, 0x5C2312, 0x5D2413, 0x5E2413, 0x5F2513, 0x602613,
            0x602614, 0x602714, 0x612614, 0x612714, 0x622614, 0x622715, 0x632715,
            0x642715, 0x642816, 0x652816, 0x652916, 0x662816, 0x662916, 0x662917,
            0x672917, 0x692B18, 0x6A2C19, 0x6B2C19, 0x6D2D1A, 0x6E2E1B, 0x6F2E1B,
            0x702F1C, 0x712F1C, 0x72321F, 0x73301D, 0x73311D, 0x74321E, 0x75321E,
            0x76321E, 0x783520, 0x783623, 0x793520, 0x7B3722, 0x7B3925, 0x7C3722,
            0x7D3823, 0x7D3925, 0x7E3823, 0x7F3924, 0x7F3B26, 0x7F3C28, 0x803924,
            0x803B26, 0x813D28, 0x813E2A, 0x823D28, 0x833E2A, 0x843D28, 0x843F2A,
            0x84402B, 0x84402C, 0x853E29, 0x853F2A, 0x85402B, 0x86402B, 0x86412C,
            0x87412C, 0x87422D, 0x88412C, 0x88422D, 0x89442F, 0x8A442F, 0x8A4530,
            0x8B442F, 0x8B4530, 0x8B4531, 0x8B4631, 0x8C4632, 0x8D4631, 0x8D4732,
            0x8E4732, 0x8E4833, 0x8F4833, 0x8F4934);

    public static void main(String[] args) throws Exception {
        BufferedImage vanilla;
        try (var jar = new ZipFile(args[0]);
             var input = jar.getInputStream(jar.getEntry("assets/minecraft/textures/gui/book.png"))) {
            vanilla = ImageIO.read(input);
        }
        if (vanilla.getWidth() != 256 || vanilla.getHeight() != 256)
            throw new IllegalArgumentException("Expected the native 256x256 book GUI texture");
        var recolored = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int changed = 0;
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int original = vanilla.getRGB(x, y), result = original;
            if ((original >>> 24) != 0 && LEATHER.contains(original & 0xFFFFFF)) {
                // Native main leather #75321E becomes #5E3C20: a slightly darker,
                // less red leather brown beside the item cover's #6A401E.
                int r = Math.round(((original >>> 16) & 255) * 0.80F);
                int g = Math.round(((original >>> 8) & 255) * 1.20F);
                int b = Math.round((original & 255) * (32.0F / 30));
                result = (original & 0xFF000000) | (r << 16) | (g << 8) | b;
                changed++;
            }
            recolored.setRGB(x, y, result);
            if ((result >>> 24) != (original >>> 24))
                throw new IllegalStateException("Book GUI alpha changed at " + x + "," + y);
            if (!LEATHER.contains(original & 0xFFFFFF) && result != original)
                throw new IllegalStateException("Non-leather pixel changed at " + x + "," + y);
        }
        if (changed != 4440) throw new IllegalStateException("Unexpected native leather footprint: " + changed);
        var target = Path.of("src/main/resources/assets/biome_based_crops/textures/gui/field_guide_book.png");
        Files.createDirectories(target.getParent());
        ImageIO.write(recolored, "png", target.toFile());

        var preview = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 512; x++)
            preview.setRGB(x, y, (x < 256 ? vanilla : recolored).getRGB(x % 256, y));
        Path previewPath = Path.of("build/book-reference/guide-ui-leather-comparison.png");
        Files.createDirectories(previewPath.getParent());
        ImageIO.write(preview, "png", previewPath.toFile());
        System.out.println("Recolored " + changed + " leather pixels; all other GUI pixels and alpha unchanged.");
    }
}
