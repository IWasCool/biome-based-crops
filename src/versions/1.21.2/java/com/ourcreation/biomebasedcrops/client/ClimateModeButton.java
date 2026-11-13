package com.ourcreation.biomebasedcrops.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** A climate readout using Minecraft's recessed text field artwork. */
public final class ClimateModeButton extends AbstractButton {
    private static final ResourceLocation NORMAL = ResourceLocation.withDefaultNamespace("widget/text_field");
    private static final ResourceLocation HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/text_field_highlighted");
    private static final int ICON_SIZE = 9, ICON_GAP = 4;
    private static final String[] ICY_ICON = {
            "....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########",
            "...###...", "..#.#.#..", ".#..#..#.", "....#...."
    };
    private static final String[] COLD_ICON = {
            "...###...", "...#+#...", "...#+#...", "...#+#...", "...#+#...",
            "..##+##..", "..#+++#..", "..#+++#..", "...###..."
    };
    private static final String[] NEUTRAL_ICON = {
            ".....###.", "...#####.", "..##++##.", ".##++##..", ".#++##...",
            ".#+##....", "..##.....", ".#.......", "#........"
    };
    private static final String[] WARM_ICON = {
            "....+....", ".+.....+.", "..#####..", "..#+++#..", "+.#+++#.+",
            "..#+++#..", "..#####..", ".+.....+.", "....+...."
    };
    private static final String[] HOT_ICON = {
            ".....#...", "....##...", "...###...", ".#.####..", ".######..",
            "###++###.", "##++++##.", ".##++##..", "..####..."
    };
    private final Supplier<CropClimate> selected;
    private final Consumer<CropClimate> select;

    public ClimateModeButton(int x, int y, int width,
                             Supplier<CropClimate> selected, Consumer<CropClimate> select) {
        super(x, y, width, 20, Component.translatable("biome_based_crops.ac.climate_label"));
        this.selected = selected;
        this.select = select;
    }

    @Override public void onPress() {
        cycle(Screen.hasShiftDown() ? -1 : 1);
    }

    private void cycle(int direction) {
        CropClimate[] climates = CropClimate.values();
        select.accept(climates[Math.floorMod(selected.get().ordinal() + direction, climates.length)]);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (active && visible && isFocused()) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN) {
                cycle(-1);
                playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP) {
                cycle(1);
                playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blitSprite(RenderType::guiTextured, isHoveredOrFocused() ? HIGHLIGHTED : NORMAL, getX(), getY(), width, height);
        var font = Minecraft.getInstance().font;
        CropClimate climate = selected.get();
        Component label = climate.component();
        int textWidth = font.width(label);
        float scale = Math.min(1.0F, (width - 8 - ICON_SIZE - ICON_GAP) / (float) Math.max(1, textWidth));
        int contentX = getX() + (int) ((width - ICON_SIZE - ICON_GAP - textWidth * scale) / 2);
        renderClimateIcon(g, climate, contentX, getY() + (height - ICON_SIZE) / 2);
        int textY = getY() + (int) ((height - font.lineHeight * scale) / 2) + 1;
        g.pose().pushPose();
        g.pose().translate(contentX + ICON_SIZE + ICON_GAP, textY, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, label, 0, 0, 0xE0E0E0, false);
        g.pose().popPose();
    }

    private static void renderClimateIcon(GuiGraphics g, CropClimate climate, int x, int y) {
        String[] pixels = switch (climate) {
            case ICY -> ICY_ICON;
            case COLD -> COLD_ICON;
            case NEUTRAL -> NEUTRAL_ICON;
            case WARM -> WARM_ICON;
            case HOT -> HOT_ICON;
        };
        int color = switch (climate) {
            case ICY -> 0xFF7FE7F2;
            case COLD -> 0xFFB7D9F5;
            case NEUTRAL -> 0xFF67AB54;
            case WARM -> 0xFFE9AD42;
            case HOT -> 0xFFF17735;
        };
        int accent = switch (climate) {
            case ICY -> 0xFFE1FAFF;
            case COLD -> 0xFF658FFF;
            case NEUTRAL -> 0xFFB6DB85;
            case WARM -> 0xFFFFE184;
            case HOT -> 0xFFFFD45C;
        };
        for (int row = 0; row < ICON_SIZE; row++) {
            for (int column = 0; column < ICON_SIZE; column++) {
                char pixel = pixels[row].charAt(column);
                if (pixel != '.') g.fill(x + column, y + row, x + column + 1, y + row + 1,
                        pixel == '+' ? accent : color);
            }
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("biome_based_crops.tooltip.climate", selected.get().component()));
        output.add(NarratedElementType.USAGE,
                Component.translatable("biome_based_crops.ac.selector_usage"));
    }
}
