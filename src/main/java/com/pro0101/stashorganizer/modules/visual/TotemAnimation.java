package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Speeds up or slows down the totem pop animation.
 *
 * <p>The animation is driven by a counter the overlay ticks once per frame; the module adds extra
 * ticks to it, so the item spins and shrinks faster. A speed of one leaves the vanilla timing, and
 * zero freezes the animation on its first frame.
 */
public class TotemAnimation extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Integer> speed = this.general.add(new IntSetting.Builder()
        .name("speed")
        .description("Extra animation ticks per frame. Zero keeps the vanilla speed.")
        .defaultValue(1)
        .range(0, 8)
        .build()
    );

    public TotemAnimation() {
        super(StashOrganizerAddon.VISUALS, "totem-animation", "Speeds up the totem pop animation.");
    }

    /**
     * Returns the extra ticks to add each frame.
     *
     * @return extra ticks
     */
    public int extraTicks() {
        return this.speed.get();
    }
}
