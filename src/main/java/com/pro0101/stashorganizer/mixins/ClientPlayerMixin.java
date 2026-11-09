package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.BounceFly;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Bounce Fly at full speed while an item is in use.
 *
 * <p>Vanilla slows the player while eating or drinking. During a bounce that slowdown breaks the rhythm the module
 * produces, so the multiplier is reported as one while it drives movement.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMixin {
    /**
     * Removes the item use slowdown while bouncing.
     *
     * @param info speed multiplier callback
     */
    @Inject(method = "getActiveItemSpeedMultiplier", at = @At("RETURN"), cancellable = true)
    private void stashorganizer$speed(CallbackInfoReturnable<Float> info) {
        BounceFly module = Modules.get().get(BounceFly.class);
        if (module != null && module.enabled()) {
            info.setReturnValue(1.0F);
        }
    }
}
