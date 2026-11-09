package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.MiningTweaks;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes block breaking to Mining Tweaks.
 *
 * <p>The module controls break speed itself, so vanilla mining has to be suppressed while it is active or the two
 * fight over the same block and neither finishes.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MiningTweaksMixin {
    /**
     * Intercepts a fresh attack on a block.
     *
     * @param pos block position
     * @param side block side
     * @param info injection callback
     */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$attack(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> info) {
        this.stashorganizer$mine(pos, side, info);
    }

    /**
     * Intercepts progressive breaking updates.
     *
     * @param pos block position
     * @param side block side
     * @param info injection callback
     */
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$update(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> info) {
        this.stashorganizer$mine(pos, side, info);
    }

    /**
     * Forwards breaking to the module and skips vanilla handling.
     *
     * @param pos block position
     * @param side block side
     * @param info injection callback
     */
    @Unique
    private void stashorganizer$mine(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.isCreative()) return;

        MiningTweaks module = Modules.get().get(MiningTweaks.class);
        if (module == null || !module.isActive() || module.bypass(pos)) return;

        module.mine(pos, side);
        info.setReturnValue(true);
    }
}
