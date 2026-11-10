package com.pro0101.stashorganizer.modules.hyperglide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The envelope arithmetic from Grim issue #2794, checked on plain values: a motion at the prediction must read as
 * inside, an axis past its bound must be reported, and the allowances must only ever be as wide as configured.
 */
class BoostEnvelopeTest {
    private static final BoostEnvelope.Axis forward = new BoostEnvelope.Axis(1.0, 0.0, 0.0);

    @Test
    void aMotionAtThePredictionIsInside() {
        BoostEnvelope.Axis motion = new BoostEnvelope.Axis(0.4, 0.0, 0.0);

        BoostEnvelope.Result result = BoostEnvelope.measure(
            motion, motion, forward, forward, motion, 0.05, 0.0
        );

        assertNotNull(result, "a motion and a prediction can be compared");
        assertEquals(0.0, result.scale(), "no axis is past its bound");
        assertEquals(1.0, result.ratio(), 1.0E-9, "motion and prediction have the same length");
        assertTrue(result.bounded(), "a motion at the prediction is inside the envelope");
        assertFalse(result.exceeded(), "nothing exceeded");
    }

    @Test
    void anAxisBeyondItsBoundIsReported() {
        BoostEnvelope.Axis motion = new BoostEnvelope.Axis(0.45, 0.0, 0.0);
        BoostEnvelope.Axis prediction = new BoostEnvelope.Axis(0.4, 0.0, 0.0);

        BoostEnvelope.Result result = BoostEnvelope.measure(
            motion, prediction, forward, forward, prediction, 0.05, 0.0
        );

        assertNotNull(result);
        assertEquals(1.125, result.scale(), 1.0E-9, "0.45 over a 0.4 bound");
        assertTrue(result.exceeded(), "the axis is past its bound");
        assertEquals(result.ratio(), result.scale(), 1.0E-9, "with no look change both ratios agree");
        assertFalse(result.bounded(), "an exceedance equal to the ratio is not inside");
    }

    @Test
    void aNegativeAxisIsComparedWithItsLowerBound() {
        BoostEnvelope.Axis prediction = new BoostEnvelope.Axis(0.4, 0.0, 0.0);
        BoostEnvelope.Axis motion = new BoostEnvelope.Axis(-0.06, 0.0, 0.0);

        BoostEnvelope.Result result = BoostEnvelope.measure(
            motion, prediction, forward, forward, prediction, 0.05, 0.0
        );

        assertNotNull(result);
        assertEquals(12.0, result.scale(), 1.0E-9, "-0.06 over a -0.005 bound");
    }

    @Test
    void theVerticalAllowanceAbsorbsASmallExcess() {
        BoostEnvelope.Axis motion = new BoostEnvelope.Axis(0.4, 0.02, 0.0);
        BoostEnvelope.Axis prediction = new BoostEnvelope.Axis(0.4, 0.0, 0.0);

        BoostEnvelope.Result roomy = BoostEnvelope.measure(
            motion, prediction, forward, forward, prediction, 0.05, 0.03
        );
        BoostEnvelope.Result tight = BoostEnvelope.measure(
            motion, prediction, forward, forward, prediction, 0.05, 0.0
        );

        assertNotNull(roomy);
        assertNotNull(tight);

        assertEquals(0.0, roomy.scale(), "the allowance covers the excess");
        assertEquals(4.0, tight.scale(), 1.0E-9, "without the allowance the excess is reported");
    }

    @Test
    void aWiderAllowanceLowersTheExceedance() {
        BoostEnvelope.Axis motion = new BoostEnvelope.Axis(0.5, 0.0, 0.0);
        BoostEnvelope.Axis last = new BoostEnvelope.Axis(0.2, 0.0, 0.0);
        BoostEnvelope.Axis prediction = new BoostEnvelope.Axis(0.4, 0.0, 0.0);

        BoostEnvelope.Result tight = BoostEnvelope.measure(
            motion, last, forward, forward, prediction, 0.05, 0.0
        );
        BoostEnvelope.Result wide = BoostEnvelope.measure(
            motion, last, forward, forward, prediction, 0.3, 0.0
        );

        assertNotNull(tight);
        assertNotNull(wide);
        assertTrue(tight.exceeded(), "the tight allowance reports the excess");
        assertTrue(wide.scale() < tight.scale(), "a wider allowance accepts more of the motion");
    }

    @Test
    void nothingIsMeasuredWithoutMotion() {
        BoostEnvelope.Axis still = new BoostEnvelope.Axis(0.0, 0.0, 0.0);

        assertNull(BoostEnvelope.measure(
            still, still, forward, forward, new BoostEnvelope.Axis(0.4, 0.0, 0.0), 0.05, 0.0
        ), "a still motion has nothing to compare");
    }

    @Test
    void nothingIsMeasuredWithoutAPrediction() {
        BoostEnvelope.Axis motion = new BoostEnvelope.Axis(0.4, 0.0, 0.0);
        BoostEnvelope.Axis still = new BoostEnvelope.Axis(0.0, 0.0, 0.0);

        assertNull(BoostEnvelope.measure(
            motion, motion, forward, forward, still, 0.05, 0.0
        ), "a still prediction has nothing to compare against");
    }
}
