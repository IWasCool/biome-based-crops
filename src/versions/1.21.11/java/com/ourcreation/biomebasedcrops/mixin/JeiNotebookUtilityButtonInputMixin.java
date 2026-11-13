package com.ourcreation.biomebasedcrops.mixin;

import java.util.Optional;


import com.ourcreation.biomebasedcrops.client.viewer.JeiNotebookButtons;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.elements.IconButton$ButtonInputHandler", remap = false)
abstract class JeiNotebookUtilityButtonInputMixin {
    @Shadow @Final private IIconButtonController controller;

    @Inject(method = "beginInput", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void biomeBasedCrops$ignoreHiddenUtilityIcon(Screen screen, UserInput input, IInternalKeyMappings keys,
                                                       CallbackInfoReturnable<Optional<IInputInteraction>> result) {
        if (input.isMouseInput() && JeiNotebookButtons.hidden(screen, controller))
            result.setReturnValue(Optional.empty());
    }
}
