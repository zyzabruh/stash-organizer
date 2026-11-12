package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Shaders;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps only the arms when the local player is drawn in first person.
 *
 * <p>The camera sits inside the head, so the head fills the view and the torso hides the ground
 * when looking down. The arms and their sleeves stay visible, which is where the held items are
 * attached, so the shader has something to draw without the rest of the body getting in the way.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    /**
     * Hides the head, torso and legs of the first-person self model.
     *
     * @param state render state of the player being posed
     * @param info injection callback
     */
    @Inject(method = "setAngles", at = @At("RETURN"))
    private void stashorganizer$hideFirstPersonBody(PlayerEntityRenderState state, CallbackInfo info) {
        Shaders module = Modules.get().get(Shaders.class);

        if (module == null || !module.isActive() || !module.selfInFirstPerson()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || state.id != client.player.getId()) return;
        if (!client.options.getPerspective().isFirstPerson()) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;

        model.head.visible = false;
        model.body.visible = false;
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
    }
}
