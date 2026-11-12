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
    public static final int CAPACITY = 100_000;
    public static final int BASE_FE_PER_TICK = 360;
    public static final int MAX_RECEIVE = 10_000;
    private final Battery battery = new Battery();
    private CropClimate selectedClimate = CropClimate.NEUTRAL;
    private RoomClimateState lastRoomState;
    private long lastTickedGameTime = Long.MIN_VALUE;
    private long lastPaidTick = Long.MIN_VALUE;

    public AirConditionerBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseContent.AIR_CONDITIONER_ENTITY.get(), pos, state);
    }

    public CropClimate selectedClimate() { return selectedClimate; }
    public CropClimate outletClimate() { return selectedClimate; }
    public int energyStored() { return battery.getEnergyStored(); }
    public IEnergyStorage energyStorage() { return battery; }
    public IEnergyStorage energy() { return battery; }
    public boolean hasPaidAirflow() {
        return level != null && !isRemoved() && getBlockState().getValue(AirConditionerBlock.ACTIVE)
                && (level.isClientSide || lastPaidTick >= level.getGameTime() - 1);
    }
    public boolean hasThermalInfluence() { return hasPaidAirflow(); }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AirConditionerBlockEntity unit) {
        if (state.getValue(AirConditionerBlock.ACTIVE)) {
            AirConditionerBlock.emitAirflow(level, pos, state);
            AirConditionerBlock.emitIntakeAirflow(level, pos, state);
        }
    }

    private RoomClimateState roomState(boolean discover) {
        if (level == null || level.isClientSide) return null;
        RoomClimateState current = GreenhouseEnvironment.controllerAirState(level, worldPosition, discover);
        if (lastRoomState != null && lastRoomState != current) lastRoomState.removeDrive(worldPosition.asLong());
        lastRoomState = current;
        return current;
    }

    public CropClimate localClimate() {
        return level == null ? CropClimate.NEUTRAL : GreenhouseEnvironment.naturalClimateForController(level, worldPosition);
    }
    public CropClimate currentClimate() {
        RoomClimateState state = roomState(false);
        if (state != null) return state.climateAt(worldPosition, level.getGameTime());
        return level == null ? CropClimate.NEUTRAL : GreenhouseEnvironment.currentClimate(level,
                worldPosition.relative(getBlockState().getValue(AirConditionerBlock.FACING)));
    }
    public float oxygenPercent() {
        RoomClimateState state = roomState(false);
        if (state != null) return state.oxygenPercent();
        if (lastRoomState != null) return lastRoomState.oxygenPercent();
        return level == null ? 100F : GreenhouseEnvironment.oxygenPercent(level, worldPosition);
    }
    public int roomVolume() {
        RoomClimateState state = roomState(false);
        return state == null ? 0 : state.roomVolume();
    }
    public int currentEnergyCost() {
        if (level == null) return 0;
        RoomClimateState state = roomState(false);
        CropClimate natural = state == null ? localClimate() : state.naturalClimate();
        boolean breathable = state == null ? NorthstarAtmosphere.naturalAtmosphereBreathable(level) : state.naturalBreathable();
        return selectedClimate != natural || !breathable ? BASE_FE_PER_TICK : 0;
    }
    public void setClimate(CropClimate climate) {
        Objects.requireNonNull(climate, "climate");
        if (selectedClimate == climate) return;
        selectedClimate = climate;
        updateTargetIndicator();
        changed(true);
    }
    private void updateTargetIndicator() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        boolean warm = selectedClimate == CropClimate.WARM || selectedClimate == CropClimate.HOT;
        if (state.getValue(AirConditionerBlock.WARM) != warm)
            level.setBlock(worldPosition, state.setValue(AirConditionerBlock.WARM, warm), Block.UPDATE_CLIENTS);
    }
    public boolean canProvideClimate(CropClimate climate) {
        RoomClimateState state = roomState(false);
        return state != null && state.breathable() && state.climateAt(worldPosition, level.getGameTime()) == climate;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, AirConditionerBlockEntity unit) {
        if (level.isClientSide || unit.lastTickedGameTime == level.getGameTime()) return;
        unit.lastTickedGameTime = level.getGameTime();
        unit.updateTargetIndicator();
        RoomClimateState room = unit.roomState(true);
        int cost = unit.currentEnergyCost();
        boolean working = cost > 0 && !level.hasNeighborSignal(pos) && unit.battery.getEnergyStored() >= cost;
        boolean outlet = AirConditionerAirflow.clearOutlet(level, pos, unit.getBlockState().getValue(AirConditionerBlock.FACING));
        if (working && outlet && (room == null || room.addPaidDrive(pos.asLong(), level.getGameTime(), unit.selectedClimate, true))) {
            unit.battery.spend(cost);
            unit.lastPaidTick = level.getGameTime();
        } else working = false;
        if (!working && room != null) room.removeDrive(pos.asLong());
        unit.setActive(working);
        unit.setChanged();
        if (working && level.getGameTime() % 10 == 0)
            level.sendBlockUpdated(pos, unit.getBlockState(), unit.getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override public void setRemoved() {
        if (lastRoomState != null) lastRoomState.removeDrive(worldPosition.asLong());
        super.setRemoved();
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
    @Override public Component getDisplayName() { return Component.translatable("block.biome_based_crops.air_conditioner"); }
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
        selectedClimate = tag.contains("Climate")
                ? CropClimate.byId(tag.getString("Climate")).orElse(CropClimate.NEUTRAL)
                : tag.contains("TargetTemperature") ? LegacyClimate.fromCelsius(tag.getInt("TargetTemperature")) : CropClimate.NEUTRAL;
        lastRoomState = null;
        lastTickedGameTime = Long.MIN_VALUE;
        lastPaidTick = Long.MIN_VALUE;
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
        void spend(int amount) { energy -= amount; setChanged(); }
        void load(int amount) { energy = Math.max(0, Math.min(CAPACITY, amount)); }
    }
}
