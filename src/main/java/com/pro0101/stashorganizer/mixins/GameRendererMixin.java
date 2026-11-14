package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the camera tilt that follows damage.
 *
 * <p>The tilt is applied to the view matrix right after the bob, so cancelling it leaves the camera
 * exactly where the player put it; every other frame effect is untouched.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /**
     * Skips the hurt tilt when the option is on.
     *
     * @param matrices view matrix stack
     * @param tickDelta partial tick
     * @param info injection callback
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$noHurtTilt(MatrixStack matrices, float tickDelta, CallbackInfo info) {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        if (module != null && module.isActive() && module.noHurtCam()) info.cancel();
    }
}
