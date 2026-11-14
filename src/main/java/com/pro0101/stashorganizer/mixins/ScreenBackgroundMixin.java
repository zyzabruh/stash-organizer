package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the dark tint and the blur drawn behind screens.
 *
 * <p>Only while a world is loaded: the menus use the same method to draw their own background, and
 * blanking it there would leave the main menu as a flat window with no panorama.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    /**
     * Skips the background of the screen.
     *
     * @param context draw context
     * @param mouseX mouse x position
     * @param mouseY mouse y position
     * @param tickDelta partial tick
     * @param info injection callback
     */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$noBackground(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo info) {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        if (module == null || !module.noGuiBackground()) return;
        if (MinecraftClient.getInstance().world == null) return;

        info.cancel();
    }
}
