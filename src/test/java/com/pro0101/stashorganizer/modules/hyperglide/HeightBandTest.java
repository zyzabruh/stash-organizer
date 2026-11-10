package com.pro0101.stashorganizer.modules.hyperglide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the two ways rocket-cycle picks a phase: the height band and the player's keys.
 */
class HeightBandTest {
    @Test
    void belowTheBandTheCycleClimbs() {
        assertEquals(HeightBand.Phase.CLIMB, HeightBand.automatic(100.0, 120.0, 8.0, 40.0));
    }

    @Test
    void insideTheBandItHoldsLevel() {
        assertEquals(HeightBand.Phase.CRUISE, HeightBand.automatic(120.0, 120.0, 8.0, 40.0));
        assertEquals(HeightBand.Phase.CRUISE, HeightBand.automatic(112.0, 120.0, 8.0, 40.0));
        assertEquals(HeightBand.Phase.CRUISE, HeightBand.automatic(128.0, 120.0, 8.0, 40.0));
    }

    @Test
    void aboveTheBandItDives() {
        assertEquals(HeightBand.Phase.DIVE, HeightBand.automatic(140.0, 120.0, 8.0, 40.0));
    }

    @Test
    void theFloorWinsOverTheBand() {
        // A band that sits under the floor must not be able to hold the cycle down there.
        assertEquals(HeightBand.Phase.CLIMB, HeightBand.automatic(60.0, 50.0, 5.0, 60.0));
    }

    @Test
    void theJumpKeyClimbsAndTheSneakKeyDives() {
        assertEquals(HeightBand.Phase.CLIMB, HeightBand.manual(true, false));
        assertEquals(HeightBand.Phase.DIVE, HeightBand.manual(false, true));
    }

    @Test
    void holdingBothKeysOrNeitherHoldsLevel() {
        assertEquals(HeightBand.Phase.CRUISE, HeightBand.manual(true, true));
        assertEquals(HeightBand.Phase.CRUISE, HeightBand.manual(false, false));
    }
}
