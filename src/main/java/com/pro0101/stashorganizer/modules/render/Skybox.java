package com.pro0101.stashorganizer.modules.render;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.render.SkyboxImage;
import com.pro0101.stashorganizer.render.SkyboxRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Replaces the sky with a fullscreen shader.
 *
 * <p>The sky is drawn by {@link SkyboxRenderer} in the sky pass, with the vanilla gradient
 * cancelled. Each mode is a separate fragment shader, so switching mode changes the shader rather
 * than a setting inside one.
 */
public class Skybox extends Module {
    /** Available sky looks, one fragment shader each. */
    public enum Mode {
        Aurora,
        Nebula,
        Sunset,
        Storm,
        Twilight,
        Galaxy,
        Ocean,
        Ember,
        Image
    }

    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.general.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which sky shader is drawn.")
        .defaultValue(Mode.Aurora)
        .build()
    );

    private final Setting<String> imageFile = this.general.add(new StringSetting.Builder()
        .name("image-file")
        .description("PNG inside config/stash-organizer/skybox/, drawn as an equirectangular panorama. A missing file falls back to Sunset.")
        .defaultValue("sky.png")
        .visible(() -> this.mode.get() == Mode.Image)
        .build()
    );

    private final Setting<SettingColor> tint = this.general.add(new ColorSetting.Builder()
        .name("tint")
        .description("Colour the shader output is multiplied by.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Double> animationSpeed = this.general.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .description("Multiplier for every animated sky. Zero freezes the motion.")
        .defaultValue(1.0)
        .range(0.0, 3.0)
        .visible(() -> this.mode.get() != Mode.Sunset)
        .build()
    );

    private final Setting<Boolean> darkSky = this.general.add(new BoolSetting.Builder()
        .name("replace-dark-sky")
        .description("Hides the dark disc vanilla paints below the horizon, so the shader covers the whole sky instead of stopping at it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> darkFog = this.general.add(new BoolSetting.Builder()
        .name("replace-dark-fog")
        .description("In the Nether and the End, fades the terrain into the fog colour below instead of the vanilla one, which would otherwise sit against the shader as a wall.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> fog = this.general.add(new ColorSetting.Builder()
        .name("fog-color")
        .description("Fog colour used in the Nether and the End while the shader sky is drawn.")
        .defaultValue(new SettingColor(18, 16, 28, 255))
        .visible(this.darkFog::get)
        .build()
    );

    private final Setting<Boolean> celestial = this.general.add(new BoolSetting.Builder()
        .name("hide-celestial-bodies")
        .description("Also hides the vanilla sun, moon, stars and sunrise tint, which would otherwise be drawn over the shader.")
        .defaultValue(true)
        .build()
    );

    public Skybox() {
        super(StashOrganizerAddon.CATEGORY, "skybox",
            "Replaces the sky with a fullscreen shader."
        );
    }

    /**
     * Reports the active mode in the module list.
     *
     * @return mode name
     */
    @Override
    public String getInfoString() {
        return this.mode.get().name();
    }

    /**
     * Loads the panorama ahead of the first frame that needs it, so opening the mode does not stall
     * the sky pass on a file read.
     */
    @Override
    public void onActivate() {
        if (this.mode.get() == Mode.Image) SkyboxImage.get(this.imageFile.get());
    }

    /**
     * Draws the sky for the current settings.
     */
    public void draw() {
        SettingColor color = this.tint.get();

        SkyboxRenderer.render(
            this.mode.get(),
            color.r / 255.0F, color.g / 255.0F, color.b / 255.0F,
            this.imageFile.get(),
            this.animationSpeed.get().floatValue()
        );
    }

    /**
     * Returns the active preset.
     *
     * @return sky mode
     */
    public Mode mode() {
        return this.mode.get();
    }

    /**
     * Returns the tint applied to the sky, for previews.
     *
     * @return tint colour
     */
    public SettingColor tintColor() {
        return this.tint.get();
    }

    /**
     * Reports whether the dark fog colour should be replaced.
     *
     * @return true when the fog colour follows the sky
     */
    public boolean hideDarkFog() {
        return this.darkFog.get();
    }

    /**
     * Returns the fog colour used in the dark dimensions.
     *
     * @return fog colour
     */
    public SettingColor fog() {
        return this.fog.get();
    }

    /**
     * Reports whether the dark sky of the Nether and the End should be replaced.
     *
     * @return true when the shader replaces the dark sky
     */
    public boolean hideDarkSky() {
        return this.darkSky.get();
    }

    /**
     * Reports whether the vanilla sun and moon should be cancelled.
     *
     * @return true when the celestial bodies are hidden
     */
    public boolean hideCelestial() {
        return this.celestial.get();
    }
}
