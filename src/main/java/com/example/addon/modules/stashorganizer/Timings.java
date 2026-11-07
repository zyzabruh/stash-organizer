package com.example.addon.modules.stashorganizer;

import java.util.function.Supplier;

/**
 * Waits used by the components, in ticks, named by what they wait for instead of by a number.
 * <p>
 * Every wait is scaled by the {@link Latency} profile of the module, so tuning a laggy server is a
 * setting change instead of a code change. The latency is read on every call, so changing it
 * mid-run applies to the next action.
 */
public final class Timings {
    /** Server latency profile: scales every wait between two actions. */
    public enum Latency {
        /** Everything answers within two ticks. */
        Fast(75),
        /** Default: the client sees the server's answer within two or three ticks. */
        Normal(100),
        /** A busy server with a few hundred milliseconds of delay. */
        Slow(175),
        /** A heavily loaded server or a very high ping. */
        VerySlow(300);

        /** Percentage applied to every base wait. */
        public final int percent;

        Latency(int percent) {
            this.percent = percent;
        }
    }

    /** Ticks a slot click needs before the client sees the server's answer. */
    private static final int SLOT_CLICK = 2;
    /** Ticks a shift-clicked deposit needs before it is verified. */
    private static final int SLOT_DEPOSIT = 3;
    /** Ticks a placed block or item frame needs to appear. */
    private static final int BLOCK_PLACE = 6;
    /** Ticks an item frame needs to accept an item. */
    private static final int ENTITY_USE = 4;
    /** Pause after a mined block disappears, before walking onto its drop. */
    private static final int BREAK_SETTLE = 8;
    /** Ticks to let the player walk onto a broken block's drop. */
    private static final int PICKUP_MOVE = 15;
    /** Ticks between two pickup attempts. */
    private static final int PICKUP_RETRY = 10;
    /** Pickup attempts before the dropped item is considered lost. */
    private static final int MAX_PICKUP_TRIES = 6;
    /** Ticks between two item frame rotations. */
    private static final int FRAME_ROTATION = 6;

    private final Supplier<Latency> latency;

    public Timings(Supplier<Latency> latency) {
        this.latency = latency;
    }

    /** Scales a base wait for the current latency profile; a wait is never shorter than one tick. */
    private int scale(int ticks) {
        Latency current = latency.get();
        int percent = current == null ? Latency.Normal.percent : current.percent;
        return Math.max(1, (ticks * percent + 99) / 100);
    }

    public int slotClick() {
        return scale(SLOT_CLICK);
    }

    public int slotDeposit() {
        return scale(SLOT_DEPOSIT);
    }

    public int blockPlace() {
        return scale(BLOCK_PLACE);
    }

    public int entityUse() {
        return scale(ENTITY_USE);
    }

    public int breakSettle() {
        return scale(BREAK_SETTLE);
    }

    public int pickupMove() {
        return scale(PICKUP_MOVE);
    }

    public int pickupRetry() {
        return scale(PICKUP_RETRY);
    }

    public int frameRotation() {
        return scale(FRAME_ROTATION);
    }

    /** Retry count of a pickup: a laggy server gets more attempts, but never an endless loop. */
    public int maxPickupTries() {
        return Math.min(12, Math.max(3, scale(MAX_PICKUP_TRIES)));
    }
}
