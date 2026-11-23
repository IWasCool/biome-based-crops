package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.GreenhouseRoomScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
abstract class SableGreenhouseAssemblyMixin {
    // Sable writes directly to chunks when moving blocks between the world and a plot.
    @Inject(method = "markAndNotifyBlock", at = @At("RETURN"), remap = false, require = 0)
    private static void biomeBasedCrops$movedBlock(Level level, BlockPos pos, LevelChunk chunk, BlockState before,
                                                   BlockState after, int flags, int recursion, CallbackInfo callback) {
        GreenhouseRoomScanner.assemblyBlockChanged(level, pos, before, after);
    }
}
