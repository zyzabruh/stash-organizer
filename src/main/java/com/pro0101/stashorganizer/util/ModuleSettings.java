package com.pro0101.stashorganizer.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Typed access to another module's settings.
 *
 * <p>Meteor hands back {@code Setting<?>}, so every reader used to cast at the call site. Routing those reads through
 * one named method keeps the unchecked cast in a single documented place instead of scattered through the flight code.
 */
public final class ModuleSettings {
    private ModuleSettings() {}

    @SuppressWarnings("unchecked")
    public static <T> Setting<T> get(Module module, String name) {
        return (Setting<T>) module.settings.get(name);
    }
}
