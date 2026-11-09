package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.utilities.Flight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns a firework use into the spoofed boost request during simulated flight.
 *
 * <p>When elytra flight is spoofed the server would not apply the rocket, so the use is consumed here and handed to
 * the flight utility instead.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class InteractionMixin {
    /**
     * Consumes a firework interaction for the active spoofing window.
     *
     * @param player local player
     * @param hand requested hand
     * @param info interaction callback
     */
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$rocket(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != client.player || !Flight.get().active() ||
            !player.getStackInHand(hand).isOf(Items.FIREWORK_ROCKET)) {
            return;
        }

        if (!Flight.get().request()) return;
        info.setReturnValue(ActionResult.SUCCESS);
    }
}
