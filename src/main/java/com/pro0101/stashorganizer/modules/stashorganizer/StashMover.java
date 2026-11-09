package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/**
 * Abstraction over the pathing backend. The organizer only needs "walk next to this block" and
 * "stop", so both Baritone and Meteor's path manager can implement it.
 */
public interface StashMover {
    boolean isAvailable();

    void moveTo(BlockPos pos);

    /** Moves to a block the player has to stand in (not merely next to it). */
    default void moveToStanding(BlockPos pos) {
        moveTo(pos);
    }

    void stop();

    boolean isPathing();

    /** Called once before the organizer starts moving. */
    default void prepare() {
    }

    /** Called when the organizer stops, to restore any global setting it changed. */
    default void shutdown() {
    }
}
