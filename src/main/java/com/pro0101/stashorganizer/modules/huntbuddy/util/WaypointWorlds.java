package com.pro0101.stashorganizer.modules.huntbuddy.util;

import net.minecraft.world.World;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * The part of HunterBuddy's WaypointFollower that ChunkRadar actually calls, lifted out on its own so the radar does not
 * drag the whole follower, its waypoint package and the AreaLoader behind it.
 */
public final class WaypointWorlds {
    private WaypointWorlds() {}

    /** The Xaero minimap world that holds the overworld waypoints, or null when Xaero has none yet. */
    public static MinimapWorld overworldWaypointWorld() {
        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) return null;

            MinimapWorldManager manager = session.getWorldManager();
            if (manager == null) return null;

            MinimapWorld current = manager.getCurrentWorld();
            if (current != null && current.getDimId() == World.OVERWORLD) return current;

            MinimapWorld auto = manager.getAutoWorld();
            if (auto != null && auto.getDimId() == World.OVERWORLD) return auto;

            MinimapWorldRootContainer root = manager.getCurrentRootContainer();
            if (root != null) {
                for (MinimapWorld world : root.getWorlds()) {
                    if (world.getDimId() == World.OVERWORLD) return world;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
