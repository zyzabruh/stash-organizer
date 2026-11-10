package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.BounceFly;
import com.pro0101.stashorganizer.modules.hyperglide.ControlFly;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes mouse look through Control Fly while it owns the view.
 *
 * <p>Vanilla applies look input straight to the player rotation; the module needs the input to move its own camera
 * instead, otherwise steering would also turn the flight direction.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    /**
     * Forwards look input to the module and cancels the vanilla rotation.
     *
     * @param px horizontal look movement
     * @param py vertical look movement
     * @param info injection callback
     */
    @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$look(double px, double py, CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this != client.player) return;

        ControlFly module = Modules.get().get(ControlFly.class);
        if (module != null && module.view()) {
            module.look(px, py);
            info.cancel();
            return;
        }

        // Bounce Fly rewrites the player rotation every tick, so it needs the same treatment when its
        // freelook is on: the look input has to move the camera, not the highway heading.
        BounceFly bounce = Modules.get().get(BounceFly.class);
        if (bounce == null || !bounce.view()) return;

        bounce.look(px, py);
        info.cancel();
    }
}
