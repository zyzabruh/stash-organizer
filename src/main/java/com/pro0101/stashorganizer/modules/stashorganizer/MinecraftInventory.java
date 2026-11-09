package com.pro0101.stashorganizer.modules.stashorganizer;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

import java.util.HashMap;
import java.util.Map;

/** The player inventory of the running client. */
public final class MinecraftInventory implements InventoryTools {
    /** Resolved on use: a test builds these services without loading the client. */
    private MinecraftClient mc() {
        return MeteorClient.mc;
    }

    @Override
    public int freeSlots() {
        int free = 0;
        var inventory = mc().player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).isEmpty()) free++;
        }

        return free;
    }

    @Override
    public int count(Item item) {
        int count = 0;
        var inventory = mc().player.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            count += stack.getCount();
        }

        return count;
    }

    @Override
    public ItemStack stack(int inventoryIndex) {
        return mc().player.getInventory().getStack(inventoryIndex);
    }

    @Override
    public int selectedSlot() {
        return mc().player.getInventory().getSelectedSlot();
    }

    @Override
    public Map<Integer, ItemStack> snapshot() {
        Map<Integer, ItemStack> snapshot = new HashMap<>();
        for (int i = 0; i < 36; i++) snapshot.put(i, mc().player.getInventory().getStack(i).copy());
        return snapshot;
    }

    @Override
    public int findTakenSlot(Item item, Map<Integer, ItemStack> before) {
        if (before == null || item == null) return -1;

        // Compare the whole inventory before and after the click: a slot that merely became
        // non-empty is not necessarily the one that received the stack (a merge in another slot,
        // a hotbar swap or a slot that was emptied by the same click would all fool that test).
        if (countOf(item, before, -1) >= countOf(item, null, -1)) return -1;

        int best = -1;
        int bestGain = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack now = mc().player.getInventory().getStack(i);
            if (now.isEmpty() || now.getItem() != item) continue;

            ItemStack was = before.getOrDefault(i, ItemStack.EMPTY);
            int previous = was.isEmpty() || was.getItem() != item ? 0 : was.getCount();
            int gain = now.getCount() - previous;

            if (gain > bestGain) {
                best = i;
                bestGain = gain;
            }
        }

        return best;
    }

    /**
     * Total count of an item, from the snapshot when {@code before} is given and from the live
     * inventory otherwise.
     */
    private int countOf(Item item, Map<Integer, ItemStack> before, int ignored) {
        int total = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = before == null ? mc().player.getInventory().getStack(i) : before.getOrDefault(i, ItemStack.EMPTY);
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }

        return total;
    }

    @Override
    public boolean ensureEmptyHand() {
        var inventory = mc().player.getInventory();
        if (inventory.getSelectedStack().isEmpty()) return true;

        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isEmpty()) {
                InvUtils.swap(i, false);
                return true;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (!(inventory.getStack(i).getItem() instanceof net.minecraft.item.BlockItem)) {
                InvUtils.swap(i, false);
                return true;
            }
        }

        return false;
    }

    @Override
    public int findShulkerWithFreeSlot(ShulkerProtection protection) {
        int empty = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) continue;
            if (protection.protectsStack(stack) || protection.protectsShulkerContents(stack)) continue;
            if (InventoryTools.contentsOf(stack).size() >= 27) continue;

            if (!InventoryTools.isEmptyShulker(stack)) return i;
            if (empty < 0) empty = i;
        }

        return empty;
    }

    @Override
    public int findShulkerWithAnyRoom(ShulkerProtection protection) {
        int empty = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) continue;
            if (protection.protectsStack(stack) || protection.protectsShulkerContents(stack)) continue;
            if (!InventoryTools.shulkerHasAnyRoom(stack)) continue;

            if (!InventoryTools.isEmptyShulker(stack)) return i;
            if (empty < 0) empty = i;
        }

        return empty;
    }

    @Override
    public int findShulkerWithRoom(Item item, ShulkerProtection protection) {
        int empty = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) continue;
            if (protection.protectsStack(stack) || protection.protectsShulkerContents(stack)) continue;
            if (!InventoryTools.shulkerHasRoomFor(stack, item)) continue;

            if (!InventoryTools.isEmptyShulker(stack)) return i;
            if (empty < 0) empty = i;
        }

        return empty;
    }

    @Override
    public int findNewShulker(Map<Integer, ItemStack> before) {
        if (before == null) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack now = mc().player.getInventory().getStack(i);
            if (now.isEmpty() || !now.isIn(ItemTags.SHULKER_BOXES)) continue;

            ItemStack was = before.getOrDefault(i, ItemStack.EMPTY);

            if (was.isEmpty() || !was.isIn(ItemTags.SHULKER_BOXES)
                || now.getCount() > was.getCount()
                || !ItemStack.areItemsAndComponentsEqual(now, was)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int ensureInHotbarStack(ItemStack target) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack != target && !ItemStack.areItemsAndComponentsEqual(stack, target)) continue;

            if (i <= 8) {
                InvUtils.swap(i, false);
                return i;
            }

            FindItemResult empty = InvUtils.findEmpty();
            int hotbar = empty.isHotbar() ? empty.slot() : mc().player.getInventory().getSelectedSlot();
            InvUtils.move().from(i).toHotbar(hotbar);
            InvUtils.swap(hotbar, false);
            return hotbar;
        }

        return -1;
    }

    @Override
    public boolean hasPickaxe() {
        for (int i = 0; i < 36; i++) {
            if (mc().player.getInventory().getStack(i).isIn(ItemTags.PICKAXES)) return true;
        }

        return false;
    }

    @Override
    public int ensurePickaxe() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isIn(ItemTags.PICKAXES)) return ensureInHotbarStack(stack);
        }

        return -1;
    }

    @Override
    public Item findEmptyShulkerItem(ShulkerProtection protection) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (!InventoryTools.isEmptyShulker(stack)) continue;
            if (protection.protectsStack(stack)) continue;

            return stack.getItem();
        }

        return null;
    }

    @Override
    public int countShulkersWithRoom() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isIn(ItemTags.SHULKER_BOXES) && InventoryTools.shulkerHasAnyRoom(stack)) count++;
        }

        return count;
    }

    @Override
    public int countEmptyShulkers() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            if (InventoryTools.isEmptyShulker(mc().player.getInventory().getStack(i))) count++;
        }

        return count;
    }

    @Override
    public boolean emptyHand() {
        var inventory = mc().player.getInventory();
        if (inventory.getSelectedStack().isEmpty()) return true;

        FindItemResult empty = InvUtils.findEmpty();
        if (!empty.found() || !empty.isHotbar()) return false;

        InvUtils.swap(empty.slot(), false);
        return true;
    }

    @Override
    public boolean holdInHand(int inventoryIndex) {
        if (inventoryIndex < 0) return false;

        ItemStack stack = mc().player.getInventory().getStack(inventoryIndex);
        if (stack.isEmpty()) return false;

        if (inventoryIndex <= 8) {
            InvUtils.swap(inventoryIndex, false);
            return true;
        }

        int hotbar = mc().player.getInventory().getSelectedSlot();
        InvUtils.move().from(inventoryIndex).toHotbar(hotbar);
        InvUtils.swap(hotbar, false);
        return true;
    }

    @Override
    public int ensureInHotbar(Item item) {
        FindItemResult result = InvUtils.find(item);
        if (!result.found()) return -1;

        if (result.isHotbar()) {
            InvUtils.swap(result.slot(), false);
            return result.slot();
        }

        if (result.isOffhand()) return result.slot();

        FindItemResult empty = InvUtils.findEmpty();
        int hotbar = empty.isHotbar() ? empty.slot() : mc().player.getInventory().getSelectedSlot();
        InvUtils.move().from(result.slot()).toHotbar(hotbar);
        InvUtils.swap(hotbar, false);
        return hotbar;
    }

    @Override
    public int findSlot(Item item) {
        return InvUtils.find(item).slot();
    }
}
