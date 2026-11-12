package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.client.viewer.GreenhouseJeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.util.ImmutableRect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
abstract class JeiNotebookBookmarkLayoutMixin {
    @Inject(method = "getDisplayArea", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void biomeBasedCrops$belowNotebook(IGuiProperties properties, CallbackInfoReturnable<ImmutableRect2i> result) {
        var layout = GreenhouseJeiPlugin.bottomLayout(properties);
        if (layout == null) return;
        var area = layout.bookmarks();
        result.setReturnValue(new ImmutableRect2i(area.x(), area.y(), area.width(), area.height()));
    }
}
