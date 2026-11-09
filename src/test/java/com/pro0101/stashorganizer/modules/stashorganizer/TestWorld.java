package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/**
 * Boots the vanilla registries, so a test can build real item stacks and compare groups.
 * <p>
 * The vanilla bootstrap cannot run on a plain test JVM outside Fabric Loader: the Yarn mappings
 * split packages that the vanilla registry code accesses package-privately, so
 * {@code Bootstrap.initialize()} fails with an {@link IllegalAccessError}. Tests that need items
 * therefore skip themselves when this returns false, and run where the game is available.
 */
final class TestWorld {
    private static Boolean bootstrapped;

    private TestWorld() {
    }

    static synchronized boolean bootstrap() {
        if (bootstrapped != null) return bootstrapped;

        try {
            SharedConstants.createGameVersion();
            Bootstrap.initialize();
            bootstrapped = true;
        } catch (Throwable e) {
            bootstrapped = false;
        }

        return bootstrapped;
    }
}
