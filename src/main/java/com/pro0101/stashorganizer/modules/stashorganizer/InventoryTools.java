package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Player inventory helpers used by the organizer. The instance methods are the seam the scripts
 * use; they are implemented by {@link MinecraftInventory} on the client and by a fake in tests.
 * The static helpers only look at item stacks, so they carry no client dependency.
 */
public interface InventoryTools {
    int freeSlots();

    int count(Item item);

    ItemStack stack(int inventoryIndex);

    int selectedSlot();

    Map<Integer, ItemStack> snapshot();

    /** Finds the inventory slot that received a stack after a shift-click. */
    int findTakenSlot(Item item, Map<Integer, ItemStack> before);

    /** Makes sure the player holds no block, so right-clicking a chest opens it. */
    boolean ensureEmptyHand();

    /** A shulker with at least one free slot, without knowing which group will go in it. */
    int findShulkerWithFreeSlot(ShulkerProtection protection);

    /** A partially filled shulker first, then an empty one, without knowing the group. */
    int findShulkerWithAnyRoom(ShulkerProtection protection);

    int findShulkerWithRoom(Item item, ShulkerProtection protection);

    /** The slot of a shulker that appeared or changed since the snapshot, or -1. */
    int findNewShulker(Map<Integer, ItemStack> before);

    /** Moves an exact stack (and not merely another shulker of the same colour) to the hotbar. */
    int ensureInHotbarStack(ItemStack target);

    boolean hasPickaxe();

    /** Puts a pickaxe in the hand so a filled shulker can be mined without losing it. */
    int ensurePickaxe();

    /** Item of the first empty shulker box in the inventory, or null. */
    Item findEmptyShulkerItem(ShulkerProtection protection);

    int countShulkersWithRoom();

    int countEmptyShulkers();

    /** Selects a truly empty hand (needed to rotate an item frame). */
    boolean emptyHand();

    /** Moves an inventory stack into the selected hotbar slot. */
    boolean holdInHand(int inventoryIndex);

    /** Puts the item in the hotbar and selects it. */
    int ensureInHotbar(Item item);

    /** Inventory slot of the item, or -1. */
    int findSlot(Item item);

    /** True for an empty shulker box (no stored stacks), used as overflow storage. */
    static boolean isEmptyShulker(ItemStack stack) {
        if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) return false;

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        return container == null || container.streamNonEmpty().findAny().isEmpty();
    }

    /** Non-empty stacks stored inside a shulker box item. */
    static List<ItemStack> contentsOf(ItemStack shulker) {
        List<ItemStack> contents = new ArrayList<>();

        if (!shulker.isIn(ItemTags.SHULKER_BOXES)) return contents;

        ContainerComponent container = shulker.get(DataComponentTypes.CONTAINER);
        if (container != null) container.streamNonEmpty().forEach(stack -> contents.add(stack.copy()));

        return contents;
    }

    /** True when the shulker item has an empty slot, or a matching partial stack to top up. */
    static boolean shulkerHasRoomFor(ItemStack shulker, Item item) {
        List<ItemStack> contents = contentsOf(shulker);
        if (contents.size() < 27) return true;

        for (ItemStack stack : contents) {
            if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) return true;
        }

        return false;
    }

    /** True when at least one slot is empty or one existing stack can still be topped up. */
    static boolean shulkerHasAnyRoom(ItemStack shulker) {
        List<ItemStack> contents = contentsOf(shulker);
        if (contents.size() < 27) return true;

        for (ItemStack stack : contents) {
            if (stack.getCount() < stack.getMaxCount()) return true;
        }

        return false;
    }
}
