package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.NorthstarAtmosphere;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Northstar's entity and block temperature queries share the room's gradually conditioned air. */
@Pseudo
@Mixin(targets = "com.lightning.northstar.world.temperature.NorthstarTemperature", remap = false)
abstract class NorthstarRoomTemperatureMixin {
    @Shadow @Final private Level level;

    @Inject(method = "getTemperature(Lnet/minecraft/core/Vec3i;Z)F", at = @At("RETURN"),
            cancellable = true, remap = false)
    private void biomeBasedCrops$temperatureAtBlock(Vec3i pos, boolean useBaseTemperature,
                                                   CallbackInfoReturnable<Float> result) {
        Float temperature = NorthstarAtmosphere.controlledTemperature(level, pos);
        if (temperature != null) result.setReturnValue(temperature);
    }

    @Inject(method = "getTemperature(Lnet/minecraft/world/phys/Vec3;Z)F", at = @At("RETURN"),
            cancellable = true, remap = false)
    private void biomeBasedCrops$temperatureAtPoint(Vec3 pos, boolean useBaseTemperature,
                                                   CallbackInfoReturnable<Float> result) {
        Float temperature = NorthstarAtmosphere.controlledTemperature(level, pos);
        if (temperature != null) result.setReturnValue(temperature);
    }

    // This overload queries Northstar's private transformed-position path directly.
    @Inject(method = "isSealed(Lnet/minecraft/world/phys/Vec3;)Z", at = @At("RETURN"),
            cancellable = true, remap = false)
    private void biomeBasedCrops$temperatureSealedAtPoint(Vec3 pos, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValue() && NorthstarAtmosphere.controlledTemperature(level, pos) != null) {
            result.setReturnValue(true);
        }
    }
}
