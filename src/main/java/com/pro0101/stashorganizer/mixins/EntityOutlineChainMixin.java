package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Shaders;
import com.pro0101.stashorganizer.render.ChamsChain;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * Hands the chams chain to the game instead of its own entity outline effect.
 *
 * <p>{@code WorldRenderer.render} loads the {@code entity_outline} effect once per frame and runs it
 * over the silhouettes it has just drawn. Returning another processor for that same identifier keeps
 * every other part of the pass intact: the silhouettes, the target, the composite and the depth
 * handling all stay vanilla, only the effect applied to them changes.
 */
@Mixin(WorldRenderer.class)
public abstract class EntityOutlineChainMixin {
    /**
     * Replaces the entity outline effect with the chams chain.
     *
     * @param loader shader loader that would load the vanilla effect
     * @param id effect identifier
     * @param targets targets the game provides to the effect
     * @return the chams chain when it applies, the vanilla effect otherwise
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;"
        )
    )
    private PostEffectProcessor stashorganizer$chamsChain(ShaderLoader loader, Identifier id, Set<Identifier> targets) {
        Shaders module = Modules.get().get(Shaders.class);

        if (module != null && module.isActive() && module.usesChain() && ChamsChain.OUTLINE_TARGET.equals(id)) {
            PostEffectProcessor chain = ChamsChain.get(
                targets, module.chainGlowRadius(), module.chainGlowIntensity(),
                module.chainFillAlpha(), module.chainOutlineWidth(), module.chainFillBrightness()
            );

            if (chain != null) return chain;
        }

        return loader.loadPostEffect(id, targets);
    }
}
