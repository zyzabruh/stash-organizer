package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the HUD pieces the render optimizer can turn off.
 *
 * <p>Each of these is a complete drawing step with its own method, so cancelling the method keeps
 * the rest of the HUD — hearts, hotbar, chat — exactly as it was, instead of hiding the result with
 * a rectangle.
 */
@Mixin(InGameHud.class)
public abstract class HudOverlayMixin {
    /**
     * Skips the scoreboard on the right of the screen.
     *
     * @param context draw context
     * @param objective scoreboard being drawn
     * @param info injection callback
     */
    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void stashorganizer$noScoreboard(DrawContext context, ScoreboardObjective objective, CallbackInfo info) {
        if (this.stashorganizer$off()) info.cancel();
    }

    /**
     * Skips the scoreboard picked from the sidebar slot.
     *
     * @param context draw context
     * @param counter render tick counter
     * @param info injection callback
     */
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$noSidebar(DrawContext context, RenderTickCounter counter, CallbackInfo info) {
        if (this.stashorganizer$off()) info.cancel();
    }

    /**
     * Skips titles and subtitles.
     *
     * @param context draw context
     * @param counter render tick counter
     * @param info injection callback
     */
    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$noTitle(DrawContext context, RenderTickCounter counter, CallbackInfo info) {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        if (module != null && module.noTitle()) info.cancel();
    }

    /**
     * Skips the action bar text above the hotbar.
     *
     * @param context draw context
     * @param counter render tick counter
     * @param info injection callback
     */
    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$noOverlayMessage(DrawContext context, RenderTickCounter counter, CallbackInfo info) {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        if (module != null && module.noOverlayMessage()) info.cancel();
    }

    /**
     * Reports whether the scoreboard should be skipped.
     *
     * @return true when the scoreboard is off
     */
    private boolean stashorganizer$off() {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        return module != null && module.noScoreboard();
    }
}
