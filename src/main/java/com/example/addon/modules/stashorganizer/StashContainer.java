package com.example.addon.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A storage block found by the organizer.
 * <p>
 * The {@link #contents} list is a cache of the last known inventory of the container. It is
 * refreshed every time the container is opened and updated while it is open, so the planner and
 * the sorter can make decisions without re-opening a container for every single item.
 */
public class StashContainer {
    public final BlockPos pos;
    public final Block block;

    /** Other half of a double chest, if any. */
    public BlockPos partner;
    /** Number of slots of the container (27 for a chest/barrel/shulker, 54 for a double chest). */
    public int size = 27;
    /** True once the container has been opened at least once. */
    public boolean scanned;
    /** True when the container could not be opened (blocked chest, chunk unloaded, ...). */
    public boolean failed;
    /** True for an overflow shulker that must be filled, broken and stored again. */
    public boolean packing;

    /** Cached contents, always filled up to {@link #size} with {@link ItemStack#EMPTY}. */
    public final List<ItemStack> contents = new ArrayList<>();

    /** Item groups allowed in this container after planning. */
    public final Set<String> groups = new HashSet<>();
    /** Number of slots reserved for the planned groups. */
    public int reserved;

    public StashContainer(BlockPos pos, Block block) {
        this.pos = pos;
        this.block = block;
    }

    public void ensureSize() {
        while (contents.size() < size) contents.add(ItemStack.EMPTY);
        while (contents.size() > size) contents.remove(contents.size() - 1);
    }

    /** True when the whole stack fits (a partial matching stack alone is not enough). */
    public boolean hasRoomFor(ItemStack stack) {
        ensureSize();
        int room = 0;

        for (ItemStack slot : contents) {
            if (slot.isEmpty()) room += stack.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                room += slot.getMaxCount() - slot.getCount();
            }

            if (room >= stack.getCount()) return true;
        }

        return false;
    }

    /** Item that best represents what is stored in this container, or null if it is empty. */
    public Item representative() {
        Item best = null;
        int bestCount = 0;

        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;

            int count = 0;
            for (ItemStack other : contents) {
                if (!other.isEmpty() && other.getItem() == stack.getItem()) count += other.getCount();
            }

            if (count > bestCount) {
                best = stack.getItem();
                bestCount = count;
            }
        }

        return best;
    }

    public boolean contains(Item item) {
        for (ItemStack stack : contents) {
            if (!stack.isEmpty() && stack.getItem() == item) return true;
        }
        return false;
    }

    public void clearCache() {
        contents.clear();
        ensureSize();
    }
}
