package com.example.addon.modules.stashorganizer;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/** Puts an item frame with a representative item on every sorted chest. */
public final class ChestLabeler {
    private static final int START = 0;
    private static final int WALK = 1;
    private static final int OPEN = 2;
    private static final int TAKE = 3;
    private static final int TAKEN = 4;
    private static final int PLACE_FRAME = 5;
    private static final int FIND_FRAME = 6;
    private static final int INSERT = 7;
    private static final int FINISH = 8;
    private static final int ROTATE = 9;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::startLabel, this::walkToChest, this::openChest, this::takeLabelItem, this::verifyLabelTake,
        this::placeFrame, this::findFrame, this::insertLabelItem, this::finishLabel, this::rotateLabelFrame);

    private int index;
    private int wait;

    private StashContainer container;
    private Item labelItem;
    private ItemFrameEntity labelFrame;
    private Direction labelFace;
    private boolean reuseFrame;
    private int labelSlot = -1;

    private Item takeItem;
    private Map<Integer, ItemStack> takeSnapshot;

    private ItemFrameEntity rotationFrame;
    private int rotationWaits;
    private int rotationClicks;

    public ChestLabeler(StashSession session) {
        this.session = session;
    }

    public String progress() {
        return Math.min(index, session.containers.size()) + "/" + session.containers.size();
    }

    /** The chest being labelled, or null between two chests. */
    public BlockPos currentChest() {
        return container == null ? null : container.pos;
    }

    /** Completion of the phase in {@code [0, 1]}, or -1 when there is no container to label. */
    public double fraction() {
        return session.containers.isEmpty() ? -1 : Math.min(1, (double) index / session.containers.size());
    }

    public void tick() {
        if (session.sorter.isDelivering()) {
            session.sorter.deliverTick();
            return;
        }

        if (!session.labelsChests()) {
            finishSummary();
            return;
        }

        if (index >= session.containers.size()) {
            finishSummary();
            return;
        }

        container = session.containers.get(index);

        if (container.failed || container.groups.isEmpty()) {
            index++;
            skipContainer();
            return;
        }

        script.run();
    }

    private StepResult startLabel() {
        reuseFrame = false;
        labelSlot = -1;
        labelFace = null;
        labelFrame = null;
        rotationFrame = null;
        rotationWaits = 0;
        rotationClicks = 0;

        Item representative = container.representative();

        if (representative == null) {
            index++;
            return StepResult.RUNNING;
        }

        labelItem = representative;
        session.access.resetOpen();
        return StepResult.jumpTo(WALK);
    }

    private StepResult walkToChest() {
        Navigator.Result navigation = session.navigator.walkToDetailed(container.pos);

        if (navigation == Navigator.Result.ARRIVED) return prepareLabel();

        if (navigation == Navigator.Result.FAILED) {
            index++;
            skipContainer();
        }

        return StepResult.RUNNING;
    }

    /** Chooses the frame face once the player is actually standing at the chest. */
    private StepResult prepareLabel() {
        labelFace = findFreeFaceDirection(container.pos);

        if (labelFace == null) {
            session.warning("No free face to put a frame on %s.", container.pos.toShortString());
            index++;
            skipContainer();
            return StepResult.RUNNING;
        }

        ItemFrameEntity existing = findAnyFrame(container.pos.offset(labelFace));

        if (existing != null) {
            if (existing.getHeldItemStack().getItem() == labelItem) {
                // Already labelled with the right item: only fix an upside-down frame.
                if (existing.getRotation() != 0) return startRotation(existing);

                index++;
                skipContainer();
                return StepResult.RUNNING;
            }

            if (existing.getHeldItemStack().isEmpty()) {
                // Take the label item from the chest first, then fill the existing frame.
                labelFrame = existing;
                reuseFrame = true;

                if (!container.contains(labelItem)) {
                    index++;
                    skipContainer();
                    return StepResult.RUNNING;
                }

                session.access.resetOpen();
                return StepResult.jumpTo(OPEN);
            }

            session.warning("Chest %s already has a frame with another item, skipping.", container.pos.toShortString());
            index++;
            skipContainer();
            return StepResult.RUNNING;
        }

        if (session.inventory.count(Items.ITEM_FRAME) <= 0) {
            session.warning("No item frame left, stopping the labelling.");
            finishSummary();
            return StepResult.RUNNING;
        }

        session.access.resetOpen();
        return StepResult.jumpTo(OPEN);
    }

    private StepResult openChest() {
        int result = session.access.open(container.pos);

        if (result == 1) {
            session.access.read(container.pos, false);
            session.act();
            return StepResult.jumpTo(TAKE);
        }

        if (result == -1) {
            index++;
            skipContainer();
        }

        return StepResult.RUNNING;
    }

    private StepResult takeLabelItem() {
        if (!container.contains(labelItem)) {
            index++;
            skipContainer();
            return StepResult.RUNNING;
        }

        for (int i = 0; i < session.access.containerSlots(); i++) {
            ItemStack stack = session.access.liveStack(i);

            if (stack.isEmpty() || stack.getItem() != labelItem) continue;

            takeItem = stack.getItem();
            takeSnapshot = session.inventory.snapshot();
            session.access.click(session.access.liveSlotId(i), 0, SlotActionType.QUICK_MOVE);
            session.act();
            return StepResult.jumpTo(TAKEN);
        }

        session.access.close();
        index++;
        skipContainer();
        return StepResult.RUNNING;
    }

    private StepResult verifyLabelTake() {
        int taken = session.inventory.findTakenSlot(takeItem, takeSnapshot);
        takeItem = null;
        takeSnapshot = null;

        if (taken < 0) {
            reuseFrame = false;
            session.access.close();
            index++;
            skipContainer();
            return StepResult.RUNNING;
        }

        labelSlot = taken;
        session.access.close();
        boolean reuse = reuseFrame;
        reuseFrame = false;
        wait = 0;
        session.act();
        return StepResult.jumpTo(reuse ? INSERT : PLACE_FRAME);
    }

    private StepResult placeFrame() {
        if (session.access.isOpen()) {
            session.access.close();
            session.act();
            return StepResult.RUNNING;
        }

        if (session.inventory.ensureInHotbar(Items.ITEM_FRAME) < 0) {
            releaseLabelItem();
            index++;
            skipContainer();
            return StepResult.RUNNING;
        }

        Vec3d hit = Vec3d.ofCenter(container.pos)
            .add(labelFace.getOffsetX() * 0.5, labelFace.getOffsetY() * 0.5, labelFace.getOffsetZ() * 0.5);

        session.game.lookAt(container.pos, 50);
        session.game.useBlock(container.pos, labelFace, hit);

        wait = session.timings.blockPlace();
        session.act();
        return StepResult.jumpTo(FIND_FRAME);
    }

    private StepResult findFrame() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        labelFrame = findEmptyFrame(container.pos.offset(labelFace));

        if (labelFrame == null) {
            session.warning("Could not place an item frame on %s.", container.pos.toShortString());
            releaseLabelItem();
            index++;
            skipContainer();
            return StepResult.RUNNING;
        }

        wait = 0;
        session.act();
        return StepResult.jumpTo(INSERT);
    }

    private StepResult insertLabelItem() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        int slot = session.inventory.findSlot(labelItem);

        if (!session.inventory.holdInHand(slot)) {
            releaseLabelItem();
            index++;
            skipContainer();
            session.act();
            return StepResult.RUNNING;
        }

        labelSlot = session.inventory.selectedSlot();
        session.game.useEntity(labelFrame);
        wait = session.timings.entityUse();
        session.act();
        return StepResult.jumpTo(FINISH);
    }

    private StepResult finishLabel() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        // A reused frame may have been turned: bring the label back upright.
        if (labelFrame != null && labelFrame.isAlive() && labelFrame.getRotation() != 0) {
            return startRotation(labelFrame);
        }

        releaseLabelItem();
        index++;
        skipContainer();
        session.act();
        return StepResult.RUNNING;
    }

    /** Turns an item frame until its item is upright again (rotation 0). */
    private StepResult startRotation(ItemFrameEntity frame) {
        rotationFrame = frame;
        rotationWaits = 0;
        rotationClicks = 0;
        session.act();
        return StepResult.jumpTo(ROTATE);
    }

    private StepResult rotateLabelFrame() {
        if (rotationFrame == null || !rotationFrame.isAlive() || rotationFrame.getRotation() == 0) {
            finishRotation();
            return StepResult.RUNNING;
        }

        if (rotationWaits > 0) {
            rotationWaits--;
            return StepResult.RUNNING;
        }

        if (!session.inventory.emptyHand()) {
            finishRotation();
            return StepResult.RUNNING;
        }

        session.game.useEntity(rotationFrame);
        rotationWaits = session.timings.frameRotation();
        rotationClicks++;

        if (rotationClicks > 8) {
            finishRotation();
            return StepResult.RUNNING;
        }

        session.act();
        return StepResult.RUNNING;
    }

    private void finishRotation() {
        rotationFrame = null;
        rotationWaits = 0;
        rotationClicks = 0;
        labelFrame = null;
        releaseLabelItem();
        index++;
        skipContainer();
        session.act();
    }

    /** The chest cannot be labelled: move on to the next container. */
    private void skipContainer() {
        script.jumpTo(START);
    }

    /** Returns the label item taken from a chest to the sorter when the frame cannot be filled. */
    private void releaseLabelItem() {
        if (labelSlot >= 0) {
            ItemStack remaining = session.inventory.stack(labelSlot);

            if (!remaining.isEmpty() && remaining.getItem() == labelItem) {
                session.carried.put(labelSlot, new Carried(labelItem, session.groupOf(remaining)));
                session.sorter.startDelivery();
            }
        }

        labelSlot = -1;
    }

    private void finishSummary() {
        session.access.close();
        session.finish("Stash organised: %d containers, %d groups, %d moves, %d chests, %d shulkers placed and %d packed.",
            session.containers.size(), session.groups.size(), session.totalMoves,
            session.placedChests, session.placedShulkers, session.packedShulkers);
    }

    /** Prefers the face looking towards the player, then the first free horizontal face. */
    private Direction findFreeFaceDirection(BlockPos pos) {
        Vec3d player = session.game.playerPos();
        double dx = player.x - (pos.getX() + 0.5);
        double dz = player.z - (pos.getZ() + 0.5);

        Direction preferred = Math.abs(dx) > Math.abs(dz)
            ? (dx > 0 ? Direction.EAST : Direction.WEST)
            : (dz > 0 ? Direction.SOUTH : Direction.NORTH);

        if (canPlaceFrameAt(pos.offset(preferred))) return preferred;

        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (canPlaceFrameAt(pos.offset(direction))) return direction;
        }

        return null;
    }

    /** The air block must be free and must not be inside the player's own body. */
    private boolean canPlaceFrameAt(BlockPos airPos) {
        if (!session.game.isAir(airPos)) return false;
        return !session.game.playerIntersects(airPos);
    }

    private ItemFrameEntity findAnyFrame(BlockPos airPos) {
        Vec3d center = Vec3d.ofCenter(airPos);

        for (ItemFrameEntity frame : session.game.itemFrames()) {
            double dx = frame.getX() - center.x;
            double dy = frame.getY() - center.y;
            double dz = frame.getZ() - center.z;
            if (dx * dx + dy * dy + dz * dz > 1.2) continue;

            return frame;
        }

        return null;
    }

    private ItemFrameEntity findEmptyFrame(BlockPos airPos) {
        ItemFrameEntity frame = findAnyFrame(airPos);
        return frame != null && frame.getHeldItemStack().isEmpty() ? frame : null;
    }

}
