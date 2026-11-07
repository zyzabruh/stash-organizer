package com.example.addon.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The game-facing services of a session, replaced by fakes. They answer without a client and
 * without touching the item registries until an item is actually needed, so a component script can
 * run as plain Java. Tests that build real item stacks boot the registries first with
 * {@link TestWorld#bootstrap()}.
 */
final class FakeServices {
    private FakeServices() {
    }

    static final class Host implements SessionHost {
        final List<String> messages = new ArrayList<>();
        boolean toggled;
        boolean packsShulkers;
        boolean unpacksShulkers;
        boolean labelsChests;
        boolean dumpsInventory;
        boolean dryRun;
        boolean keepsRenamed;
        Timings.Latency latency = Timings.Latency.Normal;

        @Override
        public int radius() {
            return 8;
        }

        @Override
        public ItemGrouping.Mode mode() {
            return ItemGrouping.Mode.Family;
        }

        @Override
        public boolean createsKits() {
            return false;
        }

        @Override
        public boolean unpacksShulkers() {
            return unpacksShulkers;
        }

        @Override
        public List<String> protectNames() {
            return List.of();
        }

        @Override
        public boolean protectSingleItem() {
            return false;
        }

        @Override
        public boolean protectGear() {
            return false;
        }

        @Override
        public List<String> protectItems() {
            return List.of();
        }

        @Override
        public boolean labelsChests() {
            return labelsChests;
        }

        @Override
        public int batch() {
            return 6;
        }

        @Override
        public int actionDelay() {
            return 0;
        }

        @Override
        public int timeoutTicks() {
            return 400;
        }

        @Override
        public boolean isDryRun() {
            return dryRun;
        }

        @Override
        public boolean dumpsInventory() {
            return dumpsInventory;
        }

        @Override
        public boolean allowOverflowShulkers() {
            return false;
        }

        @Override
        public boolean packsShulkers() {
            return packsShulkers;
        }

        @Override
        public Timings.Latency latency() {
            return latency;
        }

        @Override
        public boolean keepsRenamed() {
            return keepsRenamed;
        }

        @Override
        public void info(String message, Object... args) {
            messages.add("info: " + message);
        }

        @Override
        public void warning(String message, Object... args) {
            messages.add("warning: " + message);
        }

        @Override
        public void error(String message, Object... args) {
            messages.add("error: " + message);
        }

        @Override
        public void toggle() {
            toggled = true;
        }
    }

    static final class Mover implements StashMover {
        final List<BlockPos> moves = new ArrayList<>();
        boolean stopped;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void moveTo(BlockPos pos) {
            moves.add(pos);
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public boolean isPathing() {
            return false;
        }
    }

    static final class Game implements com.example.addon.modules.stashorganizer.Game {
        final List<BlockPos> containers = new ArrayList<>();
        final List<BlockPos> shulkers = new ArrayList<>();
        final List<BlockPos> broken = new ArrayList<>();
        final Map<BlockPos, Block> blocks = new HashMap<>();

        @Override
        public Block blockAt(BlockPos pos) {
            return blocks.get(pos);
        }

        @Override
        public boolean isContainerAt(BlockPos pos) {
            return containers.contains(pos);
        }

        @Override
        public boolean isShulkerAt(BlockPos pos) {
            return shulkers.contains(pos);
        }

        @Override
        public boolean isAir(BlockPos pos) {
            return true;
        }

        @Override
        public Iterable<BlockPos> around(BlockPos center, int radius) {
            return List.of();
        }

        @Override
        public boolean chunkLoaded(BlockPos pos) {
            return true;
        }

        @Override
        public BlockPos playerFeet() {
            return BlockPos.ORIGIN;
        }

        @Override
        public Vec3d playerPos() {
            return Vec3d.ZERO;
        }

        @Override
        public boolean playerIntersects(BlockPos pos) {
            return false;
        }

        @Override
        public double squaredDistanceTo(BlockPos pos) {
            return 0;
        }

        @Override
        public List<ItemFrameEntity> itemFrames() {
            return List.of();
        }

        @Override
        public void lookAt(BlockPos pos, int priority) {
        }

        @Override
        public void lookAt(Vec3d point, int priority) {
        }

        @Override
        public void breakBlock(BlockPos pos) {
            broken.add(pos);
            shulkers.remove(pos);
        }

        @Override
        public boolean place(BlockPos pos, ItemStack stack) {
            if (stack.isEmpty()) return false;

            Block block = stack.getItem() == Items.CHEST ? Blocks.CHEST
                : stack.getItem() == Items.TRAPPED_CHEST ? Blocks.TRAPPED_CHEST
                : null;

            if (block == null) return false;

            blocks.put(pos, block);
            containers.add(pos);
            return true;
        }

        @Override
        public void useBlock(BlockPos pos, Direction face, Vec3d hit) {
        }

        @Override
        public void useEntity(Entity entity) {
        }
    }

    static final class Spots implements BlockSpots {
        final Set<BlockPos> allowed = new HashSet<>();

        @Override
        public boolean canPlaceAt(BlockPos pos) {
            return allowed.contains(pos);
        }

        @Override
        public boolean hasFreeHorizontalFace(BlockPos pos) {
            return true;
        }

        @Override
        public boolean touchesContainer(BlockPos pos) {
            return false;
        }
    }

    static final class Walker implements Navigator {
        final List<BlockPos> walks = new ArrayList<>();

        @Override
        public boolean walkTo(BlockPos pos) {
            walks.add(pos);
            return true;
        }

        @Override
        public Result walkToDetailed(BlockPos pos) {
            walks.add(pos);
            return Result.ARRIVED;
        }
    }

    /**
     * The player inventory. Slots start as {@code null} so a test that never touches items does not
     * need the item registries; {@link #stack(int)} answers an empty stack once they are booted.
     */
    static final class Inventory implements InventoryTools {
        final ItemStack[] slots = new ItemStack[36];
        int pickaxeSlot = -1;

        @Override
        public int freeSlots() {
            int free = 0;
            for (ItemStack stack : slots) if (stack == null || stack.isEmpty()) free++;
            return free;
        }

        @Override
        public int count(Item item) {
            int count = 0;

            for (ItemStack stack : slots) {
                if (stack != null && !stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
            }

            return count;
        }

        @Override
        public ItemStack stack(int inventoryIndex) {
            if (inventoryIndex < 0 || inventoryIndex >= slots.length) return ItemStack.EMPTY;
            return slots[inventoryIndex] == null ? ItemStack.EMPTY : slots[inventoryIndex];
        }

        @Override
        public int selectedSlot() {
            return 0;
        }

        @Override
        public Map<Integer, ItemStack> snapshot() {
            Map<Integer, ItemStack> snapshot = new HashMap<>();

            for (int i = 0; i < slots.length; i++) {
                snapshot.put(i, slots[i] == null ? ItemStack.EMPTY : slots[i].copy());
            }

            return snapshot;
        }

        @Override
        public int findTakenSlot(Item item, Map<Integer, ItemStack> before) {
            if (before == null || item == null) return -1;
            if (total(item, before) >= total(item, null)) return -1;

            int best = -1;
            int bestGain = 0;

            for (int i = 0; i < slots.length; i++) {
                ItemStack now = slots[i];
                if (now == null || now.isEmpty() || now.getItem() != item) continue;

                ItemStack was = before.getOrDefault(i, ItemStack.EMPTY);
                int previous = !was.isEmpty() && was.getItem() == item ? was.getCount() : 0;
                int gain = now.getCount() - previous;

                if (gain > bestGain) {
                    best = i;
                    bestGain = gain;
                }
            }

            return best;
        }

        private int total(Item item, Map<Integer, ItemStack> snapshot) {
            int total = 0;

            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = snapshot == null ? slots[i] : snapshot.getOrDefault(i, ItemStack.EMPTY);
                if (stack != null && !stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
            }

            return total;
        }

        @Override
        public boolean ensureEmptyHand() {
            return true;
        }

        @Override
        public int findShulkerWithFreeSlot(ShulkerProtection protection) {
            return -1;
        }

        @Override
        public int findShulkerWithAnyRoom(ShulkerProtection protection) {
            return -1;
        }

        @Override
        public int findShulkerWithRoom(Item item, ShulkerProtection protection) {
            return -1;
        }

        @Override
        public int findNewShulker(Map<Integer, ItemStack> before) {
            return -1;
        }

        @Override
        public int ensureInHotbarStack(ItemStack target) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) continue;
                if (ItemStack.areItemsAndComponentsEqual(slots[i], target)) return i;
            }

            return pickaxeSlot;
        }

        @Override
        public boolean hasPickaxe() {
            return pickaxeSlot >= 0;
        }

        @Override
        public int ensurePickaxe() {
            return pickaxeSlot;
        }

        @Override
        public Item findEmptyShulkerItem(ShulkerProtection protection) {
            return null;
        }

        @Override
        public int countShulkersWithRoom() {
            return 0;
        }

        @Override
        public int countEmptyShulkers() {
            return 0;
        }

        @Override
        public boolean emptyHand() {
            return true;
        }

        @Override
        public boolean holdInHand(int inventoryIndex) {
            return inventoryIndex >= 0 && inventoryIndex < slots.length;
        }

        @Override
        public int ensureInHotbar(Item item) {
            return findSlot(item);
        }

        @Override
        public int findSlot(Item item) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] != null && !slots[i].isEmpty() && slots[i].getItem() == item) return i;
            }

            return -1;
        }
    }

    static final class Access implements ContainerAccess {
        private static final int SIZE = 27;

        final List<BlockPos> opened = new ArrayList<>();
        final Map<BlockPos, List<ItemStack>> screens = new LinkedHashMap<>();
        List<StashContainer> containers = List.of();
        FakeServices.Inventory inventory;
        int reads;
        int resets;
        boolean closed;

        private BlockPos openedPos;

        @Override
        public boolean isOpen() {
            return openedPos != null;
        }

        @Override
        public void close() {
            closed = true;
            openedPos = null;
        }

        @Override
        public int containerSlots() {
            return SIZE;
        }

        @Override
        public ItemStack liveStack(int slot) {
            List<ItemStack> screen = screen();
            if (screen == null || slot < 0 || slot >= screen.size()) return ItemStack.EMPTY;

            return screen.get(slot);
        }

        @Override
        public List<ItemStack> liveContents() {
            List<ItemStack> screen = screen();
            return screen == null ? List.of() : new ArrayList<>(screen);
        }

        @Override
        public int liveSlotId(int slot) {
            return slot;
        }

        @Override
        public int open(BlockPos pos) {
            opened.add(pos);
            openedPos = pos;
            screens.computeIfAbsent(pos, key -> new ArrayList<>());
            return 1;
        }

        @Override
        public void resetOpen() {
            resets++;
        }

        @Override
        public void read(BlockPos pos, boolean recordShulkers) {
            reads++;

            StashContainer container = at(pos);

            if (container == null) {
                container = new StashContainer(pos, null);
                containers.add(container);
            }

            List<ItemStack> screen = screens.get(pos);

            // The cached contents only follow a screen that was actually filled.
            if (screen != null && !screen.isEmpty()) {
                container.size = screen.size();
                container.contents.clear();
                for (ItemStack stack : screen) container.contents.add(stack.copy());
                container.ensureSize();
            }

            container.scanned = true;
        }

        @Override
        public StashContainer at(BlockPos pos) {
            for (StashContainer container : containers) {
                if (container.pos.equals(pos)) return container;
                if (container.partner != null && container.partner.equals(pos)) return container;
            }

            return null;
        }

        /** Moves a stack between the opened screen and the inventory, the way a shift-click does. */
        @Override
        public void click(int slotId, int button, SlotActionType type) {
            if (type != SlotActionType.QUICK_MOVE || inventory == null) return;

            List<ItemStack> screen = screen();
            if (screen == null) return;

            if (slotId >= 0 && slotId < screen.size()) takeFromScreen(screen, slotId);
            else depositIntoScreen(screen, inventoryIndex(slotId));
        }

        private void takeFromScreen(List<ItemStack> screen, int slot) {
            ItemStack stack = screen.get(slot);
            if (stack.isEmpty()) return;

            int target = firstFreeSlot();
            if (target < 0) return;

            inventory.slots[target] = stack.copy();
            screen.set(slot, ItemStack.EMPTY);
        }

        private void depositIntoScreen(List<ItemStack> screen, int inventoryIndex) {
            if (inventoryIndex < 0 || inventoryIndex >= inventory.slots.length) return;
            if (inventory.slots[inventoryIndex] == null) return;

            ItemStack stack = inventory.slots[inventoryIndex];
            if (stack.isEmpty()) return;

            int target = roomIn(screen, stack);
            if (target < 0) return;

            ItemStack existing = screen.get(target);

            if (existing.isEmpty()) screen.set(target, stack.copy());
            else screen.set(target, new ItemStack(stack.getItem(), existing.getCount() + stack.getCount()));

            inventory.slots[inventoryIndex] = ItemStack.EMPTY;
        }

        private int roomIn(List<ItemStack> screen, ItemStack stack) {
            for (int i = 0; i < screen.size(); i++) if (screen.get(i).isEmpty()) return i;

            for (int i = 0; i < screen.size(); i++) {
                ItemStack slot = screen.get(i);
                if (ItemStack.areItemsAndComponentsEqual(slot, stack) && slot.getCount() < slot.getMaxCount()) return i;
            }

            return -1;
        }

        private int firstFreeSlot() {
            for (int i = 0; i < inventory.slots.length; i++) {
                if (inventory.slots[i] == null || inventory.slots[i].isEmpty()) return i;
            }

            return -1;
        }

        /** The opened screen, grown to a full container when an item is actually stored in it. */
        private List<ItemStack> screen() {
            if (openedPos == null) return null;

            List<ItemStack> screen = screens.get(openedPos);
            if (screen == null) return null;

            while (screen.size() < SIZE) screen.add(ItemStack.EMPTY);

            return screen;
        }

        private int inventoryIndex(int slotId) {
            if (slotId < containerSlots()) return -1;

            int offset = slotId - containerSlots();
            return offset < 27 ? 9 + offset : offset - 27;
        }

        @Override
        public int playerSlotId(int inventoryIndex, int containerSlots) {
            if (inventoryIndex >= 9 && inventoryIndex <= 35) return containerSlots + (inventoryIndex - 9);
            if (inventoryIndex >= 0 && inventoryIndex <= 8) return containerSlots + 27 + inventoryIndex;
            return -1;
        }

        static List<ItemStack> emptyScreen() {
            List<ItemStack> screen = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) screen.add(ItemStack.EMPTY);
            return screen;
        }
    }
}
