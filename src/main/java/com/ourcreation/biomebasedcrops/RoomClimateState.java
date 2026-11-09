package com.ourcreation.biomebasedcrops;

import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One physical air volume shared by every controller connected to a scanned room. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID)
public final class RoomClimateState {
    public static final float BREATHABLE_PERCENT = 95F;
    public static final int AIRFLOW_PER_UNIT = 256;
    private static final int MAX_DRIVES = 64;
    private final int roomVolume;
    private final Map<Long, Drive> drives = new HashMap<>();
    private float naturalTemperature;
    private boolean naturalBreathable;
    private boolean airtight = true;
    private double temperature;
    private double oxygen;
    private float targetTemperature;
    private long lastTick;
    private long driveTick = Long.MIN_VALUE;

    public RoomClimateState(int volume, float naturalTemperature, boolean breathable, long tick) {
        if (volume < 1 || volume > 1048576 || !Float.isFinite(naturalTemperature)) {
            throw new IllegalArgumentException("Invalid room air volume or native temperature");
        }
        roomVolume = volume;
        this.naturalTemperature = naturalTemperature;
        naturalBreathable = breathable;
        temperature = naturalTemperature;
        oxygen = breathable ? 1D : 0D;
        targetTemperature = naturalTemperature;
        lastTick = tick;
    }

    public int roomVolume() { return roomVolume; }
    public float currentTemperature() { return (float) temperature; }
    public float targetTemperature() { return targetTemperature; }
    public float oxygenPercent() { return (float) (oxygen * 100D); }
    public boolean breathable() { return oxygenPercent() >= BREATHABLE_PERCENT; }
    public boolean naturalBreathable() { return naturalBreathable; }
    public float naturalTemperature() { return naturalTemperature; }
    public boolean running(long tick) { return airtight && driveTick >= tick - 1 && !drives.isEmpty(); }
    public int airflow(long tick) {
        return running(tick) ? Math.min(roomVolume, AIRFLOW_PER_UNIT * drives.size()) : 0;
    }

    /** Integrate only ticks actually paid by controllers; elapsed unloaded time never creates free air. */
    public void advance(long tick, float nativeTemperature, boolean nativeBreathable, boolean sealed) {
        if (!Float.isFinite(nativeTemperature)) throw new IllegalArgumentException("Invalid native room temperature");
        if (tick < lastTick) {
            lastTick = tick;
            driveTick = Long.MIN_VALUE;
            drives.clear();
        }
        long elapsed = tick - lastTick;
        naturalTemperature = nativeTemperature;
        naturalBreathable = nativeBreathable;
        if (nativeBreathable) oxygen = 1D;
        airtight = sealed;
        if (elapsed == 0) return;
        long passiveTicks = elapsed;
        if (sealed && driveTick == lastTick && !drives.isEmpty()) {
            double target = 0;
            int airSuppliers = 0;
            for (Drive drive : drives.values()) {
                target += drive.temperature;
                if (drive.suppliesAir) airSuppliers++;
            }
            target /= drives.size();
            targetTemperature = (float) target;
            double oneUnit = Math.min(1D / 40D, (double) AIRFLOW_PER_UNIT / roomVolume);
            double replacement = Math.min(0.25D, oneUnit * drives.size());
            temperature += (target - temperature) * replacement;
            if (Math.abs(target - temperature) < 0.05D) temperature = target;
            if (naturalBreathable) oxygen = 1D;
            else if (airSuppliers > 0) oxygen = Math.min(1D, oxygen + oneUnit * airSuppliers);
            else oxygen *= 1D - passiveRate(true);
            passiveTicks--;
        }
        if (passiveTicks > 0) {
            double retention = Math.pow(1D - passiveRate(sealed), passiveTicks);
            temperature = naturalTemperature + (temperature - naturalTemperature) * retention;
            double ambientOxygen = naturalBreathable ? 1D : 0D;
            oxygen = ambientOxygen + (oxygen - ambientOxygen) * retention;
        }
        oxygen = Math.max(0D, Math.min(1D, oxygen));
        lastTick = tick;
    }

    public boolean addPaidDrive(long source, long tick, float target, boolean suppliesAir) {
        if (!Float.isFinite(target) || tick != lastTick || !airtight) return false;
        if (driveTick != tick) {
            driveTick = tick;
            drives.clear();
        }
        if (!drives.containsKey(source) && drives.size() >= MAX_DRIVES) return false;
        drives.put(source, new Drive(target, suppliesAir));
        targetTemperature = (float) drives.values().stream().mapToDouble(Drive::temperature).average().orElse(target);
        return true;
    }

    /** Northstar oxygen drains are measured in millibuckets; one filled cell holds one bucket. */
    public boolean consumeOxygen(float millibuckets) {
        if (!Float.isFinite(millibuckets) || millibuckets < 0) return false;
        double fraction = millibuckets / (roomVolume * 1000D);
        if (fraction > oxygen) return false;
        oxygen = Math.max(0D, oxygen - fraction);
        return true;
    }

    Snapshot snapshot() {
        return new Snapshot(roomVolume, naturalTemperature, naturalBreathable, temperature, oxygen, lastTick);
    }

    static RoomClimateState restore(Snapshot saved, long tick, float nativeTemperature, boolean nativeBreathable) {
        RoomClimateState restored = new RoomClimateState(saved.volume, saved.nativeTemperature, saved.nativeBreathable, saved.tick);
        restored.temperature = saved.temperature;
        restored.oxygen = saved.oxygen;
        // The snapshot contains paid air already produced, never future machine output.
        restored.advance(tick, nativeTemperature, nativeBreathable, true);
        return restored;
    }

    record Snapshot(int volume, float nativeTemperature, boolean nativeBreathable, double temperature, double oxygen, long tick) { }

    private double passiveRate(boolean sealed) {
        if (!sealed) return 0.02D;
        return Math.max(0.00005D, Math.min(0.005D, 0.0005D * Math.cbrt(512D / roomVolume)));
    }

    private record Drive(float temperature, boolean suppliesAir) { }

    @SubscribeEvent
    public static void tickRooms(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide) GreenhouseEnvironment.advanceRoomAir(event.getLevel());
    }
}
