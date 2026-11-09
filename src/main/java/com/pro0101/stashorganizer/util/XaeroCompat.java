package com.pro0101.stashorganizer.util;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Keeps the modules built against Xaero's Minimap, Xaero's World Map and XaeroPlus optional.
 *
 * <p>They are referenced by name only: the JVM resolves those classes when they are actually loaded, so an addon with
 * Xaero missing never has to verify bytecode that points at classes it cannot find, and simply does not register the
 * modules that would be unusable.
 */
public final class XaeroCompat {
    private static final String MINIMAP = "xaero.hud.minimap.BuiltInHudModules";
    private static final String WORLD_MAP = "xaero.map.mods.SupportMods";
    private static final String XAERO_PLUS = "xaeroplus.XaeroPlus";

    private XaeroCompat() {}

    /** True when every Xaero dependency the radar and the trail follower need is on the classpath. */
    public static boolean isAvailable() {
        return present(MINIMAP) && present(WORLD_MAP) && present(XAERO_PLUS);
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, XaeroCompat.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Instantiates a module by name and hands it to Meteor; a failure is logged instead of thrown. */
    public static void register(String className) {
        try {
            Class<?> type = Class.forName(className, true, XaeroCompat.class.getClassLoader());
            Modules.get().add((Module) type.getDeclaredConstructor().newInstance());
        } catch (Throwable t) {
            StashOrganizerAddon.LOG.warn("Could not register module {}: {}", className, t.toString());
        }
    }
}
