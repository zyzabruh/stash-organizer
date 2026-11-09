package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.BounceFly;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds Bounce Fly's movement pattern into the key bindings.
 *
 * <p>Bounce Fly computes a forward and jump sequence but never touches the keyboard itself, so the pattern only
 * reaches the game through the key state. Answering {@code isPressed()} is also what makes takeoff possible: vanilla
 * starts elytra flight from a jump pressed while airborne, so without this the module can neither jump nor glide.
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    /**
     * Reports Bounce Fly input while the module controls movement.
     *
     * @param info key state callback
     */
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$bounceInput(CallbackInfoReturnable<Boolean> info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        BounceFly bounce = Modules.get().get(BounceFly.class);
        if (bounce == null || !bounce.enabled()) return;

        if ((Object) this == client.options.forwardKey) {
            info.setReturnValue(true);
        } else if ((Object) this == client.options.jumpKey) {
            info.setReturnValue(bounce.jumping());
        }
    }
}
