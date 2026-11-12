package com.ourcreation.biomebasedcrops.mixin;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.ourcreation.biomebasedcrops.client.viewer.JeiNotebookButtons;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.elements.IconButton$UserInputHandler", remap = false)
abstract class JeiNotebookUtilityButtonInputMixin {
    @Shadow @Final private IIconButtonController controller;

    @Inject(method = "handleUserInput", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void biomeBasedCrops$ignoreHiddenUtilityIcon(Screen screen, UserInput input, IInternalKeyMappings keys,
                                                       CallbackInfoReturnable<Optional<IUserInputHandler>> result) {
        if (input.getKey().getType() == InputConstants.Type.MOUSE && JeiNotebookButtons.hidden(screen, controller))
            result.setReturnValue(Optional.empty());
    }
}
