package com.ourcreation.biomebasedcrops.client;

import java.util.List;
import java.util.Locale;

import com.mojang.math.Axis;
import com.ourcreation.biomebasedcrops.AirConditionerBlockEntity;
import com.ourcreation.biomebasedcrops.AirConditionerMenu;
import com.ourcreation.biomebasedcrops.GreenhouseContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class AirConditionerScreen extends AbstractContainerScreen<AirConditionerMenu> {
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation DIAL = texture("temperature_dial");
    private static final ResourceLocation NEEDLE = texture("temperature_needle");
    private static final ResourceLocation HUB = texture("temperature_hub");
    private static final ResourceLocation ENERGY_BACKGROUND = texture("energy_empty");
    private static final ResourceLocation ENERGY_FILL = texture("energy_filled");
    private static final int ENERGY_X = 13, ENERGY_Y = 35, ENERGY_WIDTH = 18, ENERGY_HEIGHT = 44;
    private static final int THERMOMETER_X = 150, THERMOMETER_Y = 21;
    private TemperatureDial dial;

    public AirConditionerScreen(AirConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 204;
        inventoryLabelY = 110;
    }

    @Override protected void init() {
        super.init();
        dial = addRenderableWidget(new TemperatureDial(leftPos + 60, topPos + 30, 56));
    }

    @Override public void containerTick() {
        super.containerTick();
        dial.tick();
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // Reuse the vanilla container frame and player inventory, including pack replacements.
        g.blit(CONTAINER, x, y, 0, 0, imageWidth, 17);
        g.blit(CONTAINER, x, y + 17, imageWidth, 91, 0.0F, 8.0F, 176, 8, 256, 256);
        g.blit(CONTAINER, x, y + 108, 0, 126, imageWidth, 96);
        int barX = x + ENERGY_X, barY = y + ENERGY_Y;
        g.blit(ENERGY_BACKGROUND, barX, barY, 0, 0, ENERGY_WIDTH, ENERGY_HEIGHT, ENERGY_WIDTH, ENERGY_HEIGHT);
        int stored = Math.clamp(menu.energyStored(), 0, AirConditionerBlockEntity.CAPACITY);
        int fill = (int) ((long) ENERGY_HEIGHT * stored / AirConditionerBlockEntity.CAPACITY);
        if (fill > 0) {
            g.blit(ENERGY_FILL, barX, barY + ENERGY_HEIGHT - fill, 0, ENERGY_HEIGHT - fill,
                    ENERGY_WIDTH, fill, ENERGY_WIDTH, ENERGY_HEIGHT);
        }
        g.renderItem(new ItemStack(GreenhouseContent.THERMOMETER.get()), x + THERMOMETER_X, y + THERMOMETER_Y);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "Thermoregulator", titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, "Inventory", inventoryLabelX, inventoryLabelY, 0x404040, false);
        g.drawString(font, "Target", 88 - font.width("Target") / 2, 20, 0x404040, false);
        String target = dial.displayedTarget() + "\u00B0C";
        g.drawString(font, target, 88 - font.width(target) / 2, 91, 0x202020, false);
        g.drawString(font, "FE", ENERGY_X + (ENERGY_WIDTH - font.width("FE")) / 2, 84, 0x404040, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (inside(mouseX, mouseY, leftPos + THERMOMETER_X, topPos + THERMOMETER_Y, 16, 16)) {
            g.renderTooltip(font, Component.literal("Current temperature: " + temperature(menu.currentTemperature())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT)) {
            g.renderComponentTooltip(font, List.of(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE", menu.energyStored(), AirConditionerBlockEntity.CAPACITY)),
                    Component.literal(String.format(Locale.ROOT, "%,d FE/t", menu.energyCost())),
                    Component.literal(String.format(Locale.ROOT, "Room: %,d blocks", menu.roomVolume()))), mouseX, mouseY);
        }
    }

    private static String temperature(float value) { return String.format(Locale.ROOT, "%.1f\u00B0C", value); }
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("biome_based_crops", "textures/gui/" + name + ".png");
    }
    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private final class TemperatureDial extends AbstractWidget {
        private int target, pendingTicks;
        private boolean dragging;

        private TemperatureDial(int x, int y, int size) {
            super(x, y, size, size, Component.literal("Target temperature"));
            target = menu.targetTemperature();
        }
        private int displayedTarget() { return target; }
        private void tick() {
            if (pendingTicks > 0) pendingTicks--;
            if (!dragging && (pendingTicks == 0 || menu.targetTemperature() == target)) target = menu.targetTemperature();
        }
        private void choose(int value) {
            value = Math.clamp(value, AirConditionerMenu.MIN_TARGET, AirConditionerMenu.MAX_TARGET);
            if (value == target) return;
            target = value;
            pendingTicks = 10;
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, value - AirConditionerMenu.MIN_TARGET);
        }
        private void chooseAt(double mouseX, double mouseY) {
            double dx = mouseX - (getX() + width / 2.0), dy = mouseY - (getY() + height / 2.0);
            if (dx * dx + dy * dy < 16) return;
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 135) angle += 360;
            if (angle > 405) angle = angle - 405 < 495 - angle ? 405 : 135;
            choose((int) Math.round(AirConditionerMenu.MIN_TARGET + (angle - 135) / 270.0 * (AirConditionerMenu.MAX_TARGET - AirConditionerMenu.MIN_TARGET)));
        }
        @Override protected boolean clicked(double mouseX, double mouseY) {
            double dx = mouseX - (getX() + width / 2.0), dy = mouseY - (getY() + height / 2.0);
            return active && visible && dx * dx + dy * dy <= width * width / 4.0;
        }
        @Override public void onClick(double mouseX, double mouseY) { dragging = true; chooseAt(mouseX, mouseY); }
        @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) { chooseAt(mouseX, mouseY); }
        @Override public void onRelease(double mouseX, double mouseY) { dragging = false; }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!isFocused()) return false;
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN) choose(target - 1);
            else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP) choose(target + 1);
            else if (key == GLFW.GLFW_KEY_PAGE_DOWN) choose(target - 5);
            else if (key == GLFW.GLFW_KEY_PAGE_UP) choose(target + 5);
            else if (key == GLFW.GLFW_KEY_HOME) choose(AirConditionerMenu.MIN_TARGET);
            else if (key == GLFW.GLFW_KEY_END) choose(AirConditionerMenu.MAX_TARGET);
            else return super.keyPressed(key, scanCode, modifiers);
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, Component.literal("Target " + target + " degrees Celsius"));
            output.add(NarratedElementType.USAGE, Component.literal("Drag the dial or use arrow keys to adjust the target."));
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, cy = getY() + height / 2;
            g.blit(DIAL, getX(), getY(), 0, 0, 56, 56, 56, 56);
            float angle = 225.0F + (target - AirConditionerMenu.MIN_TARGET)
                    / (float) (AirConditionerMenu.MAX_TARGET - AirConditionerMenu.MIN_TARGET) * 270.0F;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            g.blit(NEEDLE, -3, -21, 0, 0, 6, 24, 6, 24);
            g.pose().popPose();
            g.blit(HUB, cx - 4, cy - 4, 0, 0, 8, 8, 8, 8);
        }
    }
}
