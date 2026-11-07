package com.example.addon.modules.stashorganizer;

/** Group slots that still need a container after the main assignment pass. */
public class UnassignedGroup {
    public final StashGroup group;
    public int slots;

    public UnassignedGroup(StashGroup group, int slots) {
        this.group = group;
        this.slots = slots;
    }
}
