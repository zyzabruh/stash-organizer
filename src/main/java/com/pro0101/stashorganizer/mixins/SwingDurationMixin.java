package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.Animations;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales how long the local player's hand swing lasts.
 *
 * <p>The duration is not a constant: it comes from the held item, and potions shorten or lengthen
 * it. Scaling the returned value keeps those rules and only changes the length, where returning a
 * fixed number would throw away the item and effect handling.
 */
@Mixin(LivingEntity.class)
public abstract class SwingDurationMixin {
    /**
     * Multiplies the swing duration of the local player.
     *
     * @param info injection callback carrying the vanilla duration
     */
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void stashorganizer$scaleSwing(CallbackInfoReturnable<Integer> info) {
        Animations module = Modules.get().get(Animations.class);

        if (module == null || !module.isActive()) return;
        if ((Object) MinecraftClient.getInstance().player != this) return;

        int duration = (int) Math.max(1, Math.round(info.getReturnValue() * module.multiplier()));

        info.setReturnValue(duration);
    }
}
