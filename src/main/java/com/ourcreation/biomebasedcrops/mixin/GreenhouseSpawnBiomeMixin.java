package com.ourcreation.biomebasedcrops.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NaturalSpawner.class)
public abstract class GreenhouseSpawnBiomeMixin {
    @WrapOperation(method = "getRoughBiome", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getNoiseBiome(III)Lnet/minecraft/core/Holder;"))
    private static Holder<Biome> biomeBasedCrops$roomSpawnCostBiome(ChunkAccess chunk, int x, int y, int z,
                                                                  Operation<Holder<Biome>> original, BlockPos pos) {
        Holder<Biome> nativeBiome = original.call(chunk, x, y, z);
        return chunk instanceof LevelChunk loaded
                ? GreenhouseEnvironment.overlayBiome(loaded.getLevel(), pos, nativeBiome) : nativeBiome;
    }
}
