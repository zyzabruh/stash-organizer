package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.EntityGlow;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Colours outlines the game tints without asking the entity.
 *
 * <p>{@link EntityGlowMixin} answers {@code getTeamColorValue}, but some paths call
 * {@code setColor} with a plain white instead of asking. Only that white is replaced: a colour the
 * entity already answered is left untouched, so the per-kind colours still win.
 */
@Mixin(OutlineVertexConsumerProvider.class)
public abstract class OutlineColorMixin {
    /**
     * Substitutes the fallback colour for the plain white outline tint.
     *
     * @param color tint the game is about to use
     * @return tint to use instead
     */
    @ModifyVariable(method = "setColor", at = @At("HEAD"), argsOnly = true)
    private int stashorganizer$outlineColor(int color) {
        EntityGlow module = Modules.get().get(EntityGlow.class);

        if (module == null || !module.isActive()) return color;
        if (color != -1) return color;

        return module.fallback();
    }
}
