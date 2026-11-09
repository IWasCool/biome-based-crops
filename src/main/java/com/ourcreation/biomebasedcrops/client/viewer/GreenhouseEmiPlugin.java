package com.ourcreation.biomebasedcrops.client.viewer;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@EmiEntrypoint
public final class GreenhouseEmiPlugin implements EmiPlugin {
    private static boolean ready;

    @Override public void register(EmiRegistry registry) {
        registry.addScreenBoundsProvider(FieldGuideScreen.class, screen -> {
            var bounds = GreenhouseViewerBridge.initializedBounds(screen);
            if (bounds == null) return null;
            return new Bounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        });
        registry.addStackProvider(FieldGuideScreen.class, (screen, x, y) -> {
            if (GreenhouseViewerBridge.initializedBounds(screen) == null) return EmiStackInteraction.EMPTY;
            return screen.viewerIngredientAt(x, y)
                    // Keep EMI's native hover/hotkeys; the book handles mouse clicks so recipe-less natural
                    // blocks can fall back to their uses instead of EMI consuming an empty recipe lookup.
                    .map(ingredient -> new EmiStackInteraction(
                            EmiStack.of(GreenhouseViewerBridge.identity(ingredient.stack())), null, false))
                    .orElse(EmiStackInteraction.EMPTY);
        });
        ready = true;
    }

    public static boolean open(ItemStack stack, boolean uses) {
        if (!ready) return false;
        Screen previous = Minecraft.getInstance().screen;
        if (uses) EmiApi.displayUses(EmiStack.of(stack));
        else EmiApi.displayRecipes(EmiStack.of(stack));
        return Minecraft.getInstance().screen != previous;
    }

    /** EMI's keyboard mixin handles container screens only; its API exposes no generic-screen key adapter. */
    public static boolean keyPressed(ItemStack stack, int keyCode, int scanCode) throws ReflectiveOperationException {
        if (!ready || stack.isEmpty() || KeyBindings.ACCESS.isEmpty()) return false;
        KeyBindings bindings = KeyBindings.ACCESS.get();
        if ((Boolean) bindings.matches.invoke(bindings.recipes.get(null), keyCode, scanCode)) {
            return GreenhouseViewerBridge.open(stack, false);
        }
        if ((Boolean) bindings.matches.invoke(bindings.uses.get(null), keyCode, scanCode)) {
            return GreenhouseViewerBridge.open(stack, true);
        }
        return false;
    }

    public static boolean renderTooltip(GuiGraphics graphics, ItemStack stack, List<Component> details, int x, int y) {
        if (!ready) return false;
        List<ClientTooltipComponent> tooltip = new ArrayList<>(EmiStack.of(stack).getTooltip());
        details.forEach(line -> tooltip.add(EmiTooltipComponents.of(line)));
        GreenhouseViewerBridge.renderComponents(graphics, stack, tooltip, x, y);
        return true;
    }

    /** Public configured bindings from the pinned EMI runtime; no hardcoded keys or private access. */
    private record KeyBindings(Field recipes, Field uses, Method matches) {
        private static final Optional<KeyBindings> ACCESS = find();

        private static Optional<KeyBindings> find() {
            try {
                Class<?> config = Class.forName("dev.emi.emi.config.EmiConfig");
                Class<?> bind = Class.forName("dev.emi.emi.input.EmiBind");
                return Optional.of(new KeyBindings(config.getField("viewRecipes"), config.getField("viewUses"),
                        bind.getMethod("matchesKey", int.class, int.class)));
            } catch (ReflectiveOperationException | LinkageError error) {
                BiomeBasedCrops.LOGGER.debug("EMI's configured recipe keys are unavailable for the field guide", error);
                return Optional.empty();
            }
        }
    }
}
