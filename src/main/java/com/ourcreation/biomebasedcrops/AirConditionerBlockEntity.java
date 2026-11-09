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
    public static final int CAPACITY = 100_000_000;
    public static final int BASE_FE_PER_TICK = 2_000;
    public static final int FE_PER_DEGREE = 50;
    public static final int POWER_REFERENCE_VOLUME = 512;
    public static final int MAX_RECEIVE = 20_000_000;
    private final Battery battery = new Battery();
    private int targetTemperature = 20;
    private RoomClimateState lastRoomState;
    private long lastTickedGameTime = Long.MIN_VALUE;

    public AirConditionerBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseContent.AIR_CONDITIONER_ENTITY.get(), pos, state);
    }

    public CropClimate selectedClimate() { return ThermoregulatorDetector.northstarClimate(targetTemperature); }
    public int targetTemperature() { return targetTemperature; }
    public int energyStored() { return battery.getEnergyStored(); }
    public IEnergyStorage energyStorage() { return battery; }
    public IEnergyStorage energy() { return battery; }

    private RoomClimateState roomState(boolean discover) {
        if (level == null || level.isClientSide) return null;
        RoomClimateState current = GreenhouseEnvironment.controllerAirState(level, worldPosition, discover);
        if (current != null) lastRoomState = current;
        else if (lastRoomState != null) lastRoomState.advance(level.getGameTime(),
                GreenhouseEnvironment.naturalTemperatureForController(level, worldPosition),
                NorthstarAtmosphere.naturalAtmosphereBreathable(level), false);
        return current;
    }

    public CropClimate localClimate() {
        return ThermoregulatorDetector.northstarClimate(level == null ? 20F
                : GreenhouseEnvironment.naturalTemperatureForController(level, worldPosition));
    }

    public float currentTemperature() {
        RoomClimateState state = roomState(false);
        if (state != null) return state.currentTemperature();
        if (lastRoomState != null) return lastRoomState.currentTemperature();
        return level == null ? 20F : GreenhouseEnvironment.currentTemperature(level, worldPosition);
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
        float nativeTemperature = state == null ? GreenhouseEnvironment.naturalTemperatureForController(level, worldPosition)
                : state.naturalTemperature();
        float actual = state == null ? nativeTemperature : state.currentTemperature();
        boolean breathable = state == null ? NorthstarAtmosphere.naturalAtmosphereBreathable(level) : state.naturalBreathable();
        boolean needsWork = !breathable || Math.abs(targetTemperature - nativeTemperature) > 0.1F
                || Math.abs(targetTemperature - actual) > 0.1F;
        return powerCost(targetTemperature, nativeTemperature, state == null ? POWER_REFERENCE_VOLUME : state.roomVolume(), needsWork);
    }

    public static int powerCost(int target, float nativeTemperature, int volume, boolean needsWork) {
        if (!needsWork) return 0;
        double delta = Float.isFinite(nativeTemperature) ? Math.abs(target - nativeTemperature) : 0;
        long base = BASE_FE_PER_TICK + Math.min(CAPACITY, (long) Math.ceil(delta * FE_PER_DEGREE));
        long scale = (Math.max(1, Math.min(1048576, volume)) + POWER_REFERENCE_VOLUME - 1L) / POWER_REFERENCE_VOLUME;
        return (int) Math.min(CAPACITY, base * scale);
    }

    public void setClimate(CropClimate climate) {
        setTargetTemperature(GreenhouseEnvironment.temperatureForClimate(Objects.requireNonNull(climate, "climate")));
    }

    public void setTargetTemperature(int temperature) {
        int clamped = Math.max(-20, Math.min(50, temperature));
        if (targetTemperature == clamped) return;
        targetTemperature = clamped;
        setActive(false);
        changed(true);
    }

    /** Availability comes from conditioned room air, never merely a selected dial setting. */
    public boolean canProvideClimate(CropClimate climate) {
        RoomClimateState state = roomState(false);
        return state != null && state.breathable()
                && ThermoregulatorDetector.northstarClimate(state.currentTemperature()) == climate;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, AirConditionerBlockEntity unit) {
        if (level.isClientSide || unit.lastTickedGameTime == level.getGameTime()) return;
        unit.lastTickedGameTime = level.getGameTime();
        if (!GreenhouseSettings.enabled()) {
            unit.setActive(false);
            return;
        }
        RoomClimateState room = unit.roomState(true);
        int cost = unit.currentEnergyCost();
        boolean working = room != null && cost > 0 && !level.hasNeighborSignal(pos)
                && unit.battery.getEnergyStored() >= cost;
        if (working && room.addPaidDrive(pos.asLong(), level.getGameTime(), unit.targetTemperature, true)) {
            unit.battery.spend(cost);
        } else {
            working = false;
        }
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
        tag.putInt("TargetTemperature", targetTemperature);
        tag.putString("Climate", selectedClimate().name());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        battery.load(tag.getInt("Energy"));
        targetTemperature = tag.contains("TargetTemperature") ? Math.max(-20, Math.min(50, tag.getInt("TargetTemperature")))
                : GreenhouseEnvironment.temperatureForClimate(CropClimate.byId(tag.getString("Climate")).orElse(CropClimate.NEUTRAL));
        lastRoomState = null;
        lastTickedGameTime = Long.MIN_VALUE;
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
