package com.example.addon.modules.stashorganizer;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collect phase: shulker boxes lying on the ground are mined, picked up and stored with the other
 * shulker boxes, so a stash never keeps boxes on its floor.
 * <p>
 * The boxes the organizer itself placed as overflow storage are containers of the plan, not loose
 * blocks: they are skipped here and handled by the packing phase. A collected box keeps whatever
 * it holds, so the unpack phase can still empty the ones that must be opened.
 */
public final class ShulkerCollector {
    private static final int WALK = 0;
    private static final int TOOL = 1;
    private static final int MINE = 2;
    private static final int SETTLE = 3;
    private static final int PICK_UP = 4;
    private static final int STORE = 5;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::walkToTarget, this::prepareTool, this::mine, this::settleAfterBreak, this::pickUp, this::store);

    private final List<BlockPos> targets = new ArrayList<>();
    private boolean discovered;
    private int index;
    private int wait;
    private int breakTicks;
    private int pickupTicks;
    private boolean movedForPickup;
    private int collected;

    private BlockPos target;
    private Map<Integer, ItemStack> beforeBreak;
    private int shulkerSlot = -1;

    public ShulkerCollector(StashSession session) {
        this.session = session;
    }

    public String progress() {
        return Math.min(index, targets.size()) + "/" + targets.size();
    }

    /** The box currently being mined, or null between two boxes. */
    public BlockPos currentTarget() {
        return target;
    }

    /** Completion of the phase in {@code [0, 1]}, or -1 before the boxes are known. */
    public double fraction() {
        return targets.isEmpty() ? -1 : Math.min(1, (double) index / targets.size());
    }

    public void tick() {
        if (session.sorter.isDelivering()) {
            session.sorter.deliverTick();
            return;
        }

        if (!discovered) {
            discover();
            discovered = true;
        }

        if (index >= targets.size()) {
            if (!session.carried.isEmpty()) {
                session.sorter.startDelivery();
                return;
            }

            if (collected > 0) session.info("Collected and stored %d shulker(s) lying on the ground.", collected);

            session.nextPhase(session.collectTarget());
            return;
        }

        target = targets.get(index);

        if (!session.game.isShulkerAt(target)) {
            advance();
            return;
        }

        script.run();
    }

    private StepResult walkToTarget() {
        Navigator.Result navigation = session.navigator.walkToDetailed(target);

        if (navigation == Navigator.Result.ARRIVED) {
            session.act();
            return StepResult.jumpTo(TOOL);
        }

        if (navigation == Navigator.Result.FAILED) {
            skip("Could not reach the shulker box at %s; it stays on the floor.", target.toShortString());
        }

        return StepResult.RUNNING;
    }

    private StepResult prepareTool() {
        if (session.inventory.freeSlots() < 1
            || (!session.carried.isEmpty() && session.carried.size() >= session.batch())) {
            if (session.carried.isEmpty()) {
                skip("No free inventory slot to pick up the shulker box at %s; it stays on the floor.", target.toShortString());
                return StepResult.RUNNING;
            }

            // Stays on this step: the box is stored once the buffer has been delivered.
            session.sorter.startDelivery();
            return StepResult.RUNNING;
        }

        // A pickaxe mines the box instantly; the hand works too, only slower.
        session.inventory.ensurePickaxe();

        beforeBreak = session.inventory.snapshot();
        breakTicks = 0;
        pickupTicks = 0;
        movedForPickup = false;
        session.act();
        return StepResult.jumpTo(MINE);
    }

    private StepResult mine() {
        if (!session.game.isShulkerAt(target)) {
            wait = session.timings.breakSettle();
            movedForPickup = false;
            return StepResult.jumpTo(SETTLE);
        }

        if (++breakTicks > session.timeoutTicks()) {
            skip("Could not mine the shulker box at %s; it stays on the floor.", target.toShortString());
            return StepResult.RUNNING;
        }

        // The client keeps the breaking progress; calling it every tick is required, so the
        // global action delay is deliberately not reset here.
        session.game.lookAt(Vec3d.ofCenter(target), 100);
        session.game.breakBlock(target);
        return StepResult.RUNNING;
    }

    private StepResult settleAfterBreak() {
        if (session.game.isShulkerAt(target)) {
            return StepResult.jumpTo(MINE);
        }

        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        wait = session.timings.pickupRetry();
        movedForPickup = false;
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
            // The block is air now, so standing on its spot collects the dropped box.
            session.mover.moveToStanding(target);
            wait = session.timings.pickupMove();
            return StepResult.RUNNING;
        }

        shulkerSlot = session.inventory.findNewShulker(beforeBreak);
        beforeBreak = null;

        if (shulkerSlot >= 0) {
            session.act();
            return StepResult.jumpTo(STORE);
        }

        if (++pickupTicks > session.timings.maxPickupTries()) {
            skip("The shulker box at %s was not picked up (is the inventory full?); it stays on the floor.",
                target.toShortString());
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

        session.carried.put(shulkerSlot, new Carried(stack.getItem(), session.groupOf(stack)));
        collected++;
        advance();

        if (session.carried.size() >= session.batch() || session.inventory.freeSlots() <= 3) {
            session.sorter.startDelivery();
        }

        return StepResult.RUNNING;
    }

    /**
     * Loose shulker blocks: around the player (a box dropped by hand) and around every known
     * container (a box left behind by an interrupted unpack). Scanning those small volumes keeps
     * the search off the main thread's critical path, unlike a full radius cube.
     */
    private void discover() {
        targets.clear();

        Set<BlockPos> seen = new HashSet<>();
        BlockPos center = session.game.playerFeet();

        scan(seen, center, Math.min(session.radius(), 12));

        for (StashContainer container : session.containers) {
            if (!container.failed) scan(seen, container.pos, 4);
        }

        targets.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(center)));
    }

    private void scan(Set<BlockPos> seen, BlockPos center, int radius) {
        for (BlockPos pos : session.game.around(center, radius)) {
            BlockPos immutable = pos.toImmutable();
            if (!seen.add(immutable)) continue;
            if (!session.game.isShulkerAt(immutable)) continue;

            // An overflow shulker placed by the plan is a container, not a box left on the floor.
            if (session.access.at(immutable) != null) continue;

            targets.add(immutable);
        }
    }

    private void skip(String message, Object... args) {
        session.warning(message, args);
        advance();
    }

    private void advance() {
        index++;
        wait = 0;
        breakTicks = 0;
        pickupTicks = 0;
        movedForPickup = false;
        shulkerSlot = -1;
        target = null;
        script.jumpTo(WALK);
        session.act();
    }
}
