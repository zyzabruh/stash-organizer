package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.Navigation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the Hyperglide navigation map with the HUD.
 *
 * <p>Meteor's {@code Render2DEvent} only carries the overlay pass used for hover and status text;
 * the map itself is drawn straight onto the HUD's {@link DrawContext}, so it needs this hook at the
 * end of {@link InGameHud#render}. The module checks whether it is enabled before drawing.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /**
     * Renders the navigation map.
     *
     * @param context draw context
     * @param counter render tick counter
     * @param info injection callback
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void stashorganizer$navigation(DrawContext context,
        RenderTickCounter counter, CallbackInfo info) {

        Navigation module = Modules.get().get(Navigation.class);
        if (module != null) module.render(context);
    }
}
