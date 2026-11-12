package com.ourcreation.biomebasedcrops;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Categorical climate control and breathable air shared by one sealed room. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID)
public final class RoomClimateState {
    public static final float BREATHABLE_PERCENT = 95F;
    public static final int AIRFLOW_PER_UNIT = 256;
    public static final int LEAK_GRACE_TICKS = 20 * 60;
    public static final int LEAK_STEP_TICKS = 20 * 10;
    // One controller per 256 enclosed blocks can condition the largest supported room.
    private static final int MAX_DRIVES = 1_048_576 / GreenhouseEnvironment.INTERIOR_BLOCKS_PER_UNIT;
    private final int roomVolume;
    private final Map<Long, Drive> drives = new HashMap<>();
    private CropClimate naturalClimate;
    private boolean naturalBreathable;
    private boolean airtight = true;
    private double oxygen;
    private long lastTick;
    private long leakStart = -1;
    private final Map<Long, CropClimate> leakedClimates = new HashMap<>();

    public RoomClimateState(int volume, CropClimate naturalClimate, boolean breathable, long tick) {
        if (volume < 1 || volume > 1048576) throw new IllegalArgumentException("Invalid room air volume");
        roomVolume = volume;
        this.naturalClimate = Objects.requireNonNull(naturalClimate);
        naturalBreathable = breathable;
        oxygen = breathable ? 1D : 0D;
        lastTick = tick;
    }

    public int roomVolume() { return roomVolume; }
    public CropClimate naturalClimate() { return naturalClimate; }
    public CropClimate currentClimate() {
        if (leakStart >= 0) return leakingClimate(null, lastTick);
        return drives.entrySet().stream().filter(e -> fresh(e.getValue(), lastTick))
                .min(Map.Entry.comparingByKey()).map(e -> e.getValue().climate).orElse(naturalClimate);
    }
    public CropClimate targetClimate() { return currentClimate(); }
    public float oxygenPercent() { return (float) (oxygen * 100D); }
    public boolean breathable() { return oxygenPercent() >= BREATHABLE_PERCENT; }
    public boolean naturalBreathable() { return naturalBreathable; }
    public boolean running(long tick) { return airtight && drives.values().stream().anyMatch(d -> fresh(d, tick)); }
    public int airflow(long tick) {
        long suppliers = drives.values().stream().filter(d -> fresh(d, tick)).count();
        return airtight ? (int) Math.min(roomVolume, AIRFLOW_PER_UNIT * suppliers) : 0;
    }

    public boolean hasControlAt(BlockPos query, long tick) {
        return leakStart >= 0 ? leakingClimate(query, tick) != naturalClimate : nearest(query, tick) != null;
    }
    public CropClimate climateAt(BlockPos query, long tick) {
        if (leakStart >= 0) return leakingClimate(query, tick);
        Drive nearest = nearest(query, tick);
        return nearest == null ? naturalClimate : nearest.climate;
    }

    /** A known room remembers its climate through a brief opening, never through a new survey. */
    public void setLeakStart(long openedAt, long tick) {
        if (openedAt == leakStart) return;
        if (openedAt < 0) {
            leakStart = -1;
            leakedClimates.clear();
            return;
        }
        if (leakStart >= 0) return; // More holes or paid AC ticks cannot restart the deadline.
        for (var entry : drives.entrySet())
            if (fresh(entry.getValue(), tick))
                leakedClimates.put(entry.getKey(), entry.getValue().climate);
        leakStart = openedAt;
    }

    public boolean leaking() { return leakStart >= 0; }

    /** A fully dissipated leak may discard its old geometry and discover a changed enclosure. */
    public boolean leakDissipated(long tick) {
        if (leakStart < 0 || tick - leakStart < LEAK_GRACE_TICKS) return false;
        long steps = (tick - leakStart - LEAK_GRACE_TICKS) / LEAK_STEP_TICKS;
        return leakedClimates.values().stream().allMatch(climate ->
                Math.abs(climate.ordinal() - naturalClimate.ordinal()) <= steps);
    }

    private CropClimate leakingClimate(BlockPos query, long tick) {
        CropClimate captured = naturalClimate;
        double nearest = Double.POSITIVE_INFINITY;
        long selected = Long.MAX_VALUE;
        for (var entry : leakedClimates.entrySet()) {
            double distance = query == null ? 0 : BlockPos.of(entry.getKey()).distSqr(query);
            if (distance < nearest || distance == nearest && entry.getKey() < selected) {
                nearest = distance;
                selected = entry.getKey();
                captured = entry.getValue();
            }
        }
        long steps = Math.max(0, tick - leakStart - LEAK_GRACE_TICKS) / LEAK_STEP_TICKS;
        int difference = naturalClimate.ordinal() - captured.ordinal();
        int movement = (int) Math.min(Math.abs(difference), steps);
        return CropClimate.values()[captured.ordinal() + Integer.signum(difference) * movement];
    }

    private Drive nearest(BlockPos query, long tick) {
        if (!airtight) return null;
        Drive result = null;
        double nearest = Double.POSITIVE_INFINITY;
        long selected = Long.MAX_VALUE;
        for (var entry : drives.entrySet()) {
            if (!fresh(entry.getValue(), tick)) continue;
            long packed = entry.getKey();
            double dx = BlockPos.getX(packed) - query.getX(), dy = BlockPos.getY(packed) - query.getY(), dz = BlockPos.getZ(packed) - query.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= nearest && (distance < nearest || result == null || entry.getKey() < selected)) {
                nearest = distance;
                selected = entry.getKey();
                result = entry.getValue();
            }
        }
        return result;
    }

    private boolean fresh(Drive drive, long tick) { return airtight && drive.tick <= tick && drive.tick >= tick - 1; }

    /** Only paid, current controller output supplies a climate; oxygen can remain after shutdown. */
    public void advance(long tick, CropClimate nativeClimate, boolean nativeBreathable, boolean sealed) {
        Objects.requireNonNull(nativeClimate);
        if (tick < lastTick) drives.clear();
        long elapsed = Math.max(0, tick - lastTick);
        naturalClimate = nativeClimate;
        naturalBreathable = nativeBreathable;
        airtight = sealed;
        if (nativeBreathable) oxygen = 1D;
        long passiveTicks = elapsed;
        if (sealed && leakStart < 0 && elapsed > 0) {
            int suppliers = (int) drives.values().stream().filter(d -> d.tick == lastTick && d.suppliesAir).count();
            if (suppliers > 0) {
                double oneUnit = Math.min(1D / 40D, (double) AIRFLOW_PER_UNIT / roomVolume);
                oxygen = nativeBreathable ? 1D : Math.min(1D, oxygen + oneUnit * suppliers);
                passiveTicks--;
            }
        }
        if (passiveTicks > 0) {
            double ambient = nativeBreathable ? 1D : 0D;
            oxygen = ambient + (oxygen - ambient) * Math.pow(1D - passiveRate(sealed && leakStart < 0), passiveTicks);
        }
        oxygen = Math.max(0D, Math.min(1D, oxygen));
        lastTick = tick;
        drives.values().removeIf(d -> !sealed || d.tick < tick - 1 || d.tick > tick);
    }

    public void removeDrive(long source) { drives.remove(source); }

    public boolean addPaidDrive(long source, long tick, CropClimate target, boolean suppliesAir) {
        Objects.requireNonNull(target);
        if (tick != lastTick || !airtight) return false;
        if (!drives.containsKey(source) && drives.size() >= MAX_DRIVES) return false;
        drives.put(source, new Drive(tick, target, suppliesAir));
        return true;
    }

    public boolean consumeOxygen(float millibuckets) {
        if (!Float.isFinite(millibuckets) || millibuckets < 0) return false;
        double fraction = millibuckets / (roomVolume * 1000D);
        if (fraction > oxygen) return false;
        oxygen = Math.max(0D, oxygen - fraction);
        return true;
    }

    Snapshot snapshot() { return new Snapshot(roomVolume, naturalClimate, naturalBreathable, oxygen, lastTick); }
    static RoomClimateState restore(Snapshot saved, long tick, CropClimate nativeClimate, boolean nativeBreathable) {
        RoomClimateState restored = new RoomClimateState(saved.volume, saved.nativeClimate, saved.nativeBreathable, saved.tick);
        restored.oxygen = saved.oxygen;
        restored.advance(tick, nativeClimate, nativeBreathable, true);
        return restored;
    }
    record Snapshot(int volume, CropClimate nativeClimate, boolean nativeBreathable, double oxygen, long tick) { }
    private double passiveRate(boolean sealed) {
        return !sealed ? 0.02D : Math.max(0.00005D, Math.min(0.005D, 0.0005D * Math.cbrt(512D / roomVolume)));
    }
    private record Drive(long tick, CropClimate climate, boolean suppliesAir) { }

    @SubscribeEvent public static void tickRooms(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide) GreenhouseEnvironment.advanceRoomAir(event.getLevel());
    }
}
