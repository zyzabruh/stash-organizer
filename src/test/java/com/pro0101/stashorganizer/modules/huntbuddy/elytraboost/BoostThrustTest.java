package com.pro0101.stashorganizer.modules.huntbuddy.elytraboost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares what a boost is worth before and after extending its duration.
 *
 * <p>The recurrence is run tick by tick as the game applies it, then against the closed form, so the duration
 * comparison is not just the same arithmetic written twice.
 */
class BoostThrustTest {
    private static final double VANILLA = 1.5;
    private static final double TOLERANCE = 1.0E-9;

    /** Runs the boost one tick at a time, the way the game does. */
    private static double loop(int ticks, double initial, double multiplier) {
        double speed = initial;

        for (int tick = 0; tick < ticks; tick++) {
            speed = BoostThrust.step(speed, multiplier);
        }

        return speed;
    }

    @Test
    void oneTickAppliesHalfTheSpeedPlusTheThrust() {
        assertEquals(0.5 * 1.0 + 1.35, BoostThrust.step(1.0, 2.5), TOLERANCE);
    }

    @Test
    void noTicksLeaveTheSpeedAlone() {
        assertEquals(1.0, BoostThrust.after(0, 1.0, 2.5), TOLERANCE);
        assertEquals(1.0, loop(0, 1.0, 2.5), TOLERANCE);
    }

    @Test
    void theClosedFormMatchesTheTickByTickLoop() {
        assertEquals(loop(16, 1.0, 2.5), BoostThrust.after(16, 1.0, 2.5), TOLERANCE);
        assertEquals(loop(40, 1.0, 2.5), BoostThrust.after(40, 1.0, 2.5), TOLERANCE);
        assertEquals(loop(16, 0.4, VANILLA), BoostThrust.after(16, 0.4, VANILLA), TOLERANCE);
    }

    @Test
    void theDurationSliderCountsFiftyMillisecondTicks() {
        assertEquals(16, BoostThrust.ticks(800));
        assertEquals(40, BoostThrust.ticks(2000));
        assertEquals(16, BoostThrust.ticks(849));
        assertEquals(0, BoostThrust.ticks(0));
    }

    @Test
    void theVanillaBoostPlateausAtTheGrimCeiling() {
        // 0.1 + 0.5 * 1.5 over a 0.5 damping gives 1.7 - exactly the per-axis cap Grim uses for fireworks.
        assertEquals(1.7, BoostThrust.after(200, 0.0, VANILLA), TOLERANCE);
    }

    @Test
    void aLongerBoostEndsFasterWithDiminishingReturns() {
        double shortBoost = BoostThrust.after(BoostThrust.ticks(800), 1.0, 2.5);
        double longBoost = BoostThrust.after(BoostThrust.ticks(2000), 1.0, 2.5);
        double plateau = 2.7;

        assertTrue(longBoost > shortBoost, "1200 ms more must end faster");
        assertEquals(2.699974060058594, shortBoost, 1.0E-9, "16 boosted ticks");
        assertEquals(plateau, longBoost, 1.0E-6, "40 ticks are within a millionth of the plateau");

        double firstGain = shortBoost - 1.0;
        double secondGain = longBoost - shortBoost;

        assertTrue(secondGain < firstGain, "the second 1200 ms buys less than the first 800");
    }
}
