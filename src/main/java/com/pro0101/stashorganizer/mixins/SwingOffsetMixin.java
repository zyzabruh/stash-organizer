package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.Animations;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the rotations applied to the held item while it swings.
 *
 * <p>The game positions the item first, then adds a short sequence of rotations for the swing. Only
 * that second step is cancelled here: the position, the equipment progress and the item itself are
 * left alone, so a replaced swing still ends exactly where the vanilla one would.
 */
@Mixin(HeldItemRenderer.class)
public abstract class SwingOffsetMixin {
    /**
     * Applies the selected swing motion in place of the vanilla one.
     *
     * @param matrices matrix stack of the held item
     * @param arm arm holding the item
     * @param swingProgress progress of the swing, from zero to one
     * @param info injection callback
     */
    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true, require = 0)
    private void stashorganizer$swing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo info) {
        Animations module = Modules.get().get(Animations.class);

        if (module == null || !module.isActive()) return;

        int side = arm == Arm.RIGHT ? 1 : -1;
        float root = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

        switch (module.swing()) {
            case Vanilla -> {
                return;
            }
            case Pop -> {
                float arc = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + arc * -20.0F)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * root * -10.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(root * -80.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
            }
            case Smooth -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * root * -10.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(root * -40.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
            }
            case BlockSword -> {
                if (arm == Arm.RIGHT && this.stashorganizer$sword()) {
                    matrices.translate(0.0F, 0.1F, -0.2F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 65.0F));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(side * -100.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 20.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -220.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * root * -40.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                } else {
                    float arc = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + arc * -20.0F)));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * root * -20.0F));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(root * -80.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                }
            }
        }

        info.cancel();
    }

    /**
     * Reports whether the main hand holds a sword.
     *
     * @return true when a sword is held in the main hand
     */
    private boolean stashorganizer$sword() {
        MinecraftClient client = MinecraftClient.getInstance();

        return client.player != null && client.player.getMainHandStack().isIn(ItemTags.SWORDS);
    }
}
