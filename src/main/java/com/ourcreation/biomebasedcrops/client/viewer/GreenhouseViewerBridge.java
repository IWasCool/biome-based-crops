package com.ourcreation.biomebasedcrops.client.viewer;

import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Optional viewer APIs remain isolated from the ordinary guide and dedicated server. */
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
        try {
            if (ModList.get().isLoaded("emi")) return GreenhouseEmiPlugin.open(ingredient, uses);
            if (ModList.get().isLoaded("roughlyenoughitems")) return GreenhouseReiPlugin.open(ingredient, uses);
            if (ModList.get().isLoaded("jei")) return GreenhouseJeiPlugin.open(ingredient, uses);
        } catch (RuntimeException | LinkageError error) {
            BiomeBasedCrops.LOGGER.debug("Recipe viewer could not open a guide ingredient", error);
        }
        return false;
    }
}
