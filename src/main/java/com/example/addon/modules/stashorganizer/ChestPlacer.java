package com.example.addon.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;

/**
 * Places the chests needed by the plan (and any extra chest required for a group discovered later)
 * on free floor space next to the stash.
 */
public final class ChestPlacer {
    private static final int FIND = 0;
    private static final int WALK = 1;
    private static final int PLACE = 2;
    private static final int VERIFY = 3;

    private final StashSession session;
    private final StepScript script = new StepScript(
        this::findSpot, this::walkToSpot, this::placeBlock, this::verifyPlacement);

    private BlockPos spot;
    private StashContainer lastPlaced;
    private boolean placingPacking;
    /** Null until a placement starts: the empty stack constant needs the game registries. */
    private ItemStack placingStack;
    private boolean warnedNoPickaxe;
    private int failedPlacements;
    private int placeWait;

    /** Arguments of the current {@link #placeOneChest} call, used by the steps over several ticks. */
    private String group;
    private boolean staging;
    private int result;

    public ChestPlacer(StashSession session) {
        this.session = session;
    }

    /** Placement phase: one chest per tick until the plan has enough room. */
    public void tickPhase() {
        if (session.unassigned.isEmpty()) {
            session.nextPhase(StashPhase.Sort);
            return;
        }

        int placed = placeOneChest(null);

        if (placed == -1) {
            session.warning("%d group(s) have no container left; they will stay unsorted.", session.unassigned.size());
            session.nextPhase(StashPhase.Sort);
            return;
        }

        if (placed == 1) {
            fillNewContainer(lastPlaced);
            markPlannedPacking(lastPlaced);
            session.persist();
            session.act();
        }
    }

    /**
     * Places one chest. Runs over several ticks.
     *
     * @param group group to pre-assign to the new chest, or null for the overflow plan
     * @return 0 = working, 1 = placed, -1 = failed
     */
    public int placeOneChest(String group) {
        return placeOneChest(group, false);
    }

    public int placeOneChest(String group, boolean staging) {
        this.group = group;
        this.staging = staging;
        this.result = 0;
        script.run();
        return result;
    }

    private StepResult findSpot() {
        spot = findFreeSpot();

        if (spot == null) {
            result = -1;
            return StepResult.RUNNING;
        }

        return StepResult.jumpTo(WALK);
    }

    private StepResult walkToSpot() {
        Navigator.Result navigation = session.navigator.walkToDetailed(spot);

        if (navigation == Navigator.Result.MOVING) return StepResult.RUNNING;

        if (navigation == Navigator.Result.FAILED) {
            resetSpot();

            if (++failedPlacements < 3) return StepResult.RUNNING;

            failedPlacements = 0;
            result = -1;
            return StepResult.RUNNING;
        }

        return StepResult.jumpTo(PLACE);
    }

    private StepResult placeBlock() {
        boolean pack = !staging && session.packsShulkers() && session.inventory.hasPickaxe();
        ItemStack placement = placementItem(group, pack);
        placingPacking = pack;
        placingStack = placement;

        if (placement.isEmpty()) {
            resetSpot();
            result = -1;
            return StepResult.RUNNING;
        }

        int hotbar = placement.isIn(ItemTags.SHULKER_BOXES)
            ? session.inventory.ensureInHotbarStack(placement)
            : session.inventory.ensureInHotbar(placement.getItem());

        if (hotbar < 0) {
            resetSpot();
            result = -1;
            return StepResult.RUNNING;
        }

        // The game rotates towards the placement so the chest faces the player instead of the wall.
        if (!session.game.place(spot, placement)) {
            resetSpot();

            if (++failedPlacements < 3) return StepResult.RUNNING;

            failedPlacements = 0;
            result = -1;
            return StepResult.RUNNING;
        }

        // BlockUtils.place rotates asynchronously: wait for the block to exist.
        placeWait = session.timings.blockPlace();
        return StepResult.jumpTo(VERIFY);
    }

    private StepResult verifyPlacement() {
        if (placeWait > 0) {
            placeWait--;
            return StepResult.RUNNING;
        }

        Block block = session.game.blockAt(spot);

        if (!ContainerTypes.isContainer(block, session.extraContainers())) {
            resetSpot();

            if (++failedPlacements < 3) return StepResult.RUNNING;

            failedPlacements = 0;
            result = -1;
            return StepResult.RUNNING;
        }

        StashContainer container = new StashContainer(spot, block);
        container.scanned = true;
        container.size = 27;
        container.clearCache();

        int existingStacks = block instanceof ShulkerBoxBlock ? InventoryTools.contentsOf(placing()).size() : 0;

        if (block instanceof ShulkerBoxBlock && placingPacking) {
            container.packing = true;

            // A partially filled shulker already holds items that belong to their own
            // groups; keep those groups so the sorter tops the shulker up instead of
            // pulling its contents out again.
            for (ItemStack existing : InventoryTools.contentsOf(placing())) {
                container.groups.add(session.groupOf(existing));
            }
        }

        // Existing contents already take slots in the shulker; only the remaining slots
        // may be planned for new groups.
        container.reserved = existingStacks;

        if (group != null) {
            container.groups.add(group);
            container.reserved = existingStacks + 1;
        }

        session.containers.add(container);

        if (block instanceof ShulkerBoxBlock) session.placedShulkers++;
        else session.placedChests++;

        lastPlaced = container;
        failedPlacements = 0;
        result = 1;
        resetSpot();
        session.persist();
        return StepResult.RUNNING;
    }

    /** A chest when one is available, otherwise a shulker box for overflow storage. */
    private ItemStack placementItem(String group, boolean pack) {
        if (session.inventory.count(Items.CHEST) > 0) return new ItemStack(Items.CHEST);
        if (session.inventory.count(Items.TRAPPED_CHEST) > 0) return new ItemStack(Items.TRAPPED_CHEST);

        if (!session.allowOverflowShulkers()) return ItemStack.EMPTY;

        if (pack) {
            Item groupItem = Items.AIR;
            StashGroup stashGroup = group == null ? null : session.groups.get(group);
            if (stashGroup != null && !stashGroup.counts.isEmpty()) groupItem = stashGroup.counts.keySet().iterator().next();

            int slot = groupItem == Items.AIR
                ? session.inventory.findShulkerWithFreeSlot(session.protection)
                : session.inventory.findShulkerWithRoom(groupItem, session.protection);

            if (slot >= 0) return session.inventory.stack(slot).copy();

            return ItemStack.EMPTY;
        }

        Item empty = session.inventory.findEmptyShulkerItem(session.protection);
        return empty == null ? ItemStack.EMPTY : new ItemStack(empty);
    }

    /** Planned overflow groups start with a fresh shulker; mark it for the packing phase. */
    private void markPlannedPacking(StashContainer container) {
        if (!(container.block instanceof ShulkerBoxBlock)) return;
        if (!session.packsShulkers()) return;

        if (!session.inventory.hasPickaxe()) {
            if (!warnedNoPickaxe) {
                warnedNoPickaxe = true;
                session.warning("No pickaxe found: overflow shulkers will stay placed instead of being packed.");
            }

            return;
        }

        container.packing = true;
    }

    public StashContainer lastPlaced() {
        return lastPlaced;
    }

    /** The spot the placer is working on, or null between two placements. */
    public BlockPos currentTarget() {
        return spot;
    }

    /** The stack being placed, or an empty stack before the first placement. */
    private ItemStack placing() {
        return placingStack == null ? ItemStack.EMPTY : placingStack;
    }

    private void resetSpot() {
        script.jumpTo(FIND);
        spot = null;
    }

    private void fillNewContainer(StashContainer container) {
        while (!session.unassigned.isEmpty()) {
            UnassignedGroup entry = session.unassigned.get(0);
            int give = Math.min(entry.slots, container.size - container.reserved);
            if (give <= 0) return;

            container.groups.add(entry.group.key);
            container.reserved += give;
            entry.slots -= give;

            if (entry.slots <= 0) session.unassigned.remove(0);
        }
    }

    private BlockPos findFreeSpot() {
        BlockPos center = stashCenter();

        for (int radius = 2; radius <= 12; radius++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                        BlockPos pos = center.add(dx, dy, dz);
                        if (!session.spots.canPlaceAt(pos)) continue;
                        // A chest needs a free face for its item frame and must not touch another
                        // container, otherwise its lid cannot open.
                        if (!session.spots.hasFreeHorizontalFace(pos)) continue;
                        if (session.spots.touchesContainer(pos)) continue;

                        return pos;
                    }
                }
            }
        }

        return null;
    }

    public BlockPos stashCenter() {
        int x = 0;
        int y = 0;
        int z = 0;
        int n = 0;

        for (StashContainer container : session.containers) {
            x += container.pos.getX();
            y += container.pos.getY();
            z += container.pos.getZ();
            n++;
        }

        if (n == 0) return session.game.playerFeet();
        return new BlockPos(x / n, y / n, z / n);
    }
}
