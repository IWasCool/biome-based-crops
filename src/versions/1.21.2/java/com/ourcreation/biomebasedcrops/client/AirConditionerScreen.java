package com.ourcreation.biomebasedcrops.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ourcreation.biomebasedcrops.AirConditionerBlockEntity;
import com.ourcreation.biomebasedcrops.AirConditionerMenu;
import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class AirConditionerScreen extends AbstractContainerScreen<AirConditionerMenu> {
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation ENERGY_BACKGROUND = texture("energy_empty");
    private static final ResourceLocation ENERGY_FILL = texture("energy_filled");
    private static final int ENERGY_X = 13, ENERGY_Y = 28, ENERGY_WIDTH = 18, ENERGY_HEIGHT = 60;
    private static final int ENERGY_TEXTURE_SIZE = 16;
    private static final int STATUS_X = 157, STATUS_Y = 3;
    private static final int CLIMATE_X = 42, CLIMATE_Y = 49;
    private static final int CLIMATE_ARROW_WIDTH = 18, CLIMATE_WIDTH = 80, CLIMATE_GAP = 4;
    private static final int CLIMATE_CENTER_X = CLIMATE_X + CLIMATE_ARROW_WIDTH + CLIMATE_GAP + CLIMATE_WIDTH / 2;
    private static final int POWER_Y = 76, POWER_WIDTH = CLIMATE_ARROW_WIDTH * 2 + CLIMATE_WIDTH + CLIMATE_GAP * 2;
    private CropClimate selectedClimate;
    private int pendingTicks;
    private Button powerButton;
    private boolean displayedEnabled, requestedEnabled;
    private int powerPendingTicks;

    public AirConditionerScreen(AirConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 204;
        inventoryLabelY = 110;
    }

    @Override protected void init() {
        super.init();
        selectedClimate = menu.targetClimate();
        displayedEnabled = menu.enabled();
        powerPendingTicks = 0;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> cycleClimate(-1))
                .bounds(leftPos + CLIMATE_X, topPos + CLIMATE_Y, CLIMATE_ARROW_WIDTH, 20)
                .createNarration(message -> Component.translatable("biome_based_crops.ac.previous_climate")).build());
        addRenderableWidget(new ClimateModeButton(leftPos + CLIMATE_X + CLIMATE_ARROW_WIDTH + CLIMATE_GAP,
                topPos + CLIMATE_Y, CLIMATE_WIDTH,
                () -> selectedClimate, this::selectClimate));
        addRenderableWidget(Button.builder(Component.literal(">"), button -> cycleClimate(1))
                .bounds(leftPos + CLIMATE_X + CLIMATE_ARROW_WIDTH + CLIMATE_WIDTH + CLIMATE_GAP * 2,
                        topPos + CLIMATE_Y, CLIMATE_ARROW_WIDTH, 20)
                .createNarration(message -> Component.translatable("biome_based_crops.ac.next_climate")).build());
        powerButton = addRenderableWidget(Button.builder(powerLabel(), button -> togglePower())
                .bounds(leftPos + CLIMATE_X, topPos + POWER_Y, POWER_WIDTH, 20)
                .createNarration(message -> powerDescription().copy()).build());
        powerButton.setTooltip(Tooltip.create(powerDescription()));
        addRenderableWidget(new RoomStatusIndicator(leftPos + STATUS_X, topPos + STATUS_Y));
    }

    private Component powerLabel() {
        return Component.translatable(displayedEnabled ? "options.on" : "options.off")
                .withStyle(displayedEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private Component powerDescription() {
        return title.copy().append(": ").append(powerLabel());
    }

    private void togglePower() {
        if (minecraft.gameMode == null || powerPendingTicks > 0) return;
        requestedEnabled = !menu.enabled();
        powerPendingTicks = 20;
        powerButton.active = false;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AirConditionerMenu.TOGGLE_POWER);
    }

    private void cycleClimate(int direction) {
        CropClimate[] climates = CropClimate.values();
        selectClimate(climates[Math.floorMod(selectedClimate.ordinal() + direction, climates.length)]);
    }

    private void selectClimate(CropClimate climate) {
        if (climate == selectedClimate) return;
        selectedClimate = climate;
        pendingTicks = 10;
        if (minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selectedClimate.ordinal());
    }

    @Override public void containerTick() {
        super.containerTick();
        if (pendingTicks > 0) pendingTicks--;
        if (pendingTicks == 0 || menu.targetClimate() == selectedClimate) {
            selectedClimate = menu.targetClimate();
        }
        if (powerPendingTicks > 0) {
            powerPendingTicks--;
            if (menu.enabled() == requestedEnabled) powerPendingTicks = 0;
        }
        powerButton.active = powerPendingTicks == 0;
        if (displayedEnabled != menu.enabled()) {
            displayedEnabled = menu.enabled();
            powerButton.setMessage(powerLabel());
            powerButton.setTooltip(Tooltip.create(powerDescription()));
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // Reuse the vanilla container frame and player inventory, including pack replacements.
        g.blit(RenderType::guiTextured, CONTAINER, x, y, 0, 0, imageWidth, 17, 256, 256);
        g.blit(RenderType::guiTextured, CONTAINER, x, y + 17, 0.0F, 8.0F, imageWidth, 91, 176, 8, 256, 256);
        g.blit(RenderType::guiTextured, CONTAINER, x, y + 108, 0, 126, imageWidth, 96, 256, 256);
        int barX = x + ENERGY_X, barY = y + ENERGY_Y;
        g.blit(RenderType::guiTextured, ENERGY_BACKGROUND, barX, barY, 0.0F, 0.0F, ENERGY_WIDTH, ENERGY_HEIGHT,
                ENERGY_TEXTURE_SIZE, ENERGY_TEXTURE_SIZE, ENERGY_TEXTURE_SIZE, ENERGY_TEXTURE_SIZE);
        int stored = Math.clamp(menu.energyStored(), 0, AirConditionerBlockEntity.CAPACITY);
        int fill = (int) ((long) ENERGY_HEIGHT * stored / AirConditionerBlockEntity.CAPACITY);
        if (fill > 0) {
            // Clip the stretched texture so both gauges retain the same frame and fill alignment.
            g.enableScissor(barX, barY + ENERGY_HEIGHT - fill, barX + ENERGY_WIDTH, barY + ENERGY_HEIGHT);
            g.blit(RenderType::guiTextured, ENERGY_FILL, barX, barY, 0.0F, 0.0F, ENERGY_WIDTH, ENERGY_HEIGHT,
                    ENERGY_TEXTURE_SIZE, ENERGY_TEXTURE_SIZE, ENERGY_TEXTURE_SIZE, ENERGY_TEXTURE_SIZE);
            g.disableScissor();
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        float titleScale = Math.min(1.0F, (STATUS_X - titleLabelX - 3) / (float) Math.max(1, font.width(title)));
        g.pose().pushPose();
        g.pose().translate(titleLabelX, titleLabelY, 0);
        g.pose().scale(titleScale, titleScale, 1);
        g.drawString(font, title, 0, 0, 0x404040, false);
        g.pose().popPose();
        g.drawString(font, Component.translatable("container.inventory"), inventoryLabelX, inventoryLabelY, 0x404040, false);
        String modeLabel = Component.translatable("biome_based_crops.ac.climate_label").getString();
        g.drawString(font, modeLabel, CLIMATE_CENTER_X - font.width(modeLabel) / 2, CLIMATE_Y - 18, 0x404040, false);
        g.drawString(font, "FE", ENERGY_X + (ENERGY_WIDTH - font.width("FE")) / 2, 94, 0x404040, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (inside(mouseX, mouseY, leftPos + STATUS_X, topPos + STATUS_Y, 16, 16)) {
            int tooltipWidth = Math.min(240, Math.max(1, width - 24));
            var lines = roomTooltip().stream().flatMap(line -> font.split(line, tooltipWidth).stream()).toList();
            g.renderTooltip(font, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT)) {
            g.renderComponentTooltip(font, List.of(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE", menu.energyStored(), AirConditionerBlockEntity.CAPACITY)),
                    Component.literal(String.format(Locale.ROOT, "%,d FE/t", menu.energyCost())),
                    Component.translatable("biome_based_crops.ac.energy_room", number(menu.roomVolume()))), mouseX, mouseY);
        }
    }

    private Component roomSummary() {
        if (menu.roomStatus() == AirConditionerMenu.RoomStatus.SCANNING) return menu.roomStatus().message();
        if (!hasDetectedRoom()) return Component.translatable("biome_based_crops.ac.no_greenhouse");
        if (menu.roomStatus() == AirConditionerMenu.RoomStatus.LEAKING)
            return Component.translatable("biome_based_crops.ac.greenhouse_leaking");
        return Component.translatable(menu.roomStatus() == AirConditionerMenu.RoomStatus.VALID
                ? "biome_based_crops.ac.greenhouse_registered" : "biome_based_crops.ac.greenhouse_unregistered");
    }

    private List<Component> roomTooltip() {
        var lines = new ArrayList<Component>();
        if (hasDetectedRoom() && menu.roomStatus() != AirConditionerMenu.RoomStatus.LEAKING)
            lines.add(Component.translatable("biome_based_crops.ac.enclosed_room").withStyle(ChatFormatting.GRAY));
        lines.add(roomSummary().copy().withStyle(menu.roomStatus() == AirConditionerMenu.RoomStatus.VALID
                ? ChatFormatting.GREEN : menu.roomStatus() == AirConditionerMenu.RoomStatus.SCANNING
                ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        if (menu.roomStatus() != AirConditionerMenu.RoomStatus.VALID
                && menu.roomStatus() != AirConditionerMenu.RoomStatus.SCANNING
                && menu.roomStatus() != AirConditionerMenu.RoomStatus.WRONG_CLIMATE) lines.add(menu.roomStatus().message());
        if (hasDetectedRoom()) {
            lines.add(Component.translatable("biome_based_crops.ac.room_climate", menu.roomClimate().component()));
            lines.add(Component.translatable("biome_based_crops.ac.interior_capacity",
                    number(menu.roomInteriorBlocks()), number(menu.roomInteriorCapacity())));
            lines.add(Component.translatable("biome_based_crops.ac.unit_capacity",
                    number(menu.installedUnits()), number(menu.requiredUnits())));
            lines.add(Component.translatable("biome_based_crops.ac.sky_requirement").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private boolean hasDetectedRoom() {
        return menu.roomEnclosed() && menu.roomStatus() != AirConditionerMenu.RoomStatus.OPEN
                && menu.roomStatus() != AirConditionerMenu.RoomStatus.OUTSIDE;
    }

    private static String number(int value) { return String.format(Locale.ROOT, "%,d", value); }

    private final class RoomStatusIndicator extends AbstractWidget {
        private RoomStatusIndicator(int x, int y) {
            super(x, y, 16, 16, Component.translatable("biome_based_crops.ac.front_room"));
        }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int color = 0xFF404040;
            int x = getX() + 1, y = getY() + 1;
            // Center the actual pixels instead of a font glyph's advance width.
            g.fill(x + 6, y + 2, x + 8, y + 8, color);
            g.fill(x + 6, y + 10, x + 8, y + 12, color);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.HINT, roomSummary());
            output.add(NarratedElementType.USAGE, menu.roomStatus().message());
        }
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("biome_based_crops", "textures/gui/" + name + ".png");
    }
    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

}
