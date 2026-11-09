package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Item groups allowed in this container after planning, in the order the plan gave them. The
     * order is kept (and not a plain hash order) so the tidying pass can lay the chest out the same
     * way from one run to the next, with the main group of the chest first.
     */
    public final Set<String> groups = new LinkedHashSet<>();
    /**
     * Group named by an item frame already standing on this container, or null without a label.
     * The planner gives that group to this chest first, so a stash labelled by hand keeps its
     * layout instead of having every item moved to a fresh chest.
     */
    public String frameGroup;
    /** Number of slots reserved for the planned groups. */
    public int reserved;

    /** Stacks this container gave away, because it held a group that belongs elsewhere. */
    public int movedOut;
    /** Stacks another container delivered here. */
    public int movedIn;
    /** Stacks taken out of here to make room for the group this chest is supposed to hold. */
    public int evicted;
    /** Misplaced stacks still in this container when the sort gave up. */
    public int left;
    /**
     * Why those stacks stayed, by reason, with the number of stacks each reason covers. The sorter
     * fills it when it gives up on a chest, so the report can explain every incomplete chest
     * instead of only saying that something was left.
     */
    public final Map<String, Integer> leftReasons = new LinkedHashMap<>();

    /** Records why stacks were left in this chest. */
    public void explain(String reason, int stacks) {
        if (reason == null || stacks <= 0) return;

        leftReasons.merge(reason, stacks, Integer::sum);
    }

    /** True when the sorter already said why it left stacks here. */
    public boolean explained() {
        return !leftReasons.isEmpty();
    }

    public StashContainer(BlockPos pos, Block block) {
        this.pos = pos;
        this.block = block;
    }

    public void ensureSize() {
        while (contents.size() < size) contents.add(ItemStack.EMPTY);
        while (contents.size() > size) contents.remove(contents.size() - 1);
    }

    /**
     * True when the game lets this stack go into this container. Vanilla refuses to put a shulker box
     * inside another shulker box, so a box must never be delivered to an overflow shulker: it has to
     * find a chest instead.
     */
    public boolean accepts(ItemStack stack) {
        return !(ContainerTypes.isShulker(block) && stack.isIn(ItemTags.SHULKER_BOXES));
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
