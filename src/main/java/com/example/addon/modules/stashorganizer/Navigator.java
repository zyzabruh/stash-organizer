package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/**
 * Walks next to a block and reports when the player is close enough to interact. The scripts only
 * talk to this interface, so a test can answer "arrived" without pathing anywhere.
 */
public interface Navigator {
    enum Result {
        ARRIVED,
        MOVING,
        FAILED
    }

    /**
     * @return true once the player is close enough to interact with the block
     */
    boolean walkTo(BlockPos pos);

    /** Detailed variant so callers can skip an unreachable target instead of stopping the run. */
    Result walkToDetailed(BlockPos pos);
}
