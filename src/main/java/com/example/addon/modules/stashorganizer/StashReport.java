package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/**
 * Collects what happened during a run (phase durations, protected boxes, stacks left in place) and
 * writes it at the end. A test uses {@link #NONE} so it never touches the player's files.
 */
public interface StashReport {
    StashReport NONE = new StashReport() {
        @Override
        public void phase(StashPhase phase) {
        }

        @Override
        public void undelivered(String reason, int stacks) {
        }

        @Override
        public void protectedBox(BlockPos pos, String name, String reason) {
        }

        @Override
        public void renamed(String name) {
        }

        @Override
        public void finish(String outcome) {
        }
    };

    /** Records the start of a phase and the duration of the previous one. */
    void phase(StashPhase phase);

    /** Records stacks that could not be delivered, with the reason. */
    void undelivered(String reason, int stacks);

    /** Records a shulker box the organizer left shut, with the rule that protected it. */
    void protectedBox(BlockPos pos, String name, String reason);

    /** Records the custom name of a shulker box found in the stash. */
    void renamed(String name);

    /** Writes the report once; later calls are ignored. */
    void finish(String outcome);
}
