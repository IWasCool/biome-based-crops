package com.ourcreation.biomebasedcrops.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
        g.blitSprite(isHoveredOrFocused() ? HIGHLIGHTED : NORMAL, getX(), getY(), width, height);
        var font = Minecraft.getInstance().font;
        Component label = selected.get().component();
        int textWidth = font.width(label);
        float scale = Math.min(1.0F, (width - 8) / (float) Math.max(1, textWidth));
        int textY = getY() + (int) ((height - font.lineHeight * scale) / 2) + 1;
        g.pose().pushPose();
        g.pose().translate(getX() + width / 2.0F, textY, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, label, -textWidth / 2, 0, 0xE0E0E0, false);
        g.pose().popPose();
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("biome_based_crops.tooltip.climate", selected.get().component()));
        output.add(NarratedElementType.USAGE,
                Component.translatable("biome_based_crops.ac.selector_usage"));
    }
}
