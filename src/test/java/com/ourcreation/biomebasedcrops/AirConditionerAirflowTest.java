package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class AirConditionerAirflowTest {
    private static final BlockPos MACHINE = new BlockPos(-48, 70, 31);

    @Test
    void roundedCoverageReachesTheNearShouldersAndTheFourBlockFrontEdge() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            Direction side = face.getClockWise();
            for (int sign : new int[]{-1, 1}) {
                assertInside(face, MACHINE.relative(face).relative(side, 3 * sign));
                assertInside(face, MACHINE.relative(face, 2).relative(side, 3 * sign));
                assertInside(face, MACHINE.relative(face, 3).relative(side, 2 * sign));
            }
            assertInside(face, MACHINE.relative(face, 4));
        }
    }

    @Test
    void roundedCoverageExcludesCornersOutsideTheCircleForEveryFacing() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            Direction side = face.getClockWise();
            for (int sign : new int[]{-1, 1}) {
                assertOutside(face, MACHINE.relative(face, 3).relative(side, 3 * sign));
                assertOutside(face, MACHINE.relative(face, 4).relative(side, sign));
                assertOutside(face, MACHINE.relative(face).relative(side, 4 * sign));
            }
            assertOutside(face, MACHINE.relative(face, 5));
        }
    }

    @Test
    void outletCannotConditionItsOwnBlockSidePlaneOrRear() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            assertOutside(face, MACHINE);
            assertOutside(face, MACHINE.relative(face.getClockWise()));
            assertOutside(face, MACHINE.relative(face.getCounterClockWise(), 3));
            assertOutside(face, MACHINE.relative(face.getOpposite()));
        }
    }

    @Test
    void aCropCanBeOneBlockAboveOrBelowTheOutletButNotTwo() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos front = MACHINE.relative(face, 4);
            assertInside(face, front.above());
            assertInside(face, front.below());
            assertOutside(face, front.above(2));
            assertOutside(face, front.below(2));
        }
    }

    @Test
    void everyTargetKeepsItsFourBlockCoreThenReturnsOneCategoryPerBlockToAnyNativeClimate() {
        CropClimate[] climates = CropClimate.values();
        for (CropClimate target : climates) for (CropClimate natural : climates) {
            for (Direction face : Direction.Plane.HORIZONTAL) {
                CropClimate previous = target;
                for (int distance = 1; distance <= 9; distance++) {
                    CropClimate actual = AirConditionerAirflow.falloffClimate(target, natural, MACHINE, MACHINE.relative(face, distance));
                    if (distance <= 4) assertEquals(target, actual, "Core changed for " + target + " / " + natural);
                    else {
                        assertTrue(Math.abs(actual.ordinal() - previous.ordinal()) <= 1, "Skipped a climate category");
                        assertTrue(Math.abs(actual.ordinal() - natural.ordinal()) <= Math.abs(previous.ordinal() - natural.ordinal()), "Moved away from native climate");
                        if (previous != natural) assertTrue(actual != previous, "Falloff failed to advance by one category");
                    }
                    if (distance >= 8) assertEquals(natural, actual, "Falloff overshot ambient");
                    previous = actual;
                }
            }
        }
    }

    @Test
    void circularFalloffUsesHorizontalAnnuliRatherThanSquareDistanceOrVerticalOffset() {
        assertEquals(CropClimate.COLD, AirConditionerAirflow.falloffClimate(CropClimate.ICY, CropClimate.HOT,
                MACHINE, MACHINE.offset(4, 1, 1)));
        assertEquals(CropClimate.COLD, AirConditionerAirflow.falloffClimate(CropClimate.ICY, CropClimate.HOT,
                MACHINE, MACHINE.offset(3, -1, 4)));
        assertEquals(CropClimate.NEUTRAL, AirConditionerAirflow.falloffClimate(CropClimate.ICY, CropClimate.HOT,
                MACHINE, MACHINE.offset(4, 0, 4)));
        assertEquals(CropClimate.WARM, AirConditionerAirflow.falloffClimate(CropClimate.ICY, CropClimate.HOT,
                MACHINE, MACHINE.offset(6, 0, 3)));
    }

    private static void assertInside(Direction face, BlockPos crop) {
        assertTrue(AirConditionerAirflow.inOutletStream(MACHINE, face, crop), () -> face + " should reach " + crop);
    }

    private static void assertOutside(Direction face, BlockPos crop) {
        assertFalse(AirConditionerAirflow.inOutletStream(MACHINE, face, crop), () -> face + " must not reach " + crop);
    }
}
