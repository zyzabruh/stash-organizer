package com.pro0101.stashorganizer.modules.hyperglide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the phase transitions that replaced the fixed tick counts in rocket-cycle.
 */
class RocketCycleLogicTest {
    @Test
    void theClimbContinuesWhileTheRocketHasNotLiftedThePlayer() {
        assertFalse(RocketCycleLogic.climbDone(0.1, 5, 2, 20, 0.35));
        assertFalse(RocketCycleLogic.climbDone(-0.2, 5, 2, 20, 0.35));
    }

    @Test
    void theClimbEndsOnceTheVerticalSpeedReachesTheTarget() {
        assertTrue(RocketCycleLogic.climbDone(0.35, 5, 2, 20, 0.35));
        assertTrue(RocketCycleLogic.climbDone(0.9, 5, 2, 20, 0.35));
    }

    @Test
    void theClimbKeepsItsMinimumTicksEvenWhenTheRocketHitsAtOnce() {
        assertFalse(RocketCycleLogic.climbDone(1.0, 1, 2, 20, 0.35));
    }

    @Test
    void theClimbTimesOutWhenTheSpeedNeverArrives() {
        assertFalse(RocketCycleLogic.climbDone(0.0, 19, 2, 20, 0.35));
        assertTrue(RocketCycleLogic.climbDone(0.0, 20, 2, 20, 0.35));
    }

    @Test
    void theDiveContinuesWhileThePlayerStillFallsFasterThanTheRelease() {
        assertFalse(RocketCycleLogic.diveDone(-0.8, 5, 3, 40, 0.2));
        assertFalse(RocketCycleLogic.diveDone(-0.21, 5, 3, 40, 0.2));
    }

    @Test
    void theDiveEndsOnceTheFallSlowsToTheRelease() {
        assertTrue(RocketCycleLogic.diveDone(-0.2, 5, 3, 40, 0.2));
        assertTrue(RocketCycleLogic.diveDone(0.1, 5, 3, 40, 0.2));
    }

    @Test
    void theDiveKeepsItsMinimumAndTimesOut() {
        assertFalse(RocketCycleLogic.diveDone(0.0, 2, 3, 40, 0.2));
        assertFalse(RocketCycleLogic.diveDone(-1.0, 39, 3, 40, 0.2));
        assertTrue(RocketCycleLogic.diveDone(-1.0, 40, 3, 40, 0.2));
    }
}
