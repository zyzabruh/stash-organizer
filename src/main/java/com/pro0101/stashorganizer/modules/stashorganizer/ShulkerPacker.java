package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Packing phase: overflow shulkers were placed and filled by the sorter. This phase mines each of
 * them with a pickaxe, picks up the filled shulker item and stores it in a chest with room.
 * <p>
 * A partially filled shulker selected by {@link ChestPlacer} keeps its existing groups, so the
 * sorter tops it up instead of emptying it. If the pickaxe disappears or no storage chest has
 * room, the shulker is left where it is and the run continues.
 */
public final class ShulkerPacker {
    private static final int START = 0;
    private static final int TOOLS = 1;
    private static final int BREAK = 2;
    private static final int WAIT_BREAK = 3;
    private static final int PICK_UP = 4;
    private static final int CHOOSE_OUTPUT = 5;
    private static final int WALK_OUTPUT = 6;
    private static final int OPEN_OUTPUT = 7;
    private static final int DEPOSIT = 8;
    private static final int VERIFY = 9;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::startTarget, this::ensureTools, this::breakShulker, this::waitForBreak, this::pickUp,
        this::chooseOutput, this::walkToOutput, this::openOutput, this::depositShulker, this::verifyDeposit);

    private final List<StashContainer> packing = new ArrayList<>();

    private int index;
    private int wait;
    private int breakTicks;
    private int pickupTicks;
    private int failedMoves;
    private boolean movedForPickup;

    private StashContainer target;
    private StashContainer output;
    private int shulkerSlot = -1;
    private Map<Integer, ItemStack> beforeBreak;

    public ShulkerPacker(StashSession session) {
        this.session = session;
    }

    public String progress() {
        return Math.min(index, packing.size()) + "/" + packing.size();
    }

    /** The overflow shulker being packed, or null between two shulkers. */
    public BlockPos currentTarget() {
        return target == null ? null : target.pos;
    }

    /** The chest the packed shulker is going to, or null before one is chosen. */
    public BlockPos currentOutput() {
        return output == null ? null : output.pos;
    }

    /** Completion of the phase in {@code [0, 1]}, or -1 before the shulkers are known. */
    public double fraction() {
        return packing.isEmpty() ? -1 : Math.min(1, (double) index / packing.size());
    }

    public void tick() {
        if (!session.packsShulkers()) {
            session.nextPhase(StashPhase.Arrange);
            return;
        }

        if (packing.isEmpty()) {
            for (StashContainer container : session.containers) {
                if (container.packing && !container.failed) packing.add(container);
            }
        }

        if (index >= packing.size()) {
            if (!packing.isEmpty()) {
                session.info("Packed and stored %d shulker(s).", session.packedShulkers);
            }

            session.nextPhase(StashPhase.Arrange);
            return;
        }

        target = packing.get(index);

        if (script.index() == START && (target.failed || !target.packing)) {
            index++;
            return;
        }

        script.run();
    }

    private StepResult startTarget() {
        session.access.close();
        output = null;
        shulkerSlot = -1;
        beforeBreak = null;
        breakTicks = 0;
        pickupTicks = 0;
        failedMoves = 0;
        movedForPickup = false;

        Navigator.Result navigation = session.navigator.walkToDetailed(target.pos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            session.act();
            return StepResult.jumpTo(TOOLS);
        }

        if (navigation == Navigator.Result.FAILED) {
            skipTarget("Could not reach %s to pack it.", target.pos.toShortString());
        }

        return StepResult.RUNNING;
    }

    private StepResult ensureTools() {
        if (session.inventory.ensurePickaxe() < 0) {
            skipTarget("No pickaxe found; the shulker at %s stays placed.", target.pos.toShortString());
            return StepResult.RUNNING;
        }

        beforeBreak = session.inventory.snapshot();
        session.act();
        return StepResult.jumpTo(BREAK);
    }

    private StepResult breakShulker() {
        if (!session.game.isShulkerAt(target.pos)) {
            return StepResult.jumpTo(WAIT_BREAK);
        }

        if (++breakTicks > 20 * 60) {
            skipTarget("Could not mine the shulker at %s; it stays placed.", target.pos.toShortString());
            return StepResult.RUNNING;
        }

        // The client keeps the breaking progress; calling it every tick is required, so the
        // global action delay is deliberately not reset here.
        session.game.lookAt(Vec3d.ofCenter(target.pos), 100);
        session.game.breakBlock(target.pos);
        return StepResult.RUNNING;
    }

    private StepResult waitForBreak() {
        if (session.game.isShulkerAt(target.pos)) {
            return StepResult.jumpTo(BREAK);
        }

        // The block is gone, but keep the target active until its item has been stored; the top
        // of tick() would otherwise skip the remaining stages.
        wait = session.timings.pickupRetry();
        session.act();
        return StepResult.jumpTo(PICK_UP);
    }

    private StepResult pickUp() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        if (!movedForPickup) {
            movedForPickup = true;
            // Pulled items are only collected when the player is close enough; the block itself
            // is now air, so walking onto it is safe.
            session.mover.moveToStanding(target.pos);
            wait = session.timings.pickupMove();
            return StepResult.RUNNING;
        }

        shulkerSlot = session.inventory.findNewShulker(beforeBreak);

        if (shulkerSlot >= 0) {
            session.act();
            return StepResult.jumpTo(CHOOSE_OUTPUT);
        }

        if (++pickupTicks > session.timings.maxPickupTries()) {
            session.warning("The packed shulker at %s was not picked up (is the inventory full?).",
                target.pos.toShortString());
            skipTargetFailure();
            return StepResult.RUNNING;
        }

        wait = session.timings.pickupRetry();
        return StepResult.RUNNING;
    }

    private StepResult chooseOutput() {
        ItemStack stack = session.inventory.stack(shulkerSlot);

        if (stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) {
            skipTargetFailure();
            return StepResult.RUNNING;
        }

        output = findOutput(stack);

        if (output == null) {
            session.warning("No chest with room for the packed shulker; it stays in the inventory.");
            skipTargetFailure();
            return StepResult.RUNNING;
        }

        session.access.resetOpen();
        session.act();
        return StepResult.jumpTo(WALK_OUTPUT);
    }

    private StepResult walkToOutput() {
        Navigator.Result navigation = session.navigator.walkToDetailed(output.pos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            return StepResult.jumpTo(OPEN_OUTPUT);
        }

        if (navigation == Navigator.Result.FAILED) {
            session.warning("Could not reach a chest to store the packed shulker; it stays in the inventory.");
            skipTargetFailure();
        }

        return StepResult.RUNNING;
    }

    private StepResult openOutput() {
        int result = session.access.open(output.pos);

        if (result == 1) {
            session.access.read(output.pos, false);
            session.act();
            return StepResult.jumpTo(DEPOSIT);
        }

        if (result == -1) {
            session.warning("Could not open a chest to store the packed shulker; it stays in the inventory.");
            skipTargetFailure();
        }

        return StepResult.RUNNING;
    }

    private StepResult depositShulker() {
        if (!session.access.isOpen()) return StepResult.jumpTo(OPEN_OUTPUT);

        int slotId = session.access.playerSlotId(shulkerSlot, session.access.containerSlots());
        ItemStack stack = session.inventory.stack(shulkerSlot);

        if (slotId < 0 || stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) {
            session.access.close();
            skipTargetFailure();
            return StepResult.RUNNING;
        }

        session.access.click(slotId, 0, SlotActionType.QUICK_MOVE);
        wait = session.timings.slotDeposit();
        session.act();
        return StepResult.jumpTo(VERIFY);
    }

    private StepResult verifyDeposit() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        ItemStack left = session.inventory.stack(shulkerSlot);

        if (left.isEmpty()) {
            session.packedShulkers++;
            target.failed = true;
            target.packing = false;
            session.access.read(output.pos, false);
            session.access.close();
            index++;
            resetTarget();
            session.act();
            return StepResult.RUNNING;
        }

        if (++failedMoves < 3) {
            return StepResult.jumpTo(DEPOSIT);
        }

        session.warning("The chest refused the packed shulker; it stays in the inventory.");
        session.access.close();
        skipTargetFailure();
        return StepResult.RUNNING;
    }

    /**
     * Prefers the chest of the packed shulker's own group (the item it holds), then any chest with
     * room. Another shulker box is never a candidate: the game refuses to nest boxes, so a packed
     * box has to go into a chest or stay in the inventory.
     */
    private StashContainer findOutput(ItemStack stack) {
        String group = session.groupOf(stack);
        StashContainer fallback = null;

        for (StashContainer container : session.containers) {
            if (container == target || container.failed || !container.scanned || container.packing) continue;
            if (!container.accepts(stack)) continue;
            if (!container.hasRoomFor(stack)) continue;

            if (container.groups.contains(group)) return container;
            if (fallback == null) fallback = container;
        }

        return fallback;
    }

    /** The current target cannot be packed: forget it and move on. */
    private void skipTargetFailure() {
        target.failed = true;
        target.packing = false;
        index++;
        resetTarget();
    }

    private void skipTarget(String message, Object... args) {
        target.packing = false;
        index++;
        resetTarget();
        session.warning(message, args);
        session.act();
    }

    private void resetTarget() {
        script.jumpTo(START);
    }
}
