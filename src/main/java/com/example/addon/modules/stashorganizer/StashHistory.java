package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/**
 * Remembers which chest held which group in a previous run, so the planner reuses the same chests
 * instead of shuffling the stash on every run. A test uses {@link #NONE}.
 */
public interface StashHistory {
    StashHistory NONE = new StashHistory() {
        @Override
        public boolean isHome(String group, BlockPos pos) {
            return false;
        }

        @Override
        public void remember(String group, BlockPos pos) {
        }

        @Override
        public void save() {
        }
    };

    /** True when this container already held the group in a previous run. */
    boolean isHome(String group, BlockPos pos);

    /** Records that the group is stored in this container. */
    void remember(String group, BlockPos pos);

    void save();
}
