package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Unpacks the shulker boxes found in the stash.
 * <p>
 * Vanilla never opens a shulker box held in the hand: right-clicking one places the block instead.
 * The box is therefore taken out of its chest, placed on free floor space next to the player,
 * opened, emptied stack by stack, mined with a pickaxe (or by hand) and picked up again. The empty
 * box is then carried to the chest of the shulker box group, so no box is left lying on the floor.
 * <p>
 * Boxes selected by {@link ShulkerProtection} are left intact: renamed boxes, bulk boxes, kits and
 * boxes holding a protected item are never opened. The sorter stores them shut, in the chest of the
 * item they hold when possible.
 */
public final class ShulkerUnpacker {
    private static final int PREPARE = 0;
    private static final int WALK = 1;
    private static final int OPEN = 2;
    private static final int TAKE_BOX = 3;
    private static final int VERIFY_TAKE = 4;
    private static final int CHOOSE_SPOT = 5;
    private static final int PLACE_BOX = 6;
    private static final int VERIFY_PLACE = 7;
    private static final int OPEN_PLACED = 8;
    private static final int EXTRACT = 9;
    private static final int RETURN = 10;
    private static final int CLOSE_BREAK = 11;
    private static final int BREAK = 12;
    private static final int PICK_UP = 13;
    private static final int ADVANCE = 14;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::prepare, this::walkToSource, this::openSource, this::takeBox, this::verifyTake,
        this::chooseSpot, this::placeBox, this::verifyPlacement, this::openPlaced, this::extractStep,
        this::returnToPlaced, this::closeAndPrepareBreak, this::breakBox, this::pickUp, this::advanceStep);

    private int index;
    private int wait;
    private int retries;
    private int shulkerSlot = -1;
    private int breakTicks;
    private int pickupTicks;
    private boolean movedForPickup;
    private int unpacked;

    private ShulkerRef ref;
    private Item takeItem;
    private Map<Integer, ItemStack> takeSnapshot;
    /** Null until a box is taken: the empty stack constant needs the game registries. */
    private ItemStack placingStack;
    private BlockPos placePos;
    private BlockPos placedPos;
    private int placeWait;
    private Map<Integer, ItemStack> beforeBreak;
    private Item extractItem;
    private Map<Integer, ItemStack> extractSnapshot;
    private int extractWait;

    public ShulkerUnpacker(StashSession session) {
        this.session = session;
    }

    /** Restarts the walk over the reference list (the list itself may have been rebuilt). */
    public void reset() {
        index = 0;
        unpacked = 0;
        ref = null;
        resetTarget();
    }

    public String progress() {
        return Math.min(index, session.shulkers.size()) + "/" + session.shulkers.size();
    }

    /** The box being emptied (or the source chest it is coming from), or null between boxes. */
    public BlockPos currentTarget() {
        if (placedPos != null) return placedPos;
        if (placePos != null) return placePos;
        return ref == null ? null : ref.pos;
    }

    /** Completion of the phase in {@code [0, 1]}, or -1 when the plan holds no box. */
    public double fraction() {
        return session.shulkers.isEmpty() ? -1 : Math.min(1, (double) index / session.shulkers.size());
    }

    public void tick() {
        if (session.sorter.isDelivering()) {
            session.sorter.deliverTick();
            return;
        }

        if (!session.unpacksShulkers()) {
            session.nextPhase(StashPhase.Pack);
            return;
        }

        if (index >= session.shulkers.size()) {
            // The empty boxes of the last shulkers still have to reach their chest.
            if (!session.carried.isEmpty()) {
                session.sorter.startDelivery();
                return;
            }

            if (unpacked > 0) session.info("Unpacked %d shulker(s), their contents are sorted.", unpacked);

            session.nextPhase(StashPhase.Pack);
            return;
        }

        ref = session.shulkers.get(index);
        script.run();
    }

    private StepResult prepare() {
        if (ref.done) {
            advance();
            return StepResult.RUNNING;
        }

        StashContainer container = session.access.at(ref.pos);

        if (container == null || container.failed) {
            ref.done = true;
            advance();
            return StepResult.RUNNING;
        }

        // The cache is refreshed every time a container is opened: a reference the sort has
        // invalidated is skipped here, without walking to or opening anything.
        if (ref.slot >= container.contents.size() || !session.isBoxToUnpack(container.contents.get(ref.slot))) {
            ref.done = true;
            advance();
            return StepResult.RUNNING;
        }

        resetTarget();
        session.access.resetOpen();
        return StepResult.jumpTo(WALK);
    }

    private StepResult walkToSource() {
        Navigator.Result navigation = session.navigator.walkToDetailed(ref.pos);

        if (navigation == Navigator.Result.ARRIVED) return StepResult.jumpTo(OPEN);

        if (navigation == Navigator.Result.FAILED) {
            abandon("Could not reach %s; its shulker box stays in place.", ref.pos.toShortString());
        }

        return StepResult.RUNNING;
    }

    private StepResult openSource() {
        int result = session.access.open(ref.pos);

        if (result == 1) {
            session.act();
            return StepResult.jumpTo(TAKE_BOX);
        }

        if (result == -1) {
            abandon("Could not open %s; its shulker boxes stay in place.", ref.pos.toShortString());
        }

        return StepResult.RUNNING;
    }

    private StepResult takeBox() {
        if (!session.access.isOpen()) return StepResult.jumpTo(WALK);

        ItemStack stack = session.access.liveStack(ref.slot);

        if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES) || !session.isBoxToUnpack(stack)) {
            // Correct the cache with what is really there, so the remaining references of this
            // container are validated without reopening it.
            session.access.read(ref.pos, false);
            ref.done = true;
            session.access.close();
            script.jumpTo(PREPARE);
            session.act();
            return StepResult.RUNNING;
        }

        // The box is placed right after being taken, so a single free slot is enough; the extra
        // room is needed while its contents are moved into the inventory.
        if (session.inventory.freeSlots() < 1) {
            session.access.close();

            if (!session.carried.isEmpty()) {
                session.sorter.startDelivery();
                return StepResult.RUNNING;
            }

            abandon("No free inventory slot to unpack %s; the box stays in place.", ref.pos.toShortString());
            return StepResult.RUNNING;
        }

        takeItem = stack.getItem();
        takeSnapshot = session.inventory.snapshot();
        session.access.click(session.access.liveSlotId(ref.slot), 0, SlotActionType.QUICK_MOVE);
        wait = session.timings.slotClick();
        session.act();
        return StepResult.jumpTo(VERIFY_TAKE);
    }

    private StepResult verifyTake() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        int taken = session.inventory.findTakenSlot(takeItem, takeSnapshot);
        takeItem = null;
        takeSnapshot = null;

        if (taken < 0) {
            ref.done = true;
            session.access.close();
            script.jumpTo(PREPARE);
            session.act();
            return StepResult.RUNNING;
        }

        shulkerSlot = taken;
        placingStack = session.inventory.stack(taken).copy();

        // The box just left this slot: refresh the cache so the next references of this container
        // are validated without reopening it for an empty slot.
        session.access.read(ref.pos, false);
        session.access.close();
        session.act();
        return StepResult.jumpTo(CHOOSE_SPOT);
    }

    private StepResult chooseSpot() {
        placePos = findPlaceSpot();

        if (placePos == null) {
            storeIntact("No free spot to place the shulker box at %s; it is stored in a chest with its contents.");
            return StepResult.RUNNING;
        }

        Navigator.Result navigation = session.navigator.walkToDetailed(placePos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            session.act();
            return StepResult.jumpTo(PLACE_BOX);
        }

        if (navigation == Navigator.Result.FAILED) {
            storeIntact("Could not reach a spot to open the shulker box at %s; it is stored in a chest with its contents.");
        }

        return StepResult.RUNNING;
    }

    private StepResult placeBox() {
        if (placingStack == null || placingStack.isEmpty() || !placingStack.isIn(ItemTags.SHULKER_BOXES)) {
            storeIntact("The shulker box taken from %s disappeared; skipping it.");
            return StepResult.RUNNING;
        }

        int hotbar = session.inventory.ensureInHotbarStack(placingStack);

        if (hotbar < 0) {
            storeIntact("Could not take the shulker box from %s in hand; it is stored in a chest with its contents.");
            return StepResult.RUNNING;
        }

        shulkerSlot = hotbar;

        if (!session.game.place(placePos, placingStack)) {
            if (++retries > 3) {
                storeIntact("Could not place the shulker box taken from %s; it is stored in a chest with its contents.");
                return StepResult.RUNNING;
            }

            session.act();
            return StepResult.RUNNING;
        }

        // BlockUtils.place rotates asynchronously: wait for the block to exist.
        placeWait = session.timings.blockPlace();
        session.act();
        return StepResult.jumpTo(VERIFY_PLACE);
    }

    private StepResult verifyPlacement() {
        if (placeWait > 0) {
            placeWait--;
            return StepResult.RUNNING;
        }

        if (!session.game.isShulkerAt(placePos)) {
            if (++retries > 3) {
                storeIntact("Could not place the shulker box taken from %s; it is stored in a chest with its contents.");
                return StepResult.RUNNING;
            }

            return StepResult.jumpTo(PLACE_BOX);
        }

        retries = 0;
        placedPos = placePos;
        session.act();
        return StepResult.jumpTo(OPEN_PLACED);
    }

    private StepResult openPlaced() {
        int result = session.access.open(placedPos);

        if (result == 1) {
            session.act();
            return StepResult.jumpTo(EXTRACT);
        }

        if (result == -1) {
            // It cannot be opened where it stands: mine it again and store it as it is.
            session.warning("Could not open the shulker box placed at %s; storing it with its contents.", placedPos.toShortString());
            return StepResult.jumpTo(CLOSE_BREAK);
        }

        return StepResult.RUNNING;
    }

    private StepResult extractStep() {
        if (extractWait > 0) {
            extractWait--;
            if (extractWait == 0) return finishExtract();

            return StepResult.RUNNING;
        }

        if (!session.access.isOpen()) return StepResult.jumpTo(OPEN_PLACED);

        if (session.inventory.freeSlots() < 2) {
            session.access.close();

            if (session.carried.isEmpty()) {
                session.warning("No free inventory slot left; the shulker box at %s keeps the rest of its contents.",
                    placedPos.toShortString());
                return StepResult.jumpTo(CLOSE_BREAK);
            }

            // Deliver what was already taken, then come back and finish this box.
            session.sorter.startDelivery();
            return StepResult.jumpTo(RETURN);
        }

        int containerSlots = session.access.containerSlots();

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = session.access.liveStack(i);
            if (stack.isEmpty()) continue;

            extractItem = stack.getItem();
            extractSnapshot = session.inventory.snapshot();
            session.access.click(session.access.liveSlotId(i), 0, SlotActionType.QUICK_MOVE);
            extractWait = session.timings.slotClick();
            session.act();
            return StepResult.RUNNING;
        }

        // The box is empty now: break it and store the empty box.
        session.act();
        return StepResult.jumpTo(CLOSE_BREAK);
    }

    private StepResult finishExtract() {
        Item item = extractItem;
        int taken = session.inventory.findTakenSlot(item, extractSnapshot);
        extractItem = null;
        extractSnapshot = null;

        if (taken < 0) {
            // The server refused the move: keep the rest inside and store the box shut.
            session.access.close();
            session.warning("The shulker box at %s refused to give all its items; storing it with the rest.",
                placedPos.toShortString());
            return StepResult.jumpTo(CLOSE_BREAK);
        }

        session.act();
        return StepResult.jumpTo(EXTRACT);
    }

    private StepResult returnToPlaced() {
        Navigator.Result navigation = session.navigator.walkToDetailed(placedPos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            return StepResult.jumpTo(OPEN_PLACED);
        }

        if (navigation == Navigator.Result.FAILED) {
            abandon("Could not walk back to the shulker box placed at %s; it stays there to be collected.",
                placedPos.toShortString());
        }

        return StepResult.RUNNING;
    }

    private StepResult closeAndPrepareBreak() {
        if (session.access.isOpen()) {
            session.access.close();
            session.act();
            return StepResult.RUNNING;
        }

        if (!session.game.isShulkerAt(placedPos)) {
            // Nothing to break: either it is already gone or it was never placed in this run.
            if (beforeBreak == null) {
                advance();
                return StepResult.RUNNING;
            }

            return StepResult.jumpTo(PICK_UP);
        }

        if (session.inventory.freeSlots() < 1) {
            if (!session.carried.isEmpty()) {
                session.sorter.startDelivery();
                return StepResult.RUNNING;
            }

            session.warning("No free inventory slot to pick up the shulker box at %s; it stays on the floor.",
                placedPos.toShortString());
            advance();
            return StepResult.RUNNING;
        }

        // A pickaxe mines the box instantly; the hand works too, only slower.
        session.inventory.ensurePickaxe();

        beforeBreak = session.inventory.snapshot();
        breakTicks = 0;
        pickupTicks = 0;
        movedForPickup = false;
        session.act();
        return StepResult.jumpTo(BREAK);
    }

    private StepResult breakBox() {
        if (!session.game.isShulkerAt(placedPos)) {
            wait = session.timings.pickupRetry();
            movedForPickup = false;
            session.act();
            return StepResult.jumpTo(PICK_UP);
        }

        Navigator.Result navigation = session.navigator.walkToDetailed(placedPos);

        if (navigation == Navigator.Result.FAILED) {
            abandon("Could not reach the shulker box at %s; it stays on the floor.", placedPos.toShortString());
            return StepResult.RUNNING;
        }

        if (navigation != Navigator.Result.ARRIVED) return StepResult.RUNNING;

        if (++breakTicks > session.timeoutTicks()) {
            abandon("Could not mine the shulker box at %s; it stays on the floor.", placedPos.toShortString());
            return StepResult.RUNNING;
        }

        // The client keeps the breaking progress; calling it every tick is required, so the
        // global action delay is deliberately not reset here.
        session.game.lookAt(Vec3d.ofCenter(placedPos), 100);
        session.game.breakBlock(placedPos);
        return StepResult.RUNNING;
    }

    private StepResult pickUp() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        if (!movedForPickup) {
            movedForPickup = true;
            // The block is air now, so standing on its spot collects the dropped box.
            session.mover.moveToStanding(placedPos);
            wait = session.timings.pickupMove();
            return StepResult.RUNNING;
        }

        shulkerSlot = session.inventory.findNewShulker(beforeBreak);

        if (shulkerSlot >= 0) return store();

        if (++pickupTicks > session.timings.maxPickupTries()) {
            session.warning("The shulker box at %s was not picked up (is the inventory full?); it stays on the floor.",
                placedPos.toShortString());
            unpacked++;
            advance();
            return StepResult.RUNNING;
        }

        wait = session.timings.pickupRetry();
        return StepResult.RUNNING;
    }

    private StepResult store() {
        ItemStack stack = session.inventory.stack(shulkerSlot);

        if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) {
            advance();
            return StepResult.RUNNING;
        }

        ref.done = true;
        session.carried.put(shulkerSlot, new Carried(stack.getItem(), session.groupOf(stack)));
        unpacked++;
        advance();

        // Empty boxes are delivered in batches instead of walking to their chest for each one.
        if (session.carried.size() >= session.batch() || session.inventory.freeSlots() <= 3) {
            session.sorter.startDelivery();
        }

        return StepResult.RUNNING;
    }

    /** Fallback when the box cannot be emptied: it goes into a chest shut, as it is. */
    private void storeIntact(String message) {
        ItemStack held = shulkerSlot >= 0 ? session.inventory.stack(shulkerSlot) : ItemStack.EMPTY;

        if (!held.isEmpty() && held.isIn(ItemTags.SHULKER_BOXES)) {
            session.carried.put(shulkerSlot, new Carried(held.getItem(), session.groupOf(held)));
        }

        session.warning(message, ref.pos.toShortString());
        ref.done = true;
        advance();
        session.sorter.startDelivery();
    }

    private void abandon(String message, Object... args) {
        session.warning(message, args);
        ref.done = true;
        advance();
    }

    private StepResult advanceStep() {
        advance();
        return StepResult.RUNNING;
    }

    private void advance() {
        index++;
        resetTarget();
    }

    private void resetTarget() {
        script.jumpTo(PREPARE);
        wait = 0;
        retries = 0;
        shulkerSlot = -1;
        breakTicks = 0;
        pickupTicks = 0;
        movedForPickup = false;
        placingStack = null;
        placePos = null;
        placedPos = null;
        placeWait = 0;
        beforeBreak = null;
        extractItem = null;
        extractSnapshot = null;
        extractWait = 0;
        session.act();
    }

    /**
     * A free floor spot close to the player where the box can be placed, with the free space above
     * it that a vanilla shulker box needs to open.
     */
    private BlockPos findPlaceSpot() {
        BlockPos origin = session.game.playerFeet();

        for (int radius = 1; radius <= 4; radius++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;

            for (int dy = -2; dy <= 1; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                        BlockPos pos = origin.add(dx, dy, dz);
                        if (!session.spots.canPlaceAt(pos)) continue;

                        double distance = pos.getSquaredDistance(origin);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos;
                        }
                    }
                }
            }

            if (best != null) return best;
        }

        return null;
    }

}
