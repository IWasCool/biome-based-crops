package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.client.viewer.JeiNotebookButtons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.elements.GuiIconToggleButton", remap = false)
abstract class JeiNotebookUtilityButtonMixin {


    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void biomeBasedCrops$hideNotebookUtilityIcon(GuiGraphics graphics, int mouseX, int mouseY,
                                                       float partialTicks, CallbackInfo result) {
        if (JeiNotebookButtons.hidden(Minecraft.getInstance().screen, (mezz.jei.gui.elements.GuiIconToggleButton) (Object) this)) result.cancel();
    }

    @Inject(method = "isMouseOver", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void biomeBasedCrops$ignoreHiddenIconHover(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> result) {
        if (JeiNotebookButtons.hidden(Minecraft.getInstance().screen, (mezz.jei.gui.elements.GuiIconToggleButton) (Object) this)) result.setReturnValue(false);
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void biomeBasedCrops$reportHiddenIcon(CallbackInfoReturnable<Boolean> result) {
        if (JeiNotebookButtons.hidden(Minecraft.getInstance().screen, (mezz.jei.gui.elements.GuiIconToggleButton) (Object) this)) result.setReturnValue(false);
    }
}
