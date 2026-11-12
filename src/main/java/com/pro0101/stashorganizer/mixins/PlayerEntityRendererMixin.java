package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Shaders;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strips the layers of the first-person self model from its render state.
 *
 * <p>The head, torso and legs are hidden on the model itself, but every layer — armor, cape, the
 * block or spyglass worn on the head — is drawn from the render state and ignores model visibility.
 * Clearing the state here, before the layers run, removes them without touching the held items,
 * which come from their own state.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    /**
     * Clears the state of the local player while the camera is in first person.
     *
     * @param entity player being extracted
     * @param state render state being filled
     * @param tickProgress partial tick
     * @param info injection callback
     */
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void stashorganizer$clearFirstPersonLayers(
        PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo info
    ) {
        Shaders module = Modules.get().get(Shaders.class);

        if (module == null || !module.isActive() || !module.selfInFirstPerson()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || entity != client.player) return;
        if (!client.options.getPerspective().isFirstPerson()) return;

        state.displayName = null;
        state.spyglassState.clear();
        state.headItemRenderState.clear();
        state.wearingSkullType = null;
        state.wearingSkullProfile = null;
        state.headItemAnimationProgress = 0.0F;
        state.equippedChestStack = ItemStack.EMPTY;
        state.equippedLegsStack = ItemStack.EMPTY;
        state.equippedFeetStack = ItemStack.EMPTY;
        state.equippedHeadStack = ItemStack.EMPTY;
        state.capeVisible = false;
        state.leftShoulderParrotVariant = null;
        state.rightShoulderParrotVariant = null;
        state.stuckArrowCount = 0;
        state.stingerCount = 0;
        state.extraEars = false;
    }
}
