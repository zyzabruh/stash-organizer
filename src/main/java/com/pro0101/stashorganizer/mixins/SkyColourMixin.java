package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Repaints the vanilla sky, and the glow that sits on the horizon before it.
 *
 * <p>Both are drawn from a single colour each, handed to the sky pass as a packed ARGB value. They
 * are the first thing drawn in the frame and everything else goes on top, so replacing them here
 * leaves terrain, clouds and entities untouched — unlike painting a quad over the screen.
 *
 * <p>This only applies when the shader sky in {@link SkyRenderingMixin} is not drawing: that mixin
 * cancels these calls before the colour is used.
 */
@Mixin(SkyRendering.class)
public abstract class SkyColourMixin {
    /**
     * Replaces the colour of the sky disc.
     *
     * @param color vanilla sky colour
     * @return tinted sky colour
     */
    @ModifyVariable(method = "renderTopSky", at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private int stashorganizer$skyColour(int color) {
        return this.stashorganizer$ambienceColour(color);
    }

    /**
     * Replaces the colour of the glow drawn around the sun.
     *
     * @param color vanilla horizon colour
     * @return tinted horizon colour
     */
    @ModifyVariable(method = "renderGlowingSky", at = @At("HEAD"), argsOnly = true, index = 3, require = 0)
    private int stashorganizer$horizonColour(int color) {
        return this.stashorganizer$ambienceColour(color);
    }

    /**
     * Returns the colour the sky should be drawn in.
     *
     * @param color vanilla colour
     * @return the configured colour, or the vanilla one when the option is off
     */
    private int stashorganizer$ambienceColour(int color) {
        Ambience module = Modules.get().get(Ambience.class);

        if (module == null || !module.isActive() || !module.skyEnabled()) return color;

        return module.skyColor();
    }
}
