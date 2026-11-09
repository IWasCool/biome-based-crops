package com.ourcreation.biomebasedcrops.client;

import com.ourcreation.biomebasedcrops.AirConditionerBlockEntity;
import com.ourcreation.biomebasedcrops.AirConditionerMenu;
import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class AirConditionerScreen extends AbstractContainerScreen<AirConditionerMenu> {
    private static final ResourceLocation SLOTS = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button colder, warmer;

    public AirConditionerScreen(AirConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 204;
        inventoryLabelY = 110;
    }

    @Override protected void init() {
        super.init();
        colder = addRenderableWidget(Button.builder(Component.literal("-"), button -> setTarget(-1))
                .bounds(leftPos + 12, topPos + 38, 20, 20).build());
        warmer = addRenderableWidget(Button.builder(Component.literal("+"), button -> setTarget(1))
                .bounds(leftPos + imageWidth - 32, topPos + 38, 20, 20).build());
    }

    private void setTarget(int change) {
        int target = menu.targetClimate().ordinal() + change;
        if (minecraft.gameMode != null && target >= 0 && target < CropClimate.values().length)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, target);
    }

    @Override public void containerTick() {
        super.containerTick();
        colder.active = menu.targetClimate().ordinal() > 0;
        warmer.active = menu.targetClimate().ordinal() < CropClimate.values().length - 1;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF101010);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
        graphics.fill(x + 1, y + 1, x + imageWidth - 2, y + imageHeight - 2, 0xFFFFFFFF);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            graphics.blit(SLOTS, x + 7 + column * 18, y + 120 + row * 18, 7, 17, 18, 18);
        for (int column = 0; column < 9; column++)
            graphics.blit(SLOTS, x + 7 + column * 18, y + 178, 7, 17, 18, 18);
        graphics.fill(x + 8, y + 101, x + imageWidth - 8, y + 106, 0xFF373737);
        int charge = Math.clamp(menu.energyStored(), 0, AirConditionerBlockEntity.CAPACITY);
        graphics.fill(x + 9, y + 102, x + 9 + (imageWidth - 18) * charge / AirConditionerBlockEntity.CAPACITY, y + 105, 0xFF4DABB8);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, "Inventory", inventoryLabelX, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, "Local: " + menu.localClimate().label(), 8, 22, 0x404040, false);
        String target = "Target: " + menu.targetClimate().label();
        graphics.drawString(font, target, (imageWidth - font.width(target)) / 2, 44, 0x202020, false);
        String status = menu.energyCost() == 0 ? "Matches local climate"
                : menu.active() ? "Running" : menu.energyStored() < menu.energyCost() ? "Needs electricity" : "Redstone disabled";
        graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, 65, 0x404040, false);
        graphics.drawString(font, "Power: " + menu.energyCost() + " FE/t", 8, 79, 0x404040, false);
        graphics.drawString(font, menu.energyStored() + " / " + AirConditionerBlockEntity.CAPACITY + " FE", 8, 90, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
