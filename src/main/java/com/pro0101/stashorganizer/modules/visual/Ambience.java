package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

/**
 * Replaces the fog, the sky colour and the time of day.
 *
 * <p>The fog is described by four numbers the renderer writes into its fog buffer: where the fog
 * starts and ends, and where the sky fades out. Both are exposed here, so the fog can be pulled in
 * to hide the horizon of a weak render distance or pushed out to see further.
 *
 * <p>Everything is client side. The server keeps its own time and weather, this only changes what
 * the client draws.
 */
public class Ambience extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Boolean> fog = this.general.add(new BoolSetting.Builder()
        .name("fog")
        .description("Replace the fog colour in every dimension.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> fogColor = this.general.add(new ColorSetting.Builder()
        .name("fog-colour")
        .description("Colour the terrain fades into.")
        .defaultValue(new SettingColor(18, 16, 28))
        .visible(this.fog::get)
        .build()
    );

    private final Setting<Integer> start = this.general.add(new IntSetting.Builder()
        .name("fog-start")
        .description("Distance, in blocks, where the fog begins.")
        .defaultValue(32)
        .range(0, 512)
        .sliderRange(0, 256)
        .visible(this.fog::get)
        .build()
    );

    private final Setting<Integer> end = this.general.add(new IntSetting.Builder()
        .name("fog-end")
        .description("Distance, in blocks, where everything is fully hidden by the fog.")
        .defaultValue(128)
        .range(1, 1024)
        .sliderRange(16, 512)
        .visible(this.fog::get)
        .build()
    );

    private final Setting<Boolean> horizonBand = this.general.add(new BoolSetting.Builder()
        .name("cover-horizon")
        .description("Also fades the sky into the fog at the end distance, hiding the line where terrain stops.")
        .defaultValue(false)
        .visible(this.fog::get)
        .build()
    );

    private final Setting<Boolean> sky = this.general.add(new BoolSetting.Builder()
        .name("sky")
        .description("Replace the sky and the glow near the horizon with one colour.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> skyColor = this.general.add(new ColorSetting.Builder()
        .name("sky-colour")
        .description("Colour painted over the sky and the horizon.")
        .defaultValue(new SettingColor(40, 90, 170))
        .visible(this.sky::get)
        .build()
    );

    private final Setting<Boolean> customTime = this.general.add(new BoolSetting.Builder()
        .name("custom-time")
        .description("Hold the world at a fixed time of day.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> time = this.general.add(new IntSetting.Builder()
        .name("time")
        .description("Time of day in ticks, 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight.")
        .defaultValue(6000)
        .range(0, 23999)
        .sliderRange(0, 23999)
        .visible(this.customTime::get)
        .build()
    );

    public Ambience() {
        super(StashOrganizerAddon.VISUALS, "atmosphere", "Replaces the fog, the sky colour and holds the world at a chosen time of day.");
    }

    /**
     * Holds the time of day while the option is on.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!this.customTime.get() || this.mc.world == null) return;

        this.mc.world.setTime(this.mc.world.getTime(), this.time.get(), false);
    }

    /**
     * Reports whether the fog should be replaced.
     *
     * @return true when the fog option is on
     */
    public boolean fogEnabled() {
        return this.fog.get();
    }

    /**
     * Returns the fog colour to hand to the renderer.
     *
     * @return fog colour
     */
    public SettingColor fog() {
        return this.fogColor.get();
    }

    /**
     * Returns where the fog starts.
     *
     * <p>The two distances can be set in any order: the smaller one is always the start, so typing
     * them backwards still gives a usable fog instead of an inside-out one.
     *
     * @return start distance in blocks
     */
    public float fogStart() {
        return Math.min(this.start.get(), this.end.get());
    }

    /**
     * Returns where the fog is at full strength.
     *
     * @return end distance in blocks, always above {@link #fogStart()}
     */
    public float fogEnd() {
        return Math.max(Math.max(1, this.start.get()), this.end.get());
    }

    /**
     * Reports whether the sky should fade into the fog as well.
     *
     * @return true when the horizon band is covered
     */
    public boolean coverHorizon() {
        return this.horizonBand.get();
    }

    /**
     * Reports whether the sky colour should be replaced.
     *
     * @return true when the sky option is on
     */
    public boolean skyEnabled() {
        return this.sky.get();
    }

    /**
     * Returns the sky colour as a packed ARGB value.
     *
     * @return sky colour
     */
    public int skyColor() {
        SettingColor color = this.skyColor.get();

        return (255 << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }
}
