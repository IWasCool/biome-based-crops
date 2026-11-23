package com.ourcreation.biomebasedcrops.mixin;

import com.ourcreation.biomebasedcrops.AirConditionerBlockEntity;
import com.ourcreation.biomebasedcrops.ChunkClimateControllers;
import com.ourcreation.biomebasedcrops.GreenhouseRoomScanner;
import com.ourcreation.biomebasedcrops.ThermoregulatorDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class ChunkClimateControllersMixin implements ChunkClimateControllers {
    @Shadow public abstract Map<BlockPos, BlockEntity> getBlockEntities();
    @Unique private List<BlockEntity> biomeBasedCrops$controllers;

    @Override
    public List<BlockEntity> biomeBasedCrops$climateControllers() {
        if (biomeBasedCrops$controllers == null) {
            List<BlockEntity> controllers = new ArrayList<>();
            for (BlockEntity entity : getBlockEntities().values()) {
                if (entity instanceof AirConditionerBlockEntity
                        || ThermoregulatorDetector.isExternalController(entity.getBlockState())) {
                    controllers.add(entity);
                }
            }
            biomeBasedCrops$controllers = List.copyOf(controllers);
        }
        return biomeBasedCrops$controllers;
    }

    @Inject(method = {"removeBlockEntity", "clearAllBlockEntities"}, at = @At("RETURN"))
    private void biomeBasedCrops$invalidateControllers(CallbackInfo callback) {
        biomeBasedCrops$controllers = null;
    }

    @Inject(method = "setBlockEntity", at = @At("RETURN"))
    private void biomeBasedCrops$trackController(BlockEntity entity, CallbackInfo callback) {
        biomeBasedCrops$controllers = null;
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (!chunk.getLevel().isClientSide && GreenhouseRoomScanner.isRegistrationController(entity.getBlockState())) {
            GreenhouseRoomScanner.trackController(chunk.getLevel(), entity.getBlockPos());
        }
    }
}
