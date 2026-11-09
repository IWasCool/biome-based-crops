package com.ourcreation.biomebasedcrops;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class AirConditionerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CAPACITY = 20_000;
    public static final int FE_PER_CLIMATE_STEP = 20;
    public static final int MAX_RECEIVE = 1_000;
    private final Battery battery = new Battery();
    private CropClimate selectedClimate = CropClimate.NEUTRAL;
    private long lastPoweredTick = Long.MIN_VALUE;

    public AirConditionerBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseContent.AIR_CONDITIONER_ENTITY.get(), pos, state);
    }

    public CropClimate selectedClimate() { return selectedClimate; }
    public int energyStored() { return battery.getEnergyStored(); }
    public IEnergyStorage energyStorage() { return battery; }
    public IEnergyStorage energy() { return battery; }

    public CropClimate localClimate() {
        return level == null ? CropClimate.NEUTRAL : GreenhouseEnvironment.naturalClimateForController(level, worldPosition);
    }

    public int currentEnergyCost() {
        if (level == null) return 0;
        return Math.abs(selectedClimate.ordinal() - localClimate().ordinal()) * FE_PER_CLIMATE_STEP;
    }

    public void setClimate(CropClimate climate) {
        Objects.requireNonNull(climate, "climate");
        if (selectedClimate == climate) return;
        selectedClimate = climate;
        lastPoweredTick = Long.MIN_VALUE;
        setActive(false);
        changed(true);
    }

    public boolean canProvideClimate(CropClimate climate) {
        if (level == null || isRemoved() || selectedClimate != climate || level.hasNeighborSignal(worldPosition)) return false;
        int cost = currentEnergyCost();
        return cost > 0 && (lastPoweredTick == level.getGameTime() || battery.getEnergyStored() >= cost);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirConditionerBlockEntity unit) {
        if (level.isClientSide) return;
        int cost = unit.currentEnergyCost();
        boolean working = GreenhouseSettings.enabled() && cost > 0 && !level.hasNeighborSignal(pos)
                && unit.battery.getEnergyStored() >= cost;
        if (working) {
            unit.battery.spend(cost);
            unit.lastPoweredTick = level.getGameTime();
        } else unit.lastPoweredTick = Long.MIN_VALUE;
        unit.setActive(working);
    }

    private void setActive(boolean active) {
        if (level == null || getBlockState().getValue(AirConditionerBlock.ACTIVE) == active) return;
        level.setBlock(worldPosition, getBlockState().setValue(AirConditionerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        GreenhouseEnvironment.invalidateClimate();
    }

    private void changed(boolean synchronize) {
        setChanged();
        GreenhouseEnvironment.invalidateClimate();
        if (synchronize && level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.biome_based_crops.air_conditioner");
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AirConditionerMenu(id, inventory, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", battery.getEnergyStored());
        tag.putString("Climate", selectedClimate.name());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        battery.load(tag.getInt("Energy"));
        selectedClimate = CropClimate.byId(tag.getString("Climate")).orElse(CropClimate.NEUTRAL);
        lastPoweredTick = Long.MIN_VALUE;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    private final class Battery extends EnergyStorage {
        Battery() { super(CAPACITY, MAX_RECEIVE, 0); }

        @Override public int receiveEnergy(int amount, boolean simulate) {
            int before = getEnergyStored();
            int received = super.receiveEnergy(amount, simulate);
            if (!simulate && received > 0) {
                setChanged();
                int cost = currentEnergyCost();
                if (before < cost && getEnergyStored() >= cost) GreenhouseEnvironment.invalidateClimate();
            }
            return received;
        }

        void spend(int amount) {
            energy -= amount;
            setChanged();
        }

        void load(int amount) { energy = Math.max(0, Math.min(CAPACITY, amount)); }
    }
}
