package com.example.addon.modules.stashorganizer;

import java.util.List;

/**
 * The options, messages and lifecycle a session needs, without the Meteor module behind it. The
 * scripts can therefore run against a fake host outside the game.
 */
public interface SessionHost {
    int radius();

    ItemGrouping.Mode mode();

    boolean createsKits();

    boolean unpacksShulkers();

    List<String> protectNames();

    boolean protectSingleItem();

    boolean protectGear();

    List<String> protectItems();

    boolean labelsChests();

    int batch();

    int actionDelay();

    int timeoutTicks();

    boolean isDryRun();

    boolean dumpsInventory();

    boolean allowOverflowShulkers();

    boolean packsShulkers();

    /** Server latency profile used to scale every wait between two actions. */
    default Timings.Latency latency() {
        return Timings.Latency.Normal;
    }

    /** Extra block ids the player wants treated as containers. */
    default List<String> extraContainers() {
        return List.of();
    }

    /** True when any renamed shulker box must be kept shut. */
    default boolean keepsRenamed() {
        return false;
    }

    void info(String message, Object... args);

    void warning(String message, Object... args);

    void error(String message, Object... args);

    void toggle();
}
