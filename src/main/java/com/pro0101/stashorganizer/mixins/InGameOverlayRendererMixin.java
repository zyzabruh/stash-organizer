package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import com.pro0101.stashorganizer.modules.visual.TotemAnimation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the two overlays drawn over the screen: the flames and the floating item.
 *
 * <p>Both live in the same renderer, and both are effects applied after the world instead of things
 * placed in it, which is why cancelling one call is enough to remove them without touching the HUD.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    /** Frame counter of the floating item animation, ticked by the game once per frame. */
    @Shadow
    private int floatingItemTimer;

    /**
     * Skips the flames when the option is on.
     *
     * @param matrices matrix stack of the overlay
     * @param vertexConsumers buffer source of the overlay
     * @param sprite flame sprite
     * @param info injection callback
     * @implNote {@code renderFireOverlay} is static, so this handler is static too: Mixin refuses a
     *     non-static callback into a static target. The flag it reads is global state, so nothing is
     *     lost by dropping the receiver.
     */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void stashorganizer$noFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo info) {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        if (module != null && module.isActive() && module.noFireOverlay()) info.cancel();
    }

    /**
     * Advances the floating item animation faster.
     *
     * @param info injection callback
     */
    @Inject(method = "tickFloatingItemTimer", at = @At("TAIL"))
    private void stashorganizer$speedUpTotem(CallbackInfo info) {
        TotemAnimation module = Modules.get().get(TotemAnimation.class);

        if (module == null || !module.isActive()) return;

        this.floatingItemTimer += module.extraTicks();
    }
}
