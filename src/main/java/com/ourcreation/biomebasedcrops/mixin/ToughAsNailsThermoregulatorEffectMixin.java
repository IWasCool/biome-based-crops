package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.ToughAsNailsTemperature;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avoid applying TAN's additional two-category player adjustment to already conditioned air. */
@Pseudo
@Mixin(targets = "toughasnails.block.entity.ThermoregulatorBlockEntity", remap = false)
abstract class ToughAsNailsThermoregulatorEffectMixin {
    @Inject(method = "getEffectAtPos(Lnet/minecraft/core/BlockPos;)Ltoughasnails/block/entity/ThermoregulatorBlockEntity$Effect;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void biomeBasedCrops$useLocalClimate(BlockPos pos, CallbackInfoReturnable<Object> result) {
        Object effect = result.getReturnValue();
        Object localEffect = ToughAsNailsTemperature.localThermoregulatorEffect(effect);
        if (localEffect != effect) result.setReturnValue(localEffect);
    }
}
