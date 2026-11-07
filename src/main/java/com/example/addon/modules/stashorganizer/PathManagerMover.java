package com.example.addon.modules.stashorganizer;

import meteordevelopment.meteorclient.pathing.IPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import net.minecraft.util.math.BlockPos;

/**
 * Fallback mover that goes through Meteor's path manager. Meteor resolves this to Baritone when it
 * is installed, or to Voyager, and returns a no-op manager when no pathing mod is present.
 */
public final class PathManagerMover implements StashMover {
    private final IPathManager pathManager = PathManagers.get();

    @Override
    public boolean isAvailable() {
        return !"none".equals(pathManager.getName());
    }

    @Override
    public void moveTo(BlockPos pos) {
        pathManager.moveTo(pos);
    }

    @Override
    public void stop() {
        pathManager.stop();
    }

    @Override
    public boolean isPathing() {
        return pathManager.isPathing();
    }

    public String getName() {
        return pathManager.getName();
    }
}
