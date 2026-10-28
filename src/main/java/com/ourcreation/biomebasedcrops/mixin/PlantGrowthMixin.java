package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.CommonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class PlantGrowthMixin {
    // Some plants (notably mushroom spreading and sapling stages) never fire CropGrowEvent.
    // Guard the actual natural tick so bonemeal remains controlled by its own setting.
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void biomeBasedCrops$checkClimate(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!CommonEvents.canGrowNaturally(level, pos, (BlockState) (Object) this)) {
            ci.cancel();
        }
    }
}
