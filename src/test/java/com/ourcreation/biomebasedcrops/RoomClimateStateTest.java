package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RoomClimateStateTest {
    @Test
    void naturallyBreathableMatchingRoomStartsComfortableWithoutPower() {
        var room = new RoomClimateState(512, 20, true, 0);
        room.advance(10000, 20, true, true);
        assertEquals(20, room.currentTemperature(), 0.001);
        assertEquals(100, room.oxygenPercent(), 0.001);
        assertTrue(room.breathable());
        assertFalse(room.running(10000));
    }

    @Test
    void paidAirflowFillsHostileRoomGraduallyAndHeatsActualDegrees() {
        var room = new RoomClimateState(512, -183, false, 0);
        drive(room, 0, 10, -183, 20);
        assertTrue(room.oxygenPercent() > 0 && room.oxygenPercent() < 95);
        assertTrue(room.currentTemperature() > -183 && room.currentTemperature() < 20);
        assertFalse(room.breathable());
        drive(room, 10, 40, -183, 20);
        assertEquals(100, room.oxygenPercent(), 0.001);
        assertTrue(room.breathable());
        assertTrue(room.currentTemperature() < 20, "Dial setting must not instantly become actual room temperature");
    }

    @Test
    void largeVolumeFillsAndChangesTemperatureMoreSlowly() {
        var small = new RoomClimateState(512, 5, false, 0);
        var barn = new RoomClimateState(131072, 5, false, 0);
        drive(small, 0, 50, 5, 45);
        drive(barn, 0, 50, 5, 45);
        assertEquals(100, small.oxygenPercent(), 0.001);
        assertTrue(barn.oxygenPercent() < 10);
        assertTrue(small.currentTemperature() > barn.currentTemperature());
        assertTrue(barn.currentTemperature() > 5);
    }

    @Test
    void elapsedUnpaidTimeCannotPretendTheMachineRanWhileUnloaded() {
        var room = new RoomClimateState(512, 20, false, 0);
        room.addPaidDrive(1, 0, 45, true);
        room.advance(1000, 20, false, true);
        assertTrue(room.oxygenPercent() < 3);
        assertTrue(room.currentTemperature() < 21);
        assertFalse(room.breathable());
    }

    @Test
    void sharedUnitDrivesCannotDuplicateOneSourcesOutputAndPowerLossLeaksGradually() {
        var room = new RoomClimateState(512, 20, false, 0);
        room.addPaidDrive(1, 0, 45, true);
        room.addPaidDrive(1, 0, 45, true);
        room.advance(1, 20, false, true);
        assertEquals(2.5, room.oxygenPercent(), 0.001);
        drive(room, 1, 300, 20, 45);
        assertEquals(45, room.currentTemperature(), 0.001);
        float storedOxygen = room.oxygenPercent();
        room.advance(400, 20, false, true);
        assertTrue(room.currentTemperature() < 45 && room.currentTemperature() > 20);
        assertTrue(room.oxygenPercent() < storedOxygen && room.oxygenPercent() > 0);
        float sealedOxygen = room.oxygenPercent();
        room.advance(500, 20, false, false);
        assertTrue(room.oxygenPercent() < sealedOxygen * 0.2);
    }

    @Test
    void realBreathingConsumesSharedMillibucketsAndCanRevokeOxygenProvider() {
        var room = new RoomClimateState(512, 20, false, 0);
        drive(room, 0, 40, 20, 20);
        assertTrue(room.consumeOxygen(512 * 1000 * 0.1F));
        assertEquals(90, room.oxygenPercent(), 0.001);
        assertFalse(room.breathable());
        assertFalse(room.consumeOxygen(Float.NaN));
        assertFalse(room.consumeOxygen(-1));
        assertFalse(room.consumeOxygen(512 * 1000));
    }

    @Test
    void powerPricingIncludesSameBandDegreesVolumeAndNativeExtremesWithoutOverflow() {
        assertEquals(0, AirConditionerBlockEntity.powerCost(20, 20, 512, false));
        assertEquals(2000, AirConditionerBlockEntity.powerCost(20, 20, 512, true));
        assertEquals(2050, AirConditionerBlockEntity.powerCost(21, 20, 512, true));
        assertEquals(4100, AirConditionerBlockEntity.powerCost(21, 20, 1024, true));
        assertEquals(2000 + 203 * 50, AirConditionerBlockEntity.powerCost(20, -183, 512, true));
        assertEquals(AirConditionerBlockEntity.CAPACITY, AirConditionerBlockEntity.powerCost(-20, 1500, 1048576, true));
    }

    @Test
    void realNativeSealerSuppliesAirAndItsRemovalLeavesALeakingReservoir() {
        var room = new RoomClimateState(512, 20, false, 0);
        room.advance(1, 20, true, true);
        assertEquals(100, room.oxygenPercent(), 0.001);
        assertTrue(room.breathable());
        room.advance(2, 20, false, true);
        assertTrue(room.oxygenPercent() < 100 && room.oxygenPercent() > 95);
        room.advance(200, 20, false, true);
        assertFalse(room.breathable());
    }

    @Test
    void retainedSnapshotKeepsProducedAirButCannotReplayQueuedMachineOutput() {
        var room = new RoomClimateState(512, 20, false, 0);
        drive(room, 0, 40, 20, 45);
        assertTrue(room.addPaidDrive(1, 40, 45, true));
        float temperature = room.currentTemperature();
        float oxygen = room.oxygenPercent();
        var restored = RoomClimateState.restore(room.snapshot(), 40, 20, false);
        assertEquals(temperature, restored.currentTemperature(), 0.001);
        assertEquals(oxygen, restored.oxygenPercent(), 0.001);
        assertFalse(restored.running(40));
        restored.advance(41, 20, false, true);
        assertEquals(20 + (temperature - 20) * 0.9995, restored.currentTemperature(), 0.001);
        assertEquals(oxygen * 0.9995, restored.oxygenPercent(), 0.001);
    }

    @Test
    void retainedSnapshotIsIndependentAndOnlyLeaksDuringUnpaidElapsedTime() {
        var room = new RoomClimateState(512, 20, false, 0);
        drive(room, 0, 40, 20, 45);
        var saved = room.snapshot();
        assertTrue(room.consumeOxygen(512 * 1000 * 0.5F));
        var restored = RoomClimateState.restore(saved, 1040, 20, false);
        assertEquals(100 * Math.pow(0.9995, 1000), restored.oxygenPercent(), 0.001);
        assertTrue(restored.currentTemperature() > 20 && restored.currentTemperature() < room.currentTemperature());
        assertFalse(restored.running(1040));
    }

    private static void drive(RoomClimateState room, int from, int until, float nativeTemperature, float target) {
        for (int tick = from; tick < until; tick++) {
            room.advance(tick, nativeTemperature, false, true);
            assertTrue(room.addPaidDrive(1, tick, target, true));
        }
        room.advance(until, nativeTemperature, false, true);
    }
}
