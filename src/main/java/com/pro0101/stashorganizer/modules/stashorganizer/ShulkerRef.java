package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/** A shulker box stack found while scanning, identified by its container and slot. */
public class ShulkerRef {
    public final BlockPos pos;
    public final int slot;
    public boolean done;

    public ShulkerRef(BlockPos pos, int slot) {
        this.pos = pos;
        this.slot = slot;
    }
}
