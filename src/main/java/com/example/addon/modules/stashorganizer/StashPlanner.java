package com.example.addon.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the item groups from the scanned contents and assigns each group to a chest. With enough
 * chests every group gets its own; otherwise the remaining groups are packed into the free space,
 * and missing chests are planned for placement.
 */
public final class StashPlanner {
    private final StashSession session;

    public StashPlanner(StashSession session) {
        this.session = session;
    }

    public void plan() {
        session.groups.clear();
        session.unassigned.clear();

        collectGroups();

        if (session.groups.isEmpty()) {
            session.finish("Every scanned container is empty, nothing to organise.");
            return;
        }

        for (StashGroup group : session.groups.values()) group.computeSlots();

        List<StashContainer> targets = new ArrayList<>();
        for (StashContainer container : session.containers) {
            if (!container.failed && container.scanned) targets.add(container);
        }

        List<StashGroup> ordered = new ArrayList<>(session.groups.values());
        ordered.sort(Comparator.comparingInt((StashGroup group) -> group.slots).reversed());

        // A filled shulker can still accept items of a stack that is not complete, even when it
        // has no empty slot left. Assign that group so the sorter tops the shulker up instead of
        // looking for another destination.
        assignTopUps(targets);

        List<UnassignedGroup> leftovers = assignDedicated(targets, ordered);
        assignPacked(targets, leftovers);

        int missingSlots = 0;
        for (UnassignedGroup entry : session.unassigned) missingSlots += entry.slots;

        if (session.isDryRun()) {
            report();
            session.report.finish("dry-run");
            session.finish("Dry run: %d groups, %d containers, %d extra slot(s) needed.",
                session.groups.size(), session.containers.size(), missingSlots);
            return;
        }

        int neededContainers = (missingSlots + 26) / 27;
        int availableChests = session.inventory.count(Items.CHEST) + session.inventory.count(Items.TRAPPED_CHEST);
        int availableShulkers = session.allowOverflowShulkers()
            ? (session.packsShulkers() ? session.inventory.countShulkersWithRoom() : session.inventory.countEmptyShulkers())
            : 0;

        if (neededContainers > availableChests + availableShulkers) {
            session.warning("Only %d container(s) available for %d needed; some items will stay unsorted.",
                availableChests + availableShulkers, neededContainers);
        }

        if (session.labelsChests() && session.inventory.count(Items.ITEM_FRAME) < labelledChests(neededContainers)) {
            session.warning("Not enough item frames (%d for %d chests); some containers will stay unlabelled.",
                session.inventory.count(Items.ITEM_FRAME), labelledChests(neededContainers));
        }

        session.nextPhase(neededContainers > 0 ? StashPhase.Place : StashPhase.Sort);
    }

    private void collectGroups() {
        for (StashContainer container : session.containers) {
            if (container.failed) continue;

            container.groups.clear();
            // A filled shulker/chest already uses its occupied slots. Count them so the planner
            // does not promise more free space than the container really has.
            container.reserved = 0;
            for (ItemStack stack : container.contents) if (!stack.isEmpty()) container.reserved++;

            for (ItemStack stack : container.contents) {
                if (stack.isEmpty()) continue;

                // A box that will be unpacked is not an item to store. A protected kit stays shut
                // but must still be sorted into the stash like any other stack.
                if (session.isBoxToUnpack(stack)) continue;

                // Every box that stays shut is listed in the report with the rule that kept it.
                if (stack.isIn(ItemTags.SHULKER_BOXES)) {
                    String customName = session.protection.customName(stack);
                    if (customName != null) session.report.renamed(customName);

                    String reason = session.protection.reason(stack);
                    if (reason != null) session.report.protectedBox(container.pos, stack.getName().getString(), reason);
                }

                StashGroup group = session.groups.computeIfAbsent(session.groupOf(stack), StashGroup::new);
                group.add(stack);
            }
        }
    }

    /**
     * Full containers whose matching stacks are incomplete can still take a few more items. Give
     * them the group without reserving an empty slot; the sorter's real capacity check decides how
     * much actually fits.
     */
    private void assignTopUps(List<StashContainer> targets) {
        for (StashGroup group : session.groups.values()) {
            for (StashContainer container : targets) {
                if (container.size - container.reserved > 0) continue;
                if (container.groups.contains(group.key)) continue;
                if (!hasMatchingPartial(container, group)) continue;

                container.groups.add(group.key);
            }
        }
    }

    private boolean hasMatchingPartial(StashContainer container, StashGroup group) {
        for (ItemStack stack : container.contents) {
            if (stack.isEmpty() || !group.counts.containsKey(stack.getItem())) continue;
            if (stack.getCount() < stack.getMaxCount()) return true;
        }

        return false;
    }

    /**
     * First pass: give every group its own chest, preferring a chest that already holds items of
     * that group so they do not have to move.
     */
    private List<UnassignedGroup> assignDedicated(List<StashContainer> targets, List<StashGroup> ordered) {
        List<UnassignedGroup> leftovers = new ArrayList<>();

        for (StashGroup group : ordered) {
            int remaining = group.slots;

            while (remaining > 0) {
                StashContainer best = null;
                int bestScore = Integer.MIN_VALUE;

                for (StashContainer container : targets) {
                    if (!container.groups.isEmpty()) continue;

                    int free = container.size - container.reserved;
                    if (free <= 0) continue;

                    int matching = 0;
                    for (ItemStack stack : container.contents) {
                        if (!stack.isEmpty() && group.counts.containsKey(stack.getItem())) matching++;
                    }

                    // A chest that already held the group in a previous run is preferred, so a
                    // stash keeps its layout instead of rotating on every run.
                    int score = matching * 1000 + (session.history.isHome(group.key, container.pos) ? 600 : 0) + free;
                    if (score > bestScore) {
                        bestScore = score;
                        best = container;
                    }
                }

                if (best == null) break;

                int give = Math.min(remaining, best.size - best.reserved);
                best.groups.add(group.key);
                best.reserved += give;
                remaining -= give;
            }

            if (remaining > 0) leftovers.add(new UnassignedGroup(group, remaining));
        }

        return leftovers;
    }

    /**
     * Second pass: not enough containers for one chest per group, so pack the remaining groups
     * into the chests that still have room, keeping similar items together.
     */
    private void assignPacked(List<StashContainer> targets, List<UnassignedGroup> leftovers) {
        for (UnassignedGroup entry : leftovers) {
            int remaining = entry.slots;

            while (remaining > 0) {
                StashContainer best = null;
                int bestScore = Integer.MIN_VALUE;

                for (StashContainer container : targets) {
                    int free = container.size - container.reserved;
                    if (free <= 0) continue;

                    int matching = 0;
                    for (ItemStack stack : container.contents) {
                        if (!stack.isEmpty() && entry.group.counts.containsKey(stack.getItem())) matching++;
                    }

                    int score = matching * 1000 + free - container.groups.size() * 20;
                    if (score > bestScore) {
                        bestScore = score;
                        best = container;
                    }
                }

                if (best == null) {
                    session.unassigned.add(new UnassignedGroup(entry.group, remaining));
                    break;
                }

                int give = Math.min(remaining, best.size - best.reserved);
                best.groups.add(entry.group.key);
                best.reserved += give;
                remaining -= give;
            }
        }
    }

    private int labelledChests(int planned) {
        int labelled = 0;

        for (StashContainer container : session.containers) {
            if (!container.failed && !container.groups.isEmpty()) labelled++;
        }

        return labelled + planned;
    }

    private void report() {
        session.info("Plan: %d groups for %d containers.", session.groups.size(), session.containers.size());

        for (StashContainer container : session.containers) {
            if (container.groups.isEmpty()) continue;
            session.info("%s -> %s (%d slots)", container.pos.toShortString(), String.join(", ", container.groups), container.reserved);
        }
    }
}
