package com.example.addon.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Empties the player's inventory into one or more staging chests before the stash is scanned, so
 * the whole inventory can be used as a clean sorting buffer. Only the supplies the organizer
 * itself needs (chests, item frames, empty shulker boxes) are kept. Protected shulker boxes are
 * dumped too and then sorted like normal items; they are still never opened.
 */
public final class InventoryDumper {
    private static final int INVENTORY_SLOTS = 36;

    private static final int CONTAINER = 0;
    private static final int WALK = 1;
    private static final int OPEN = 2;
    private static final int DUMP = 3;
    private static final int VERIFY = 4;
    private static final int FINISH = 5;
    private static final int EXTRA_CHEST = 6;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::chooseContainer, this::walkToContainer, this::openContainer, this::dumpNext,
        this::verifyMove, this::finishDump, this::placeAnotherChest);

    private int slotIndex;
    private int wait;
    private int failedMoves;
    private int dumped;
    private int lastSlot = -1;
    private final List<BlockPos> newChests = new ArrayList<>();

    public InventoryDumper(StashSession session) {
        this.session = session;
    }

    public String status() {
        return dumped + " stack(s)";
    }

    public boolean hasDumpableItems() {
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            ItemStack stack = session.inventory.stack(i);
            if (!stack.isEmpty() && !isSupply(stack)) return true;
        }

        return false;
    }

    public void tick() {
        script.run();
    }

    private StepResult chooseContainer() {
        if (session.dumpPos != null
            && ContainerTypes.isContainer(session.game.blockAt(session.dumpPos), session.extraContainers())) {
            return StepResult.jumpTo(WALK);
        }

        int result = session.placer.placeOneChest(null, true);

        if (result == 0) return StepResult.RUNNING;

        if (result == -1) {
            abort("No free spot to place a staging chest; the inventory is left as is.");
            return StepResult.RUNNING;
        }

        StashContainer chest = session.placer.lastPlaced();

        if (chest == null) {
            abort("No staging chest could be placed; the inventory is left as is.");
            return StepResult.RUNNING;
        }

        session.dumpPos = chest.pos;
        newChests.add(chest.pos);
        return StepResult.jumpTo(WALK);
    }

    private StepResult walkToContainer() {
        Navigator.Result result = session.navigator.walkToDetailed(session.dumpPos);

        if (result == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            return StepResult.jumpTo(OPEN);
        }

        if (result == Navigator.Result.FAILED) {
            abort("Could not reach the staging chest; the inventory is left as is.");
        }

        return StepResult.RUNNING;
    }

    private StepResult openContainer() {
        int result = session.access.open(session.dumpPos);

        if (result == 1) {
            session.access.read(session.dumpPos, false);
            slotIndex = 0;
            session.act();
            return StepResult.jumpTo(DUMP);
        }

        if (result == -1) {
            abort("Could not open the staging chest; the inventory is left as is.");
        }

        return StepResult.RUNNING;
    }

    private StepResult dumpNext() {
        if (!session.access.isOpen()) return StepResult.jumpTo(CONTAINER);
        if (slotIndex >= INVENTORY_SLOTS) return StepResult.jumpTo(FINISH);

        ItemStack stack = session.inventory.stack(slotIndex);

        if (stack.isEmpty() || isSupply(stack)) {
            slotIndex++;
            return StepResult.RUNNING;
        }

        StashContainer dump = session.access.at(session.dumpPos);

        if (dump == null) {
            abort("The staging chest disappeared.");
            return StepResult.RUNNING;
        }

        if (!dump.hasRoomFor(stack)) {
            session.access.close();
            return StepResult.jumpTo(EXTRA_CHEST);
        }

        int slotId = session.access.playerSlotId(slotIndex, session.access.containerSlots());

        if (slotId < 0) {
            slotIndex++;
            return StepResult.RUNNING;
        }

        lastSlot = slotIndex;
        session.access.click(slotId, 0, SlotActionType.QUICK_MOVE);
        wait = session.timings.slotClick();
        session.act();
        return StepResult.jumpTo(VERIFY);
    }

    private StepResult verifyMove() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        ItemStack left = session.inventory.stack(lastSlot);

        if (left.isEmpty()) {
            dumped++;
            failedMoves = 0;
            slotIndex++;
            session.access.read(session.dumpPos, false);
            return StepResult.jumpTo(DUMP);
        }

        if (++failedMoves >= 3) {
            abort("The staging chest refused the items; the rest stays in the inventory.");
            return StepResult.RUNNING;
        }

        return StepResult.jumpTo(DUMP);
    }

    private StepResult finishDump() {
        session.access.close();

        if (dumped > 0) {
            session.info("Dumped %d stack(s) into %d staging chest(s).", dumped, newChests.size());
        }

        releaseChests();

        if (session.resuming) {
            session.persist();
            session.beginRescan();
        } else {
            session.nextPhase(StashPhase.Scan);
        }

        return StepResult.RUNNING;
    }

    private StepResult placeAnotherChest() {
        int result = session.placer.placeOneChest(null, true);

        if (result == 0) return StepResult.RUNNING;

        if (result == -1) {
            abort("The staging chest is full and no other chest can be placed.");
            return StepResult.RUNNING;
        }

        StashContainer chest = session.placer.lastPlaced();

        if (chest == null) {
            abort("The staging chest is full and no other chest can be placed.");
            return StepResult.RUNNING;
        }

        newChests.add(chest.pos);
        session.dumpPos = chest.pos;
        return StepResult.jumpTo(WALK);
    }

    private void abort(String message) {
        session.access.close();
        session.warning(message);
        releaseChests();

        if (session.resuming) {
            session.persist();
            session.beginRescan();
        } else {
            session.nextPhase(StashPhase.Scan);
        }
    }

    /**
     * On a fresh run the staging chests must be scanned again so the planner sees their new
     * contents. On a resume the loaded containers are rescanned anyway, so they can stay in the
     * list.
     */
    private void releaseChests() {
        if (session.resuming) return;

        for (BlockPos pos : newChests) {
            session.containers.removeIf(container -> container.pos.equals(pos));

            if (!session.scanQueue.contains(pos)) session.scanQueue.addLast(pos);
        }

        newChests.clear();
    }

    private boolean isSupply(ItemStack stack) {
        if (stack.isOf(Items.CHEST) || stack.isOf(Items.TRAPPED_CHEST) || stack.isOf(Items.ITEM_FRAME)) {
            return true;
        }

        // The packing phase needs a pickaxe and the shulkers that still have room.
        if (session.packsShulkers() && stack.isIn(ItemTags.PICKAXES)) return true;

        // A kit/bulk shulker stays closed, but it can still be stored in a chest with the items.
        if (stack.isIn(ItemTags.SHULKER_BOXES) && session.protection.protectsShulkerAsItem(stack)) {
            return false;
        }

        if (session.allowOverflowShulkers() && InventoryTools.isEmptyShulker(stack)) return true;

        return session.packsShulkers()
            && stack.isIn(ItemTags.SHULKER_BOXES)
            && InventoryTools.shulkerHasAnyRoom(stack);
    }
}
