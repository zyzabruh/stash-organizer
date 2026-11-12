package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Skybox;
import com.pro0101.stashorganizer.render.SkyboxRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the skybox in place of the vanilla sky.
 *
 * <p>The sky pass already has the projection and model-view matrices uploaded, which is what the
 * fragment shader needs to turn a screen position back into a view direction; replacing the disc
 * here therefore costs nothing extra.
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    /**
     * Draws the shader sky instead of the vanilla gradient.
     *
     * @param color vanilla sky colour
     * @param info injection callback
     */
    @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$skybox(int color, CallbackInfo info) {
        Skybox module = Modules.get().get(Skybox.class);

        if (module == null || !module.isActive() || !SkyboxRenderer.available()) return;

        module.draw();
        info.cancel();
    }

    /**
     * Hides the dark sky of the Nether and the End, which is painted after the top sky.
     *
     * <p>{@code WorldRenderer} calls {@code renderTopSky} first in every dimension and
     * {@code renderSkyDark} afterwards when the dimension asks for it, so the shader is already on
     * screen by the time this runs: cancelling is enough, and drawing again would only paint the
     * same quad twice.
     *
     * @param info injection callback
     */
    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$darkSky(CallbackInfo info) {
        Skybox module = Modules.get().get(Skybox.class);

        if (module == null || !module.isActive() || !SkyboxRenderer.available()) return;
        if (!module.hideDarkSky()) return;

        info.cancel();
    }

    /**
     * Replaces the End sky box with the shader sky.
     *
     * <p>The End has its own sky rather than a gradient, drawn inside the same pass, so the shader
     * is drawn in its place instead of in front of it.
     *
     * @param info injection callback
     */
    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$endSky(CallbackInfo info) {
        Skybox module = Modules.get().get(Skybox.class);

        if (module == null || !module.isActive() || !SkyboxRenderer.available()) return;

        module.draw();
        info.cancel();
    }

    /**
     * Hides the sun, moon and stars while the shader sky is drawn.
     *
     * @param matrices pose stack
     * @param sunAngle sun angle
     * @param moonAngle moon angle
     * @param starAngle star angle
     * @param moonPhase moon phase
     * @param rainBrightness rain brightness
     * @param starBrightness star brightness
     * @param info injection callback
     */
    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$celestial(
        MatrixStack matrices, float sunAngle, float moonAngle, float starAngle,
        MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo info
    ) {
        Skybox module = Modules.get().get(Skybox.class);

        if (module == null || !module.isActive() || !SkyboxRenderer.available()) return;
        if (!module.hideCelestial()) return;

        info.cancel();
    }
}
