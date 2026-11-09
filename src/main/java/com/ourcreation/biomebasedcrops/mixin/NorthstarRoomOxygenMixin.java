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

/** Share completed, oxygenated room interiors with Northstar's ordinary breathing and block logic. */
@Pseudo
@Mixin(targets = "com.lightning.northstar.world.oxygen.NorthstarOxygen", remap = false)
abstract class NorthstarRoomOxygenMixin {
    @Shadow @Final private Level level;

    @Inject(method = "getSealer(Lnet/minecraft/core/Vec3i;)Lcom/lightning/northstar/world/oxygen/NorthstarOxygen$Provider;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void biomeBasedCrops$roomOxygenAtBlock(Vec3i pos, CallbackInfoReturnable<Object> result) {
        if (result.getReturnValue() != null) return;
        Object provider = NorthstarAtmosphere.greenhouseProvider(level, pos);
        if (provider != null) result.setReturnValue(provider);
    }

    @Inject(method = "getSealer(Lnet/minecraft/world/phys/Vec3;)Lcom/lightning/northstar/world/oxygen/NorthstarOxygen$Provider;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void biomeBasedCrops$roomOxygenAtPoint(Vec3 pos, CallbackInfoReturnable<Object> result) {
        if (result.getReturnValue() != null) return;
        Object provider = NorthstarAtmosphere.greenhouseProvider(level, pos);
        if (provider != null) result.setReturnValue(provider);
    }
}
