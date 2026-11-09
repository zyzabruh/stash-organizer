package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/**
 * A block the organizer wants to point at: the container the current phase is acting on, or the
 * container the carried items are going to.
 */
public record StashHighlight(BlockPos pos, Kind kind) {
    public enum Kind {
        /** The block the current phase is working on. */
        Target,
        /** The container the carried items are being delivered to. */
        Destination
    }
}
