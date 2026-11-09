package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.ControlFly;
import com.pro0101.stashorganizer.modules.hyperglide.ElytraTweaks;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the flight modules follow the fireworks the player launches.
 *
 * <p>Boost timing depends on the rocket that belongs to the local player: the modules watch it to know when the
 * acceleration starts and when the rocket expires, which is not visible from the item use alone.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkMixin {
    @Shadow
    private LivingEntity shooter;

    /**
     * Reports rockets launched by the local player to the active modules.
     *
     * @param info injection callback
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void stashorganizer$track(CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || this.shooter != client.player) return;

        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;

        ControlFly control = Modules.get().get(ControlFly.class);
        if (control != null && control.isActive()) {
            control.track(rocket);
        }

        ElytraTweaks tweaks = Modules.get().get(ElytraTweaks.class);
        if (tweaks != null && tweaks.isActive()) {
            tweaks.track(rocket);
        }
    }
}
