package com.ourcreation.biomebasedcrops.client.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

@JeiPlugin
public final class GreenhouseJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    private static IFocusFactory focuses;

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "field_guide");
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        focuses = registration.getJeiHelpers().getFocusFactory();
        if (ModList.get().isLoaded("emi") || ModList.get().isLoaded("roughlyenoughitems")) return;
        registration.addGuiScreenHandler(FieldGuideScreen.class, new IScreenHandler<>() {
            @Override @Nullable public IGuiProperties apply(FieldGuideScreen screen) {
                var bounds = GreenhouseViewerBridge.initializedBounds(screen);
                if (bounds == null) return null;
                return new Properties(screen.getClass(), bounds.getX(), bounds.getY(), bounds.getWidth(),
                        bounds.getHeight(), screen.width, screen.height);
            }

        });
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
                if (runtime == null || !(Minecraft.getInstance().screen instanceof FieldGuideScreen screen)) return Optional.empty();
                if (GreenhouseViewerBridge.initializedBounds(screen) == null) return Optional.empty();
                return screen.viewerIngredientAt(mouseX, mouseY)
                        .flatMap(ingredient -> runtime.getIngredientManager().createClickableIngredient(VanillaTypes.ITEM_STACK,
                                GreenhouseViewerBridge.identity(ingredient.stack()), ingredient.bounds(), true))
                        .map(ingredient -> (IClickableIngredient<?>) ingredient);
            }
        });
    }

    @Override public void onRuntimeAvailable(IJeiRuntime available) { runtime = available; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    public static boolean open(ItemStack stack, boolean uses) {
        if (runtime == null || focuses == null) return false;
        Screen previous = Minecraft.getInstance().screen;
        runtime.getRecipesGui().show(focuses.createFocus(uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK, stack));
        return Minecraft.getInstance().screen != previous;
    }

    public static boolean renderTooltip(GuiGraphics graphics, ItemStack stack, List<Component> details, int x, int y) {
        if (runtime == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        var renderer = runtime.getIngredientManager().getIngredientRenderer(VanillaTypes.ITEM_STACK);
        List<Component> tooltip = new ArrayList<>(renderer.getTooltip(stack, minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL));
        tooltip.addAll(details);
        runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                .flatMap(runtime.getJeiHelpers().getModIdHelper()::getModNameForTooltip).ifPresent(tooltip::add);
        graphics.renderTooltip(renderer.getFontRenderer(minecraft, stack), tooltip, stack.getTooltipImage(), stack, x, y);
        return true;
    }

    private record Properties(Class<? extends Screen> screenClass, int guiLeft, int guiTop, int guiXSize,
                              int guiYSize, int screenWidth, int screenHeight) implements IGuiProperties {}
}
