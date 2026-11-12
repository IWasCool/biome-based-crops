package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RoomClimateStateTest {
    @Test void anOpeningKeepsItsClimateForAMinuteThenDriftsDespiteContinuedPower() {
        var room = new RoomClimateState(512, CropClimate.NEUTRAL, true, 10);
        room.addPaidDrive(BlockPos.ZERO.asLong(), 10, CropClimate.HOT, true);
        room.setLeakStart(10, 10);
        room.advance(1209, CropClimate.NEUTRAL, true, true);
        room.addPaidDrive(BlockPos.ZERO.asLong(), 1209, CropClimate.HOT, true);
        assertEquals(CropClimate.HOT, room.climateAt(BlockPos.ZERO, 1209));
        room.setLeakStart(1209, 1209);
        room.advance(1410, CropClimate.NEUTRAL, true, true);
        room.addPaidDrive(BlockPos.ZERO.asLong(), 1410, CropClimate.HOT, true);
        assertEquals(CropClimate.WARM, room.climateAt(BlockPos.ZERO, 1410));
        assertFalse(room.leakDissipated(1410));
        room.advance(1610, CropClimate.NEUTRAL, true, true);
        room.addPaidDrive(BlockPos.ZERO.asLong(), 1610, CropClimate.HOT, true);
        assertEquals(CropClimate.NEUTRAL, room.currentClimate());
        assertFalse(room.hasControlAt(BlockPos.ZERO, 1610));
        assertTrue(room.leakDissipated(1610));
    }

    @Test void expirationWaitsForEveryCapturedCategoryToReachTheNaturalClimate() {
        var room = new RoomClimateState(512, CropClimate.NEUTRAL, true, 0);
        room.addPaidDrive(0, 0, CropClimate.WARM, true);
        room.addPaidDrive(1, 0, CropClimate.ICY, true);
        room.setLeakStart(0, 0);
        assertFalse(room.leakDissipated(1200));
        assertFalse(room.leakDissipated(1400));
        assertTrue(room.leakDissipated(1600));
        var natural = new RoomClimateState(512, CropClimate.NEUTRAL, true, 0);
        natural.setLeakStart(0, 0);
        assertFalse(natural.leakDissipated(1199));
        assertTrue(natural.leakDissipated(1200));
    }

    @Test void resealingEndsTheOldLeakAndANewOpeningGetsItsOwnDeadline() {
        var room = new RoomClimateState(512, CropClimate.NEUTRAL, true, 0);
        room.addPaidDrive(0, 0, CropClimate.ICY, true);
        room.setLeakStart(0, 0);
        room.advance(1000, CropClimate.NEUTRAL, true, true);
        assertEquals(CropClimate.ICY, room.currentClimate());
        room.setLeakStart(-1, 1000);
        room.addPaidDrive(0, 1000, CropClimate.ICY, true);
        assertFalse(room.leaking());
        room.setLeakStart(1000, 1000);
        room.advance(2200, CropClimate.NEUTRAL, true, true);
        assertEquals(CropClimate.ICY, room.currentClimate());
        room.advance(2400, CropClimate.NEUTRAL, true, true);
        assertEquals(CropClimate.COLD, room.currentClimate());
    }

    @Test void naturalRoomsDoNotNeedPowerAndAnUnloadedOldDriveCannotBeCaptured() {
        var room = new RoomClimateState(512, CropClimate.WARM, true, 0);
        room.setLeakStart(0, 0);
        room.advance(10000, CropClimate.WARM, true, true);
        assertEquals(CropClimate.WARM, room.currentClimate());
        assertFalse(room.hasControlAt(BlockPos.ZERO, 10000));
        room.setLeakStart(-1, 10000);
        room.addPaidDrive(0, 10000, CropClimate.ICY, true);
        room.setLeakStart(20000, 20000);
        room.advance(20000, CropClimate.WARM, true, true);
        assertEquals(CropClimate.WARM, room.currentClimate());
    }

    @Test void matchingNaturalClimateNeedsNoController() {
        var room = new RoomClimateState(512, CropClimate.WARM, true, 0);
        room.advance(10000, CropClimate.WARM, true, true);
        assertEquals(CropClimate.WARM, room.currentClimate());
        assertEquals(CropClimate.WARM, room.climateAt(BlockPos.ZERO, 10000));
        assertTrue(room.breathable());
        assertFalse(room.running(10000));
        assertFalse(room.hasControlAt(BlockPos.ZERO, 10000));
    }

    @Test void allFiveSettingsApplyImmediatelyWithoutInventingIntermediateDegrees() {
        var room = new RoomClimateState(512, CropClimate.NEUTRAL, true, 0);
        long tick = 0;
        for (CropClimate climate : CropClimate.values()) {
            room.advance(tick, CropClimate.NEUTRAL, true, true);
            assertTrue(room.addPaidDrive(BlockPos.ZERO.asLong(), tick, climate, true));
            assertEquals(climate, room.currentClimate());
            assertEquals(climate, room.targetClimate());
            assertEquals(climate, room.climateAt(new BlockPos(5, 3, -2), tick));
            tick++;
        }
        room.advance(tick + 1, CropClimate.NEUTRAL, true, true);
        assertEquals(CropClimate.NEUTRAL, room.currentClimate());
        assertFalse(room.hasControlAt(BlockPos.ZERO, tick + 1));
    }

    @Test void controlReachesTheWholeSealedRoomAndUsesTheNearestPaidSource() {
        var room = new RoomClimateState(32768, CropClimate.NEUTRAL, true, 0);
        room.addPaidDrive(BlockPos.ZERO.asLong(), 0, CropClimate.HOT, true);
        assertTrue(room.hasControlAt(new BlockPos(16, 0, 0), 0));
        assertTrue(room.hasControlAt(new BlockPos(0, 16, 0), 0));
        assertTrue(room.hasControlAt(new BlockPos(31, 31, 31), 0));
        assertEquals(CropClimate.HOT, room.climateAt(new BlockPos(31, 31, 31), 0));
        room.addPaidDrive(new BlockPos(10, 0, 0).asLong(), 0, CropClimate.ICY, true);
        assertEquals(CropClimate.ICY, room.climateAt(new BlockPos(9, 0, 0), 0));
        assertEquals(CropClimate.HOT, room.climateAt(new BlockPos(1, 0, 0), 0));
    }

    @Test void duplicateSourcesDoNotCreateFreeAirAndOpenRoomsLoseTheirController() {
        var room = new RoomClimateState(512, CropClimate.COLD, false, 0);
        room.addPaidDrive(0, 0, CropClimate.NEUTRAL, true);
        room.addPaidDrive(0, 0, CropClimate.NEUTRAL, true);
        room.advance(1, CropClimate.COLD, false, true);
        assertEquals(2.5, room.oxygenPercent(), 0.001);
        assertEquals(CropClimate.NEUTRAL, room.currentClimate());
        room.advance(2, CropClimate.COLD, false, false);
        assertEquals(CropClimate.COLD, room.currentClimate());
        assertFalse(room.running(2));
        assertFalse(room.addPaidDrive(0, 2, CropClimate.HOT, true));
    }

    @Test void theLargestSupportedRoomAcceptsEveryRequiredController() {
        int volume = 1_048_576;
        var room = new RoomClimateState(volume, CropClimate.NEUTRAL, true, 0);
        int required = volume / GreenhouseEnvironment.INTERIOR_BLOCKS_PER_UNIT;
        assertTrue(required > 64);
        for (int i = 0; i < required; i++)
            assertTrue(room.addPaidDrive(new BlockPos(i, 0, 0).asLong(), 0, CropClimate.HOT, true),
                    "Room rejected required controller " + i);
        assertEquals(volume, room.airflow(0));
        assertEquals(CropClimate.HOT, room.climateAt(new BlockPos(0, 255, 0), 0));
    }

    @Test void airStillFillsGraduallyAndLargerRoomsNeedMoreSuppliers() {
        var small = new RoomClimateState(512, CropClimate.ICY, false, 0);
        var barn = new RoomClimateState(131072, CropClimate.ICY, false, 0);
        drive(small, 0, 50, CropClimate.ICY, CropClimate.NEUTRAL);
        drive(barn, 0, 50, CropClimate.ICY, CropClimate.NEUTRAL);
        assertTrue(small.breathable());
        assertTrue(barn.oxygenPercent() < 10);
        assertEquals(CropClimate.NEUTRAL, small.currentClimate());
        assertEquals(CropClimate.NEUTRAL, barn.currentClimate());
    }

    @Test void withdrawingAStoppedControllerRevokesItsClimateImmediately() {
        var room = new RoomClimateState(512, CropClimate.NEUTRAL, true, 0);
        room.addPaidDrive(0, 0, CropClimate.HOT, true);
        assertEquals(CropClimate.HOT, room.climateAt(BlockPos.ZERO, 0));
        room.removeDrive(0);
        assertEquals(CropClimate.NEUTRAL, room.climateAt(BlockPos.ZERO, 0));
        assertFalse(room.hasControlAt(BlockPos.ZERO, 0));
    }

    @Test void unloadedTimeCannotInventPaidClimateOrOxygen() {
        var room = new RoomClimateState(512, CropClimate.COLD, false, 0);
        room.addPaidDrive(0, 0, CropClimate.HOT, true);
        room.advance(1000, CropClimate.COLD, false, true);
        assertTrue(room.oxygenPercent() < 3);
        assertEquals(CropClimate.COLD, room.currentClimate());
        assertFalse(room.running(1000));
    }

    @Test void realBreathingConsumesSharedMillibuckets() {
        var room = new RoomClimateState(512, CropClimate.NEUTRAL, false, 0);
        drive(room, 0, 40, CropClimate.NEUTRAL, CropClimate.NEUTRAL);
        assertTrue(room.consumeOxygen(512 * 1000 * 0.1F));
        assertEquals(90, room.oxygenPercent(), 0.001);
        assertFalse(room.breathable());
        assertFalse(room.consumeOxygen(Float.NaN));
        assertFalse(room.consumeOxygen(-1));
        assertFalse(room.consumeOxygen(512 * 1000));
    }

    @Test void snapshotsKeepStoredOxygenButNeverReplayControllerOutput() {
        var room = new RoomClimateState(512, CropClimate.COLD, false, 0);
        drive(room, 0, 40, CropClimate.COLD, CropClimate.HOT);
        var saved = room.snapshot();
        var restored = RoomClimateState.restore(saved, 40, CropClimate.COLD, false);
        assertEquals(room.oxygenPercent(), restored.oxygenPercent(), 0.001);
        assertEquals(CropClimate.COLD, restored.currentClimate());
        assertFalse(restored.running(40));
        restored.advance(1040, CropClimate.COLD, false, true);
        assertEquals(100 * Math.pow(0.9995, 1000), restored.oxygenPercent(), 0.001);
    }

    private static void drive(RoomClimateState room, int from, int until, CropClimate natural, CropClimate target) {
        for (int tick = from; tick < until; tick++) {
            room.advance(tick, natural, false, true);
            assertTrue(room.addPaidDrive(0, tick, target, true));
        }
        room.advance(until, natural, false, true);
    }
}
