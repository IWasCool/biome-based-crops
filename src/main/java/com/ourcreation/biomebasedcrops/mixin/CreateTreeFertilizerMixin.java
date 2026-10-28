package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.CommonEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.equipment.TreeFertilizerItem", remap = false)
abstract class CreateTreeFertilizerMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void biomeBasedCrops$checkRealSaplingClimate(UseOnContext context,
                                                       CallbackInfoReturnable<InteractionResult> ci) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        // Create generates the tree in a temporary world before copying it back.
        // Check the real sapling before generation, particles, or item consumption.
        if (state.getBlock() instanceof BonemealableBlock && state.is(BlockTags.SAPLINGS)
                && !state.getOptionalValue(MangrovePropaguleBlock.HANGING).orElse(false)
                && !CommonEvents.canGrowWithFertilizer(level, context.getClickedPos(), state)) {
            ci.setReturnValue(InteractionResult.FAIL);
        }
    }
}
