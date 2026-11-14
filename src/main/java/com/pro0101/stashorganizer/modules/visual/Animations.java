package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Changes how the hand swings and how long a swing lasts.
 *
 * <p>The duration comes from the held item itself, so the module scales whatever the game would have
 * used instead of replacing it: a sword keeps its own timing, only stretched or shortened. The value
 * is read at the moment the swing starts, so changing it takes effect on the next swing.
 *
 * <p>The swing itself is a short sequence of rotations the game applies to the item before drawing
 * it. Replacing those rotations changes the whole motion without touching the item, its model or the
 * equipment offset that positions it.
 */
public class Animations extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Swing> swing = this.general.add(new EnumSetting.Builder<Swing>()
        .name("swing")
        .description("Which swing motion to use. Vanilla keeps the game's own animation.")
        .defaultValue(Swing.Vanilla)
        .build()
    );

    private final Setting<Double> multiplier = this.general.add(new DoubleSetting.Builder()
        .name("swing-duration")
        .description("Multiplier applied to the hand swing duration. Below one speeds the swing up, above one slows it down.")
        .defaultValue(1.0)
        .range(0.25, 4.0)
        .sliderRange(0.5, 2.0)
        .build()
    );

    public Animations() {
        super(StashOrganizerAddon.VISUALS, "animations", "Replaces the hand swing motion and scales how long a swing lasts.");
    }

    /**
     * Returns the multiplier applied to the swing duration.
     *
     * @return multiplier, clamped away from zero
     */
    public double multiplier() {
        return Math.max(0.05, this.multiplier.get());
    }

    /**
     * Returns the swing motion to draw.
     *
     * @return selected swing
     */
    public Swing swing() {
        return this.swing.get();
    }

    /** Motion applied to the held item during a swing. */
    public enum Swing {
        /** The game's own animation. */
        Vanilla,
        /** Wide arc that drops into the hit. */
        Pop,
        /** Narrower arc, closer to the vanilla motion. */
        Smooth,
        /** Long horizontal sweep that turns into a block when holding a sword. */
        BlockSword
    }
}
