package com.ourcreation.biomebasedcrops.client.viewer;

import java.util.List;

import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import dev.architectury.event.CompoundEventResult;
import me.shedaniel.math.Rectangle;
import me.shedaniel.math.Point;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.DisplayBoundsProvider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

@REIPluginClient
public final class GreenhouseReiPlugin implements REIClientPlugin {
    private static boolean ready;

    @Override public void registerScreens(ScreenRegistry registry) {
        if (ModList.get().isLoaded("emi")) return;
        registry.registerDecider(new DisplayBoundsProvider<FieldGuideScreen>() {
            @Override public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return FieldGuideScreen.class.isAssignableFrom(screen);
            }
            @Override public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
                return screen instanceof FieldGuideScreen guide && GreenhouseViewerBridge.initializedBounds(guide) != null
                        ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            @Override @Nullable public Rectangle getScreenBounds(FieldGuideScreen screen) {
                var bounds = GreenhouseViewerBridge.initializedBounds(screen);
                if (bounds == null) return null;
                return new Rectangle(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
            }
        });
        registry.registerFocusedStack((screen, mouse) -> {
            if (!(screen instanceof FieldGuideScreen guide)) return CompoundEventResult.pass();
            if (GreenhouseViewerBridge.initializedBounds(guide) == null) return CompoundEventResult.pass();
            var ingredient = guide.viewerIngredientAt(mouse.getX(), mouse.getY());
            if (ingredient.isEmpty()) return CompoundEventResult.pass();
            return CompoundEventResult.interruptTrue(EntryStacks.of(GreenhouseViewerBridge.identity(ingredient.get().stack())));
        });
        ready = true;
    }

    public static boolean open(ItemStack stack, boolean uses) {
        if (!ready) return false;
        ViewSearchBuilder search = ViewSearchBuilder.builder();
        if (uses) search.addUsagesFor(EntryStacks.of(stack));
        else search.addRecipesFor(EntryStacks.of(stack));
        return search.open();
    }

    public static boolean renderTooltip(ItemStack stack, List<Component> details, int x, int y) {
        if (!ready) return false;
        var entry = EntryStacks.of(stack);
        var tooltip = entry.getTooltip(TooltipContext.of(new Point(x, y),
                Item.TooltipContext.of(Minecraft.getInstance().level)));
        if (tooltip == null) return false;
        tooltip.addAllTexts(details).withContextStack(entry).queue();
        return true;
    }
}
