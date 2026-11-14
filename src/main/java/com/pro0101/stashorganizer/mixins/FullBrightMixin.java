package com.pro0101.stashorganizer.mixins;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lights up the world by short-circuiting the lightmap.
 *
 * <p>The lightmap is a small texture built from the brightness of each block face; terrain and
 * entities sample it instead of computing light per pixel. Filling it with white makes every sample
 * fully lit, without changing any block, any chunk or the server's view of what you can see.
 *
 * <p>Since 1.21.11 {@code update} no longer calls {@code getBrightness}: it builds the texture from
 * the environment attributes, so the old brightness hook cannot reach it. Meteor's own fullbright
 * clears the texture and cancels the update instead, and that is what the {@code update} hook below
 * does. The {@code getBrightness} hook stays for the callers that still use it, and is static
 * because the target is static.
 */
@Mixin(LightmapTextureManager.class)
public abstract class FullBrightMixin {
    /** Lightmap texture the game would rebuild; clearing it makes every sample white. */
    @Shadow
    @Final
    private GpuTexture glTexture;

    /**
     * Fills the lightmap with white instead of rebuilding it from the world's light.
     *
     * @param tickDelta partial tick
     * @param info injection callback
     */
    @Inject(method = "update(F)V", at = @At("HEAD"), cancellable = true)
    private void stashorganizer$clearLightmap(float tickDelta, CallbackInfo info) {
        if (!stashorganizer$enabled()) return;

        GpuDevice device = RenderSystem.getDevice();
        if (device == null) return;

        device.createCommandEncoder().clearColorTexture(this.glTexture, 0xFFFFFFFF);
        info.cancel();
    }

    /**
     * Returns full brightness for a block light level.
     *
     * @param skyDarken sky darkening of the frame
     * @param level light level
     * @param info injection callback carrying the brightness
     */
    @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void stashorganizer$brightness(float skyDarken, int level, CallbackInfoReturnable<Float> info) {
        if (stashorganizer$enabled()) info.setReturnValue(1.0F);
    }

    /**
     * Reports whether full bright is on.
     *
     * @return true when the lightmap should be flat white
     */
    private static boolean stashorganizer$enabled() {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        return module != null && module.fullBright();
    }
}
