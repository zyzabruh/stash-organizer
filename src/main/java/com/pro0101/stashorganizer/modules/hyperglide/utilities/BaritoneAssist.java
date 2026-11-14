package com.pro0101.stashorganizer.modules.hyperglide.utilities;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;

/**
 * Feeds the Baritone assist on every player update, the way the Hyperglide addon did.
 *
 * <p>AutoPilot hands its one-block climbing steps to Baritone. Without this hook Baritone starts a
 * step before the player is actually above the block it is stepping from, so the path fights the
 * player every step: that is the stutter that made the elytra travel feel broken. Pausing Baritone
 * for a tick, lining the player up, then resuming removes it.
 *
 * <p>The class is only loaded when Baritone is installed: {@code BaritoneCompat} checks the API
 * first, so an addon without Baritone never verifies this bytecode.
 */
public final class BaritoneAssist implements AbstractGameEventListener {
    private BaritoneAssist() {}

    /**
     * Registers the listener on the primary Baritone instance.
     */
    public static void register() {
        BaritoneAPI.getProvider().getPrimaryBaritone()
            .getGameEventHandler().registerEventListener(new BaritoneAssist());
    }

    /**
     * Aligns the player before Baritone follows its movement.
     *
     * @param event Baritone player update event
     */
    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (event.getState() == EventState.PRE) Baritone.assist();
    }
}
