package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the weather pass.
 *
 * <p>Rain and snow are drawn in their own pass, after the terrain and before the entities.
 * {@code renderPrecipitation} is the method that builds the streaks, so cancelling it removes them
 * without touching the sky darkening or the fog the weather brings.
 */
@Mixin(WeatherRendering.class)
public abstract class WeatherRendererMixin {
    /**
     * Skips the precipitation pieces.
     *
     * @param consumers buffer the streaks are written to
     * @param pos camera position
     * @param state weather render state of the frame
     * @param info injection callback
     */
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$noWeather(VertexConsumerProvider consumers, Vec3d pos, WeatherRenderState state, CallbackInfo info) {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        if (module != null && module.noWeather()) info.cancel();
    }
}
