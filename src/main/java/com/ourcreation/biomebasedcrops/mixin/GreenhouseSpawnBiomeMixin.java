package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public abstract class GreenhouseSpawnBiomeMixin {
    @Inject(method = "getRoughBiome", at = @At("RETURN"), cancellable = true)
    private static void biomeBasedCrops$roomSpawnCostBiome(BlockPos pos, ChunkAccess chunk, CallbackInfoReturnable<Biome> callback) {
        if (chunk instanceof LevelChunk loaded) {
            var nativeBiome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
            callback.setReturnValue(GreenhouseEnvironment.overlayBiome(loaded.getLevel(), pos, nativeBiome).value());
        }
    }
}
