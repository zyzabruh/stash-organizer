package com.example.addon.modules.stashorganizer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The latency profile scales every wait, and a change mid-run applies to the next action. */
class TimingsTest {
    @Test
    void scalesTheWaitsWithTheLatencyProfile() {
        Timings fast = new Timings(() -> Timings.Latency.Fast);
        Timings normal = new Timings(() -> Timings.Latency.Normal);
        Timings slow = new Timings(() -> Timings.Latency.Slow);
        Timings verySlow = new Timings(() -> Timings.Latency.VerySlow);

        assertEquals(2, normal.slotClick());
        assertEquals(4, slow.slotClick());
        assertEquals(6, verySlow.slotClick());
        assertEquals(12, fast.pickupMove());

        assertTrue(fast.blockPlace() <= normal.blockPlace(), "a fast server never waits longer");
        assertTrue(verySlow.blockPlace() > normal.blockPlace(), "a slow server waits longer");
    }

    @Test
    void keepsTheRetryCountBounded() {
        Timings normal = new Timings(() -> Timings.Latency.Normal);
        Timings verySlow = new Timings(() -> Timings.Latency.VerySlow);

        assertEquals(6, normal.maxPickupTries());
        assertEquals(12, verySlow.maxPickupTries(), "a laggy server tries more, but not forever");
        assertTrue(new Timings(() -> Timings.Latency.Fast).maxPickupTries() < normal.maxPickupTries(),
            "a fast server needs fewer attempts");
    }

    @Test
    void appliesALatencyChangeToTheNextAction() {
        AtomicReference<Timings.Latency> latency = new AtomicReference<>(Timings.Latency.Normal);
        Timings timings = new Timings(latency::get);

        assertEquals(2, timings.slotClick());

        latency.set(Timings.Latency.VerySlow);

        assertEquals(6, timings.slotClick());
    }
}
