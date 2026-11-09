package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.BounceFly;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the jump cooldown while Bounce Fly drives movement.
 *
 * <p>Vanilla refuses to jump again during a short cooldown, which would swallow part of the bounce pattern the module
 * produces and leave it hopping irregularly.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int jumpingCooldown;

    /**
     * Clears the jump cooldown for the local player while bouncing.
     *
     * @param info injection callback
     */
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void stashorganizer$jump(CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this != client.player) return;

        BounceFly module = Modules.get().get(BounceFly.class);
        if (module != null && module.enabled()) {
            this.jumpingCooldown = 0;
        }
    }
}
