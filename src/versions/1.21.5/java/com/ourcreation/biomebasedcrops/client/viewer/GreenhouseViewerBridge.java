package com.ourcreation.biomebasedcrops.client.viewer;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Either;
import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.jetbrains.annotations.Nullable;

/** Optional viewer APIs remain isolated from the ordinary guide and dedicated server. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID, value = Dist.CLIENT)
public final class GreenhouseViewerBridge {
    private GreenhouseViewerBridge() {}

    /** Viewer screen-change hooks can run before Minecraft initializes the new screen. */
    @Nullable
    static Rect2i initializedBounds(FieldGuideScreen screen) {
        if (screen.width <= 1 || screen.height <= 1) return null;
        Rect2i bounds = screen.bookBounds();
        if (bounds.getWidth() <= 1 || bounds.getHeight() <= 1
                || bounds.getX() < 0 || bounds.getY() < 0
                || bounds.getX() > screen.width - bounds.getWidth()
                || bounds.getY() > screen.height - bounds.getHeight()) return null;
        return bounds;
    }

    public static boolean open(ItemStack ingredient, boolean uses) {
        if (ingredient.isEmpty()) return false;
        ItemStack identity = identity(ingredient);
        try {
            if (ModList.get().isLoaded("emi")) {
                return GreenhouseEmiPlugin.open(identity, uses) || !uses && GreenhouseEmiPlugin.open(identity, true);
            }
            if (ModList.get().isLoaded("roughlyenoughitems")) {
                return GreenhouseReiPlugin.open(identity, uses) || !uses && GreenhouseReiPlugin.open(identity, true);
            }
            if (ModList.get().isLoaded("jei")) {
                return GreenhouseJeiPlugin.open(identity, uses) || !uses && GreenhouseJeiPlugin.open(identity, true);
            }
        } catch (RuntimeException | LinkageError error) {
            BiomeBasedCrops.LOGGER.debug("Recipe viewer could not open a guide ingredient", error);
        }
        return false;
    }

    /** Large building requirements are quantities, not oversized viewer ingredient stacks. */
    static ItemStack identity(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    /** Generic books need a keyboard adapter because EMI forwards native keys only from container screens. */
    public static boolean keyPressed(FieldGuideScreen screen, int keyCode, int scanCode) {
        if (!ModList.get().isLoaded("emi") || initializedBounds(screen) == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double x = minecraft.mouseHandler.xpos() * screen.width / window.getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * screen.height / window.getScreenHeight();
        var ingredient = screen.viewerIngredientAt(x, y);
        if (ingredient.isEmpty()) return false;
        try {
            return GreenhouseEmiPlugin.keyPressed(identity(ingredient.get().stack()), keyCode, scanCode);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            BiomeBasedCrops.LOGGER.debug("EMI could not handle a field guide ingredient key", error);
            return false;
        }
    }

    /** Uses the active viewer's ingredient renderer and tooltip metadata. */
    public static boolean renderTooltip(GuiGraphics graphics, ItemStack ingredient, List<Component> extraLines,
                                        int mouseX, int mouseY) {
        if (ingredient.isEmpty()) return false;
        ItemStack identity = identity(ingredient);
        List<Component> details = List.copyOf(extraLines);
        try {
            if (ModList.get().isLoaded("emi")) {
                return GreenhouseEmiPlugin.renderTooltip(graphics, identity, details, mouseX, mouseY);
            }
            if (ModList.get().isLoaded("roughlyenoughitems")) {
                return GreenhouseReiPlugin.renderTooltip(identity, details, mouseX, mouseY);
            }
            if (ModList.get().isLoaded("jei")) {
                return GreenhouseJeiPlugin.renderTooltip(graphics, identity, details, mouseX, mouseY);
            }
        } catch (RuntimeException | LinkageError error) {
            BiomeBasedCrops.LOGGER.debug("Recipe viewer could not render a guide ingredient tooltip", error);
        }
        return false;
    }

    /** NeoForge's public tooltip factory preserves native viewer components without private draw APIs. */
    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ViewerTooltipComponent.class, ViewerTooltipComponent::component);
    }

    static void renderComponents(GuiGraphics graphics, ItemStack stack, List<ClientTooltipComponent> components,
                                 int mouseX, int mouseY) {
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>(components.size());
        components.forEach(component -> elements.add(Either.right(new ViewerTooltipComponent(component))));
        graphics.renderComponentTooltipFromElements(Minecraft.getInstance().font, elements, mouseX, mouseY, stack);
    }

    private record ViewerTooltipComponent(ClientTooltipComponent component) implements TooltipComponent {}
}
