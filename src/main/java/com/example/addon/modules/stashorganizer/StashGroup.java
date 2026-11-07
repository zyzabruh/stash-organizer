package com.example.addon.modules.stashorganizer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** An item group and the number of chest slots it needs once its stacks are merged. */
public class StashGroup {
    public final String key;
    public final Map<Item, Integer> counts = new LinkedHashMap<>();

    public int slots;

    public StashGroup(String key) {
        this.key = key;
    }

    public void add(ItemStack stack) {
        counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }

    public void computeSlots() {
        slots = 0;

        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            int max = Math.max(1, new ItemStack(entry.getKey()).getMaxCount());
            slots += (entry.getValue() + max - 1) / max;
        }
    }
}
