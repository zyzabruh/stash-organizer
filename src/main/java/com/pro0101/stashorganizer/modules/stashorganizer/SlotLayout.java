package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Final layout of one chest: the stacks of an item become contiguous, the items follow the order of
 * the chest's planned groups and the empty slots end up at the bottom. The moves are expressed as
 * slot pairs to swap, so tidying a full chest costs a handful of clicks instead of a full unload.
 * <p>
 * Nothing here touches the game: the permutation is computed from the contents that are about to be
 * moved, so it can be checked on plain values (see {@code SlotLayoutTest}).
 */
public final class SlotLayout {
    /** Two slots to swap, with {@code from < to}, in an order that can be applied one by one. */
    public record Swap(int from, int to) {
    }

    private SlotLayout() {
    }

    /** What a container looks like once tidied: the sorted stacks first, the empty slots last. */
    public static List<ItemStack> target(List<ItemStack> contents, Comparator<ItemStack> order) {
        List<ItemStack> tidied = new ArrayList<>(contents.size());

        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) tidied.add(stack);
        }

        tidied.sort(order);
        while (tidied.size() < contents.size()) tidied.add(ItemStack.EMPTY);

        return tidied;
    }

    /** The swaps that turn the contents into their tidied order. */
    public static List<Swap> swaps(List<ItemStack> contents, Comparator<ItemStack> order) {
        return permutation(contents, target(contents, order), SlotLayout::interchangeable);
    }

    /**
     * Two stacks of one item are interchangeable for the layout, even when their counts or their
     * components differ: clicking one onto the other merges or swaps them instead of moving a stack
     * from a slot to another, so the layout must never ask for that swap. Swapping the stacks of
     * two different items is what the cursor does cleanly.
     */
    static boolean interchangeable(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();

        return first.getItem() == second.getItem();
    }

    /**
     * The swaps that turn {@code current} into {@code target}, at most one per slot, always between
     * two elements that are not interchangeable.
     * <p>
     * Generic so the ordering can be tested on plain values; {@code target} must be a permutation
     * of {@code current} under {@code same}.
     */
    static <T> List<Swap> permutation(List<T> current, List<T> target, BiPredicate<T, T> same) {
        List<T> work = new ArrayList<>(current);
        List<Swap> swaps = new ArrayList<>();

        for (int slot = 0; slot < work.size(); slot++) {
            if (same.test(work.get(slot), target.get(slot))) continue;

            // The element that belongs in this slot is still further down: the slots before this
            // one are final, so the element can only come from the part that is left.
            int from = -1;
            for (int other = slot + 1; other < work.size(); other++) {
                if (same.test(work.get(other), target.get(slot))) {
                    from = other;
                    break;
                }
            }

            // Not a permutation of the same stacks: leave the slot alone instead of guessing.
            if (from < 0) continue;

            Collections.swap(work, slot, from);
            swaps.add(new Swap(slot, from));
        }

        return swaps;
    }
}
