package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Shaders;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers vanilla's two outline questions for the entities {@link Shaders} targets.
 *
 * <p>Vanilla asks {@code isGlowing} whether an entity gets an outline, and {@code getTeamColorValue}
 * which colour it takes, both once per rendered entity. Answering here keeps the vanilla outline
 * pass, so nothing has to be re-rendered and nothing bypasses the game's depth handling.
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    /**
     * Forces the outline of targeted entities.
     *
     * @param info injection callback
     */
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$forceGlowing(CallbackInfoReturnable<Boolean> info) {
        Shaders module = Modules.get().get(Shaders.class);

        if (module == null || !module.isActive()) return;
        if (module.shouldGlow((Entity) (Object) this)) info.setReturnValue(true);
    }

    /**
     * Gives each targeted entity the colour of its category.
     *
     * @param info injection callback
     */
    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void stashorganizer$outlineColor(CallbackInfoReturnable<Integer> info) {
        Shaders module = Modules.get().get(Shaders.class);

        if (module == null || !module.isActive()) return;

        int color = module.packedColor((Entity) (Object) this);
        if (color != -1) info.setReturnValue(color);
    }
}
