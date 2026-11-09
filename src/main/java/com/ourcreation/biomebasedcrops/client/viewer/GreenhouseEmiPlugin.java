package com.ourcreation.biomebasedcrops.client.viewer;

import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
                    .map(ingredient -> new EmiStackInteraction(EmiStack.of(ingredient.stack())))
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
}
