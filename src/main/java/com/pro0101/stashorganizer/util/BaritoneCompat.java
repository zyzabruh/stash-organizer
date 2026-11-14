package com.pro0101.stashorganizer.util;

import com.pro0101.stashorganizer.StashOrganizerAddon;

/**
 * Keeps the Baritone game-event hook optional.
 *
 * <p>Baritone is a compile-only dependency here, so the addon must still load without it. The
 * listener is referenced by name only: the JVM resolves it when the hook is actually installed,
 * so a jar without Baritone never verifies bytecode that points at classes it cannot find.
 */
public final class BaritoneCompat {
    private static final String API = "baritone.api.BaritoneAPI";
    private static final String ASSIST =
        "com.pro0101.stashorganizer.modules.hyperglide.utilities.BaritoneAssist";

    private BaritoneCompat() {}

    /**
     * Checks whether Baritone is on the classpath.
     *
     * @return true when the Baritone API can be loaded
     */
    public static boolean isAvailable() {
        return present(API);
    }

    /**
     * Registers the assist listener, logging instead of throwing when the hook cannot be set up.
     */
    public static void register() {
        try {
            Class<?> type = Class.forName(ASSIST, true, BaritoneCompat.class.getClassLoader());
            type.getMethod("register").invoke(null);
        } catch (Throwable t) {
            StashOrganizerAddon.LOG.warn("Could not hook the Baritone assist: {}", t.toString());
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, BaritoneCompat.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
