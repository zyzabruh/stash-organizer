package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds every supported storage block in range and opens it to read its contents. */
public final class StashScanner {
    private static final int RESET = 0;
    private static final int OPEN = 1;

    private final StashSession session;
    private final StepScript script = new StepScript(this::resetTarget, this::openTarget);

    private boolean rescan;
    private BlockPos candidate;

    public StashScanner(StashSession session) {
        this.session = session;
    }

    /** Prepares a pass over the containers of a loaded plan, without planning again. */
    public void beginRescan() {
        rescan = true;
        script.jumpTo(RESET);
        session.scanQueue.clear();
        session.shulkers.clear();

        for (StashContainer container : session.containers) {
            if (!container.failed) session.scanQueue.add(container.pos);
        }
    }

    /** @return false when no storage block was found */
    public boolean discover() {
        BlockPos center = session.game.playerFeet();
        int radius = session.radius();

        List<BlockPos> found = new ArrayList<>();

        for (BlockPos pos : session.game.around(center, radius)) {
            Block block = session.game.blockAt(pos);
            if (!ContainerTypes.isContainer(block, session.extraContainers(), session.excludesEnderChest())) continue;
            // A shulker box standing on the floor is not storage: the collect phase mines it and
            // stores it with the other shulker boxes instead of filling it.
            if (ContainerTypes.isShulker(block)) continue;

            found.add(pos.toImmutable());
        }

        found.sort(Comparator.comparingDouble(session.game::squaredDistanceTo));
        session.scanQueue.addAll(found);

        return !found.isEmpty();
    }

    /** The container the scan is currently working on, or null between two containers. */
    public BlockPos currentTarget() {
        return candidate;
    }

    public void tick() {
        if (session.scanQueue.isEmpty()) {
            if (session.containers.isEmpty()) {
                session.stop("No container could be opened.");
                return;
            }

            boolean readable = session.containers.stream().anyMatch(c -> !c.failed);

            if (!readable) {
                if (rescan) session.pause("No saved container could be re-read; the plan is kept for a retry.");
                else session.stop("No container could be opened.");
                return;
            }

            if (rescan) {
                rescan = false;
                session.resuming = false;
                session.info("Refreshed %d containers, resuming the saved phase.", session.containers.size());

                // The stacks restored from the saved plan are delivered before the phase goes on.
                if (!session.carried.isEmpty()) session.sorter.startDelivery();

                // A box left on the floor by an interrupted run is collected first, so the resumed
                // run never leaves it lying next to the stash.
                session.nextPhase(session.resumeAfterCollect != null ? StashPhase.Collect : session.resumePhase);
                return;
            }

            session.info("Scanned %d containers, found %d shulker boxes.", session.containers.size(), session.shulkers.size());
            session.planner.plan();
            return;
        }

        candidate = session.scanQueue.peek();
        boolean chunkLoaded = session.game.chunkLoaded(candidate);

        if (!session.game.isContainerAt(candidate) || excluded(candidate)) {
            // On a resume the chunk may not be loaded yet: walk there so it loads instead of
            // marking a chest as lost.
            if (rescan && !chunkLoaded) {
                Navigator.Result navigation = session.navigator.walkToDetailed(candidate);
                if (navigation != Navigator.Result.FAILED) return;

                session.warning("Could not reach %s to reload its chunk; skipping it.", candidate.toShortString());
            }

            StashContainer existing = session.access.at(candidate);
            if (existing != null) existing.failed = true;

            session.scanQueue.poll();
            return;
        }

        if (!rescan && session.access.at(candidate) != null) {
            session.scanQueue.poll();
            return;
        }

        Navigator.Result navigation = session.navigator.walkToDetailed(candidate);

        if (navigation == Navigator.Result.MOVING) return;

        if (navigation == Navigator.Result.FAILED) {
            StashContainer failed = session.access.at(candidate);
            if (failed != null) failed.failed = true;

            skipCandidate();
            return;
        }

        script.run();
    }

    /** A storage block the settings exclude: it must never be opened nor counted as a container. */
    private boolean excluded(BlockPos pos) {
        return session.excludesEnderChest() && ContainerTypes.isEnderChest(session.game.blockAt(pos));
    }

    private StepResult resetTarget() {
        session.access.resetOpen();
        return StepResult.DONE;
    }

    private StepResult openTarget() {
        int result = session.access.open(candidate);

        if (result == 1) {
            session.access.read(candidate, true);
            session.access.close();
            skipCandidate();
        } else if (result == -1) {
            StashContainer failed = session.access.at(candidate);
            if (failed != null) failed.failed = true;

            skipCandidate();
        }

        return StepResult.RUNNING;
    }

    /** The container cannot be read: forget it and start over on the next one. */
    private void skipCandidate() {
        script.jumpTo(RESET);
        session.scanQueue.poll();
        session.act();
    }

}
