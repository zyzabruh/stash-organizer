package com.example.addon.modules.stashorganizer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Moves the misplaced stacks, one small batch at a time: take a few stacks from a source chest,
 * walk to their destination chests and deposit them. The player inventory is the only buffer.
 * <p>
 * A batch stays on one group while the source still holds it, and every walk goes to the
 * container that takes the most of the buffer, so one trip delivers several stacks.
 */
public final class ItemSorter {
    private static final int SELECT = 0;
    private static final int WALK = 1;
    private static final int OPEN = 2;
    private static final int READ = 3;
    private static final int TAKE = 4;
    private static final int CLOSE = 5;
    private static final int AFTER = 6;

    private static final int DEST = 0;
    private static final int D_WALK = 1;
    private static final int D_OPEN = 2;
    private static final int D_DEPOSIT = 3;
    private static final int D_CLOSE = 4;

    private final StashSession session;
    private final StepScript sortScript = new StepScript(
        this::selectSource, this::walkToSource, this::openSource, this::readSource,
        this::takeStep, this::closeSource, this::afterSource);
    private final StepScript deliverScript = new StepScript(
        this::chooseDestination, this::walkToDestination, this::openDestination,
        this::depositStep, this::closeDestination);

    private StashContainer sortSource;
    private StashContainer sortDest;

    private int takeWait;
    private int failedTakes;
    private int takeSlotId = -1;
    private int takeIndex = -1;
    private Item takeItem;
    private Map<Integer, ItemStack> takeSnapshot;
    private final Set<Integer> skipSlots = new HashSet<>();
    private boolean sourceExhausted;
    /** Group the current batch is clustering on, so its stacks share one destination. */
    private String batchGroup;

    private boolean delivering;
    private boolean deliveryDone;
    private int depositWait;
    private int depositIndex = -1;
    private Item depositItem;
    private String depositGroup;
    private int failedDeposits;

    public ItemSorter(StashSession session) {
        this.session = session;
    }

    public boolean isDelivering() {
        return delivering;
    }

    /** The chest the sort is taking items from, or null between two sources. */
    public BlockPos currentSource() {
        return sortSource == null ? null : sortSource.pos;
    }

    /** The chest the current delivery is walking to, or null between two destinations. */
    public BlockPos currentDestination() {
        return sortDest == null ? null : sortDest.pos;
    }

    /** Starts delivering whatever the inventory buffer currently holds. */
    public void startDelivery() {
        deliverScript.restart();
        delivering = !session.carried.isEmpty();
    }

    /**
     * Runs one delivery tick.
     *
     * @return true once the buffer is empty
     */
    public boolean deliverTick() {
        if (!delivering) return true;

        if (deliver()) {
            delivering = false;
            deliverScript.restart();
            return true;
        }

        return false;
    }

    public void tick() {
        if (delivering) {
            if (deliverTick()) finishBatch();
            return;
        }

        sortScript.run();
    }

    private StepResult selectSource() {
        if (session.inventory.freeSlots() <= 2) {
            session.warning("Inventory is too full to keep sorting; the remaining items stay in place.");
            finishSorting();
            return StepResult.RUNNING;
        }

        sortSource = null;

        for (StashContainer container : session.containers) {
            if (container.failed || !container.scanned) continue;
            if (session.exhaustedSources.contains(container.pos)) continue;

            for (ItemStack stack : container.contents) {
                if (stack.isEmpty()) continue;
                if (isBoxToUnpack(stack)) continue;

                if (!container.groups.contains(session.groupOf(stack))) {
                    sortSource = container;
                    break;
                }
            }

            if (sortSource != null) break;
        }

        if (sortSource == null) {
            finishSorting();
            return StepResult.RUNNING;
        }

        skipSlots.clear();
        failedTakes = 0;
        sourceExhausted = false;
        return StepResult.jumpTo(WALK);
    }

    private StepResult walkToSource() {
        Navigator.Result navigation = session.navigator.walkToDetailed(sortSource.pos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            return StepResult.jumpTo(OPEN);
        }

        if (navigation == Navigator.Result.FAILED) {
            sortSource.failed = true;
            session.exhaustedSources.add(sortSource.pos);
            sortSource = null;
            return StepResult.jumpTo(SELECT);
        }

        return StepResult.RUNNING;
    }

    private StepResult openSource() {
        int result = session.access.open(sortSource.pos);

        if (result == 1) {
            session.act();
            return StepResult.jumpTo(READ);
        }

        if (result == -1) {
            sortSource.failed = true;
            session.exhaustedSources.add(sortSource.pos);
            sortSource = null;
            return StepResult.jumpTo(SELECT);
        }

        return StepResult.RUNNING;
    }

    private StepResult readSource() {
        session.access.read(sortSource.pos, false);
        session.act();
        return StepResult.jumpTo(TAKE);
    }

    private StepResult takeStep() {
        if (takeWait > 0) {
            takeWait--;
            if (takeWait == 0) finishTake();
            return StepResult.RUNNING;
        }

        if (!session.access.isOpen()) return StepResult.jumpTo(SELECT);

        if (sourceExhausted) return StepResult.jumpTo(CLOSE);

        if (session.carried.size() >= session.batch() || session.inventory.freeSlots() <= 2) {
            return StepResult.jumpTo(CLOSE);
        }

        int containerSlots = session.access.containerSlots();
        int chosen = -1;

        // Prefer the group the batch already holds: one destination then takes the whole batch,
        // instead of walking to a different chest for every group mixed into the buffer.
        for (int pass = 0; pass < 2 && chosen < 0; pass++) {
            for (int i = 0; i < containerSlots; i++) {
                if (skipSlots.contains(i)) continue;

                ItemStack stack = session.access.liveStack(i);
                if (stack.isEmpty()) continue;
                if (isBoxToUnpack(stack)) continue;

                String group = session.groupOf(stack);
                if (sortSource.groups.contains(group)) continue;
                if (pass == 0 && batchGroup != null && !group.equals(batchGroup)) continue;

                chosen = i;
                break;
            }
        }

        if (chosen < 0) {
            sourceExhausted = true;
            return StepResult.jumpTo(CLOSE);
        }

        ItemStack stack = session.access.liveStack(chosen);

        // Take it even when no destination exists yet: the delivery places a chest or an
        // overflow shulker on demand, and drops the stack back in the inventory if it cannot.
        takeItem = stack.getItem();
        takeSlotId = session.access.liveSlotId(chosen);
        takeIndex = chosen;
        takeSnapshot = session.inventory.snapshot();

        if (batchGroup == null) batchGroup = session.groupOf(stack);

        session.access.click(takeSlotId, 0, SlotActionType.QUICK_MOVE);
        takeWait = session.timings.slotClick();
        session.act();
        return StepResult.RUNNING;
    }

    private StepResult closeSource() {
        session.access.close();
        session.act();
        return StepResult.jumpTo(AFTER);
    }

    private StepResult afterSource() {
        if (session.carried.isEmpty()) {
            finishBatch();
            return StepResult.RUNNING;
        }

        startDelivery();
        return StepResult.RUNNING;
    }

    private void finishSorting() {
        int remainingMisplaced = 0;

        for (StashContainer container : session.containers) {
            if (container.failed || !container.scanned) continue;

            for (ItemStack stack : container.contents) {
                if (stack.isEmpty()) continue;
                if (isBoxToUnpack(stack)) continue;
                if (!container.groups.contains(session.groupOf(stack))) remainingMisplaced++;
            }
        }

        if (remainingMisplaced > 0) {
            session.report.undelivered("no destination or no room", remainingMisplaced);
            session.warning("%d stack(s) could not be moved (no destination or no room).", remainingMisplaced);
        } else {
            session.info("Items sorted, %d moves made.", session.totalMoves);
        }

        session.nextPhase(StashPhase.Collect);
    }

    private void finishTake() {
        int index = session.inventory.findTakenSlot(takeItem, takeSnapshot);

        if (index >= 0) {
            // The group is decided here, while the stack is still in the inventory: a shulker box
            // belongs with the item it holds.
            session.carried.put(index, new Carried(takeItem, session.groupOf(session.inventory.stack(index))));
            session.totalMoves++;
            failedTakes = 0;
        } else if (takeIndex >= 0) {
            skipSlots.add(takeIndex);

            // Do not loop on a source the server refuses to let us take from.
            if (++failedTakes >= 15) {
                skipSlots.clear();
                session.exhaustedSources.add(sortSource.pos);
                sourceExhausted = true;
                session.warning("The server refuses item moves from %s; its items stay in place.",
                    sortSource.pos.toShortString());
            }
        }

        if (sortSource != null) session.access.read(sortSource.pos, false);

        takeItem = null;
        takeSnapshot = null;
        takeSlotId = -1;
        takeIndex = -1;
    }

    /** Delivery: picks a destination, walks there and deposits the buffer, one stack per click. */
    private boolean deliver() {
        if (session.carried.isEmpty()) return true;

        deliveryDone = false;
        deliverScript.run();
        return deliveryDone;
    }

    private StepResult chooseDestination() {
        sortDest = bestDestination();

        if (sortDest == null) {
            Map.Entry<Integer, Carried> firstEntry = session.carried.entrySet().iterator().next();
            ItemStack first = carriedStack(firstEntry);
            String group = firstEntry.getValue().group();
            // A shulker box that cannot join its item's chest belongs with the other boxes.
            String own = ownGroup(first);
            String fallback = first.isIn(ItemTags.SHULKER_BOXES) && !group.equals(own) ? own : null;

            int placed = session.placer.placeOneChest(group);

            if (placed == -1) {
                // No chest or no free spot left: pack the new group into existing room.
                sortDest = packNewGroup(group, first);
                if (sortDest == null && fallback != null) sortDest = packNewGroup(fallback, first);

                if (sortDest == null) {
                    dropUndeliverable("no destination for " + ItemGrouping.displayName(group));
                    return done();
                }
            } else {
                if (placed == 0) return StepResult.RUNNING;

                sortDest = session.placer.lastPlaced();
            }

            session.act();
        }

        session.access.resetOpen();
        return StepResult.jumpTo(D_WALK);
    }

    private StepResult walkToDestination() {
        Navigator.Result navigation = session.navigator.walkToDetailed(sortDest.pos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.act();
            return StepResult.jumpTo(D_OPEN);
        }

        if (navigation == Navigator.Result.FAILED) {
            sortDest.failed = true;
            return StepResult.jumpTo(DEST);
        }

        return StepResult.RUNNING;
    }

    private StepResult openDestination() {
        int result = session.access.open(sortDest.pos);

        if (result == 1) {
            session.access.read(sortDest.pos, false);
            session.act();
            return StepResult.jumpTo(D_DEPOSIT);
        }

        if (result == -1) {
            sortDest.failed = true;
            return StepResult.jumpTo(DEST);
        }

        return StepResult.RUNNING;
    }

    private StepResult depositStep() {
        if (depositWait > 0) {
            depositWait--;
            if (depositWait == 0) verifyDeposit();
            return StepResult.RUNNING;
        }

        if (depositOne()) {
            session.act();
            return StepResult.jumpTo(D_CLOSE);
        }

        return StepResult.RUNNING;
    }

    private StepResult closeDestination() {
        // Refresh the cache so the labeler knows what was just stored here.
        session.access.read(sortDest.pos, false);
        session.access.close();
        session.act();

        if (session.carried.isEmpty()) return done();

        return StepResult.jumpTo(DEST);
    }

    private StepResult done() {
        deliveryDone = true;
        return StepResult.RUNNING;
    }

    private boolean depositOne() {
        if (!session.access.isOpen()) return true;

        int containerSlots = session.access.containerSlots();

        for (Iterator<Map.Entry<Integer, Carried>> iterator = session.carried.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Integer, Carried> entry = iterator.next();
            Carried carried = entry.getValue();
            int inventoryIndex = entry.getKey();
            ItemStack stack = session.inventory.stack(inventoryIndex);

            // A slot that now holds something else no longer carries this stack: forget it instead
            // of depositing whatever took its place.
            if (stack.isEmpty() || stack.getItem() != carried.item()) {
                iterator.remove();
                continue;
            }

            if (!accepts(sortDest, carried.group(), stack)) continue;

            int slotId = session.access.playerSlotId(inventoryIndex, containerSlots);
            if (slotId < 0) continue;
            if (!sortDest.hasRoomFor(stack)) continue;

            session.access.click(slotId, 0, SlotActionType.QUICK_MOVE);
            depositIndex = inventoryIndex;
            depositItem = carried.item();
            depositGroup = carried.group();
            depositWait = session.timings.slotClick();
            iterator.remove();
            session.access.read(sortDest.pos, false);
            return false;
        }

        return true;
    }

    private void verifyDeposit() {
        if (depositIndex < 0 || depositItem == null) return;

        ItemStack left = session.inventory.stack(depositIndex);

        if (!left.isEmpty() && left.getItem() == depositItem) {
            session.carried.put(depositIndex, new Carried(depositItem, depositGroup));
            failedDeposits++;

            if (failedDeposits > 30) {
                dropUndeliverable("the server refuses the item moves (anti-cheat?)");
            }
        } else {
            failedDeposits = 0;
        }

        depositIndex = -1;
        depositItem = null;
        depositGroup = null;
    }

    /** Leaves the current buffer in the player inventory and keeps the run going. */
    private void dropUndeliverable(String reason) {
        if (!session.carried.isEmpty()) {
            session.report.undelivered(reason, session.carried.size());
            session.warning("Could not deliver %d stack(s): %s. They stay in the inventory.",
                session.carried.size(), reason);
        }

        session.carried.clear();
        batchGroup = null;
        delivering = false;
        deliverScript.jumpTo(DEST);
        failedDeposits = 0;
        sortDest = null;
    }

    /** Allows a group discovered after planning into a chest that still has room. */
    private StashContainer packNewGroup(String group, ItemStack stack) {
        for (StashContainer container : session.containers) {
            if (container.failed || !container.scanned || container.groups.contains(group)) continue;
            if (container.size - container.reserved <= 0) continue;
            if (!container.hasRoomFor(stack)) continue;

            container.groups.add(group);
            container.reserved = Math.min(container.size, container.reserved + 1);
            return container;
        }

        return null;
    }

    /** A filled shulker the unpack phase will open; it must not be moved as a normal item. */
    private boolean isBoxToUnpack(ItemStack stack) {
        return session.isBoxToUnpack(stack);
    }

    /** The real stack behind a buffer entry, so room is measured for what will be moved. */
    private ItemStack carriedStack(Map.Entry<Integer, Carried> entry) {
        ItemStack stack = session.inventory.stack(entry.getKey());
        return stack.isEmpty() ? new ItemStack(entry.getValue().item()) : stack;
    }

    /**
     * The container that takes the most stacks of the buffer, so one walk delivers as much as
     * possible instead of one stack per trip. {@code depositStep} accepts a stack with the very
     * same predicate: with a stack of 1 here and the real stack there, a chest with only a
     * partial slot left used to be chosen, refuse the deposit and be reopened forever.
     */
    private StashContainer bestDestination() {
        StashContainer best = null;
        int bestCount = 0;

        for (StashContainer container : session.containers) {
            if (container.failed || !container.scanned) continue;

            int count = 0;
            for (Map.Entry<Integer, Carried> entry : session.carried.entrySet()) {
                ItemStack stack = carriedStack(entry);
                if (!container.hasRoomFor(stack)) continue;
                if (!accepts(container, entry.getValue().group(), stack)) continue;

                count++;
            }

            if (count > bestCount) {
                best = container;
                bestCount = count;
            }
        }

        return best;
    }

    /** True when the container is the home of the group, or of the item a box is made of. */
    private boolean accepts(StashContainer container, String group, ItemStack stack) {
        if (container.groups.contains(group)) return true;

        // A shulker box that does not fit with its own item still belongs with the other boxes.
        // The fallback is only for boxes: the own group of gear differs from its kit group.
        if (!stack.isIn(ItemTags.SHULKER_BOXES)) return false;

        String own = ownGroup(stack);
        return !own.equals(group) && container.groups.contains(own);
    }

    /** Group of the item itself, ignoring what a shulker box holds. */
    private String ownGroup(ItemStack stack) {
        return ItemGrouping.key(stack, session.host.mode());
    }

    /** The batch is finished: the exhausted source is remembered and the sort restarts. */
    private void finishBatch() {
        if (sourceExhausted && sortSource != null) session.exhaustedSources.add(sortSource.pos);

        sourceExhausted = false;
        batchGroup = null;
        sortSource = null;
        sortScript.jumpTo(SELECT);
    }

}
