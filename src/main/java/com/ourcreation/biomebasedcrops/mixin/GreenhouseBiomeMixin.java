package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeManager.class)
public abstract class GreenhouseBiomeMixin {
    @Shadow @Final private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    @Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
    private void biomeBasedCrops$roomBiome(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (noiseBiomeSource instanceof Level level) {
            callback.setReturnValue(GreenhouseEnvironment.overlayBiome(level, pos, callback.getReturnValue()));
        }
    }

    @Inject(method = "getNoiseBiomeAtPosition(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void biomeBasedCrops$positionBiome(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (noiseBiomeSource instanceof Level level) {
            callback.setReturnValue(GreenhouseEnvironment.overlayBiome(level, pos, callback.getReturnValue()));
        }
    }

    @Inject(method = "getNoiseBiomeAtPosition(DDD)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void biomeBasedCrops$coordinateBiome(double x, double y, double z, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (noiseBiomeSource instanceof Level level) {
            callback.setReturnValue(GreenhouseEnvironment.overlayBiome(level, BlockPos.containing(x, y, z), callback.getReturnValue()));
        }
    }
}
