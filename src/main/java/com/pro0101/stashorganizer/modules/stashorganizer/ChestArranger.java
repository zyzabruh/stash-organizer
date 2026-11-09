package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tidying phase: the sort puts every stack in the right chest but not in a readable order, because
 * a shift-clicked stack lands in the first free slot. This pass swaps slots so the stacks of one
 * item sit together, the items follow the order of the chest's planned groups and the empty slots
 * end up at the bottom.
 * <p>
 * Only the chests whose cached contents are already out of order are visited, and one swap is three
 * clicks on the open screen: pick the out of place stack up, drop it in the other slot (the stack
 * that was there goes to the cursor), then put that one in the slot the first stack left. Every
 * swap is checked against the screen afterwards, so a server that refuses the moves stops the pass
 * instead of being hammered.
 */
public final class ChestArranger {
    private static final int SELECT = 0;
    private static final int WALK = 1;
    private static final int OPEN = 2;
    private static final int PLAN = 3;
    private static final int SWAP = 4;
    private static final int CLOSE = 5;
    /** A server that refuses this many moves in a row will not accept the next ones either. */
    private static final int MAX_REFUSED = 5;
    /** Clicks of one swap: the stack up, then into the other slot, then that one back. */
    private static final int SWAP_CLICKS = 3;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::selectContainer, this::walkToContainer, this::openContainer, this::planLayout,
        this::swapStep, this::closeContainer);

    private final List<StashContainer> targets = new ArrayList<>();
    private boolean discovered;
    private int index;
    private int chests;
    private int moved;
    private int wait;
    private int refused;
    private boolean warned;

    private StashContainer target;
    private List<SlotLayout.Swap> swaps = List.of();
    private int swapIndex;
    private int clickStage;
    private SlotLayout.Swap pending;
    private Item pendingItem;

    public ChestArranger(StashSession session) {
        this.session = session;
    }

    public String progress() {
        return Math.min(index, targets.size()) + "/" + targets.size();
    }

    /** The chest being tidied, or null between two chests. */
    public BlockPos currentTarget() {
        return target == null ? null : target.pos;
    }

    /** Completion of the phase in {@code [0, 1]}, or -1 before the chests are known. */
    public double fraction() {
        return targets.isEmpty() ? -1 : Math.min(1, (double) index / targets.size());
    }

    public void tick() {
        if (!session.arrangesChests()) {
            session.nextPhase(StashPhase.Label);
            return;
        }

        if (!discovered) {
            discover();
            discovered = true;
        }

        if (index >= targets.size()) {
            finish();
            return;
        }

        script.run();
    }

    /** Every container of the plan; the ones already in order are dropped without walking to them. */
    private void discover() {
        targets.clear();

        for (StashContainer container : session.containers) {
            if (container.failed || !container.scanned) continue;
            targets.add(container);
        }
    }

    private StepResult selectContainer() {
        target = null;

        while (index < targets.size()) {
            StashContainer candidate = targets.get(index);

            if (candidate.failed || !candidate.scanned || !needsWork(candidate)) {
                index++;
                continue;
            }

            target = candidate;
            break;
        }

        if (target == null) {
            finish();
            return StepResult.RUNNING;
        }

        session.access.resetOpen();
        session.act();
        return StepResult.jumpTo(WALK);
    }

    /** True when the cached contents are not in their tidied order yet. */
    private boolean needsWork(StashContainer container) {
        return !SlotLayout.swaps(container.contents, order(container, container.contents)).isEmpty();
    }

    private StepResult walkToContainer() {
        Navigator.Result navigation = session.navigator.walkToDetailed(target.pos);

        if (navigation == Navigator.Result.ARRIVED) {
            session.access.resetOpen();
            return StepResult.jumpTo(OPEN);
        }

        if (navigation == Navigator.Result.FAILED) {
            skip("Could not reach %s to tidy it.", target.pos.toShortString());
        }

        return StepResult.RUNNING;
    }

    private StepResult openContainer() {
        int result = session.access.open(target.pos);

        if (result == 1) {
            session.access.read(target.pos, false);
            session.act();
            return StepResult.jumpTo(PLAN);
        }

        if (result == -1) {
            skip("Could not open %s to tidy it.", target.pos.toShortString());
        }

        return StepResult.RUNNING;
    }

    /**
     * Builds the layout from the live screen, not from the cache: a chest read before the last
     * delivery would move the wrong slots.
     */
    private StepResult planLayout() {
        List<ItemStack> contents = session.access.liveContents();

        if (contents.isEmpty()) {
            skip("The container at %s has no readable slots.", target.pos.toShortString());
            return StepResult.RUNNING;
        }

        swaps = SlotLayout.swaps(contents, order(target, contents));
        swapIndex = 0;
        clickStage = 0;
        refused = 0;
        pending = null;
        pendingItem = null;
        session.act();
        return StepResult.jumpTo(SWAP);
    }

    private StepResult swapStep() {
        if (wait > 0) {
            wait--;
            return StepResult.RUNNING;
        }

        StepResult verified = verifySwap();
        if (verified != null) return verified;

        if (swapIndex >= swaps.size()) return StepResult.jumpTo(CLOSE);
        if (!session.access.isOpen()) return StepResult.jumpTo(OPEN);

        SlotLayout.Swap swap = swaps.get(swapIndex);
        int from = session.access.liveSlotId(swap.from());
        int to = session.access.liveSlotId(swap.to());

        if (from < 0 || to < 0) {
            skipSwap();
            return StepResult.RUNNING;
        }

        if (clickStage == 0) pendingItem = session.access.liveStack(swap.from()).getItem();

        session.access.click(clickStage == 1 ? to : from, 0, SlotActionType.PICKUP);
        session.act();
        wait = session.timings.slotClick();

        if (++clickStage < SWAP_CLICKS) return StepResult.RUNNING;

        clickStage = 0;
        swapIndex++;
        pending = swap;
        return StepResult.RUNNING;
    }

    /**
     * Checks the swap that was just clicked against the screen.
     *
     * @return a step to jump to when the pass has to stop, or null to keep going
     */
    private StepResult verifySwap() {
        if (pending == null) return null;

        SlotLayout.Swap done = pending;
        Item movedItem = pendingItem;
        pending = null;
        pendingItem = null;

        if (session.access.liveStack(done.from()).getItem() != movedItem) {
            refused = 0;
            moved++;
            return null;
        }

        // The slot still holds the stack that should have left: the move did not happen.
        skipSwap();

        if (++refused < MAX_REFUSED) return null;

        if (!warned) {
            warned = true;
            session.warning("The server refuses the slot moves; %s stays as it is.", target.pos.toShortString());
        }

        return StepResult.jumpTo(CLOSE);
    }

    private void skipSwap() {
        clickStage = 0;
        swapIndex++;
    }

    private StepResult closeContainer() {
        // The cache follows the new order, so the labelling sees the same chest the player does.
        session.access.read(target.pos, false);
        session.access.close();

        if (!swaps.isEmpty()) chests++;

        swaps = List.of();
        swapIndex = 0;
        clickStage = 0;
        pending = null;
        pendingItem = null;
        target = null;
        session.act();
        index++;
        script.jumpTo(SELECT);
        return StepResult.RUNNING;
    }

    private void skip(String message, Object... args) {
        if (session.access.isOpen()) session.access.close();

        session.warning(message, args);
        target = null;
        index++;
        script.jumpTo(SELECT);
        session.act();
    }

    private void finish() {
        if (chests > 0) {
            session.info("Tidied %d chest(s), %d slot move(s).", chests, moved);
        }

        session.nextPhase(StashPhase.Label);
    }

    /**
     * Order of the items inside one chest: the chest's planned groups first, in the order the plan
     * gave them, then the items the plan did not name, then the empty slots. Two items of one group
     * therefore sit next to each other, and the item id breaks the ties so the layout is the same
     * from one run to the next.
     */
    private Comparator<ItemStack> order(StashContainer container, List<ItemStack> contents) {
        List<String> groups = new ArrayList<>(container.groups);
        Map<Item, Integer> ranks = new HashMap<>();
        int unknown = groups.size() + 1;

        for (ItemStack stack : contents) {
            if (stack.isEmpty() || ranks.containsKey(stack.getItem())) continue;

            ranks.put(stack.getItem(), Math.max(0, groups.indexOf(session.groupOf(stack))));
        }

        return Comparator
            .comparingInt((ItemStack stack) -> ranks.getOrDefault(stack.getItem(), unknown))
            .thenComparing(stack -> Registries.ITEM.getId(stack.getItem()).toString());
    }
}
