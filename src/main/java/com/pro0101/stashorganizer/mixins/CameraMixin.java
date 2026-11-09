package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.ControlFly;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Applies Control Fly's independent camera rotation.
 *
 * <p>The module steers the player along the highway while the view keeps its own yaw and pitch, which only works if
 * the camera is rotated away from the player rotation vanilla derives it from.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    /**
     * Replaces the camera rotation while the module owns the view.
     *
     * @param args camera rotation arguments
     */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
    ))
    private void stashorganizer$rotate(Args args) {
        ControlFly module = Modules.get().get(ControlFly.class);
        if (module == null || !module.camera()) return;

        args.set(0, module.yaw());
        args.set(1, module.pitch());
    }
}
