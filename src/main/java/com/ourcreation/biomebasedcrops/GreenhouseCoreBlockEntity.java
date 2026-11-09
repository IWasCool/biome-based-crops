package com.ourcreation.biomebasedcrops;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GreenhouseCoreBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation targetBiome;

    public GreenhouseCoreBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseContent.CORE_ENTITY.get(), pos, state);
    }

    @Nullable
    public ResourceLocation targetBiome() {
        return targetBiome;
    }

    public void setTargetBiome(ResourceLocation biome) {
        targetBiome = biome;
        setChanged();
        GreenhouseEnvironment.clearCache();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        targetBiome = tag.contains("TargetBiome") ? ResourceLocation.tryParse(tag.getString("TargetBiome")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (targetBiome != null) {
            tag.putString("TargetBiome", targetBiome.toString());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
