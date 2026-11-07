package com.example.addon.modules.stashorganizer;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import net.minecraft.util.math.BlockPos;

/**
 * Direct Baritone integration. This class references Baritone classes, so it is only loaded when
 * {@code baritone.api.BaritoneAPI} is present (see {@code StashOrganizer#createMover()}).
 * <p>
 * While the organizer runs, Baritone is not allowed to break or place blocks: walking to a chest
 * must never damage the stash.
 */
public final class BaritoneMover implements StashMover {
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    private Boolean previousAllowBreak;
    private Boolean previousAllowPlace;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void prepare() {
        Settings settings = BaritoneAPI.getSettings();

        previousAllowBreak = settings.allowBreak.value;
        previousAllowPlace = settings.allowPlace.value;

        settings.allowBreak.value = false;
        settings.allowPlace.value = false;
    }

    @Override
    public void shutdown() {
        Settings settings = BaritoneAPI.getSettings();

        if (previousAllowBreak != null) settings.allowBreak.value = previousAllowBreak;
        if (previousAllowPlace != null) settings.allowPlace.value = previousAllowPlace;

        previousAllowBreak = null;
        previousAllowPlace = null;
        stop();
    }

    @Override
    public void moveTo(BlockPos pos) {
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
    }

    @Override
    public void moveToStanding(BlockPos pos) {
        // GoalGetToBlock accepts any adjacent block, which is too loose for a stand spot: Baritone
        // could stop one block away and the chest would still be out of reach.
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
    }

    @Override
    public void stop() {
        baritone.getPathingBehavior().cancelEverything();
    }

    @Override
    public boolean isPathing() {
        return baritone.getPathingBehavior().isPathing();
    }
}
