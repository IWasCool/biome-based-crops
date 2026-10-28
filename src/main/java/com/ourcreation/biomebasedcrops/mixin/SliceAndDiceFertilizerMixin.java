package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.CommonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.possible_triangle.sliceanddice.block.sprinkler.actions.FertilizerAction$Type", remap = false)
abstract class SliceAndDiceFertilizerMixin {
    // This action calls performBonemeal directly. Filter each target before its
    // fertilizer success roll; other sprinkler actions and fluid use are unchanged.
    @Redirect(method = "consume$lambda$0", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BonemealableBlock;isValidBonemealTarget(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", remap = false))
    private static boolean biomeBasedCrops$checkSprinkledPlant(BonemealableBlock plant, LevelReader levelReader,
                                                               BlockPos pos, BlockState state) {
        return (!(levelReader instanceof Level level) || CommonEvents.canGrowWithFertilizer(level, pos, state))
                && plant.isValidBonemealTarget(levelReader, pos, state);
    }
}
