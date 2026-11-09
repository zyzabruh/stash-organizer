package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** An item group and the number of chest slots it needs once its stacks are merged. */
public class StashGroup {
    public final String key;
    public final Map<Item, Integer> counts = new LinkedHashMap<>();

    public int slots;
    /** Kind of the group, covering most of its items; set by {@link #computeKind()}. */
    public String kind;
    /** Items of the group per kind, so one stray stack cannot decide the kind of the chest. */
    private final Map<String, Integer> kinds = new LinkedHashMap<>();

    public StashGroup(String key) {
        this.key = key;
    }

    public void add(ItemStack stack) {
        counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        kinds.merge(ItemGrouping.kind(stack), stack.getCount(), Integer::sum);
    }

    /**
     * Picks the kind that holds most of the group. A group is normally one kind of item already;
     * this matters for a group gathering several items, where the total of each kind decides.
     */
    public void computeKind() {
        String best = "misc";
        int bestCount = 0;

        for (Map.Entry<String, Integer> entry : kinds.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        kind = best;
    }

    public void computeSlots() {
        slots = 0;

        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            int max = Math.max(1, new ItemStack(entry.getKey()).getMaxCount());
            slots += (entry.getValue() + max - 1) / max;
        }
    }
}
