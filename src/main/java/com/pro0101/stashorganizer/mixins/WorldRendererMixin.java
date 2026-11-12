package com.pro0101.stashorganizer.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.pro0101.stashorganizer.modules.render.Skybox;
import com.pro0101.stashorganizer.render.SkyboxRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the skybox in dimensions that have no vanilla sky.
 *
 * <p>The End paints its own sky, so {@link SkyRenderingMixin} can replace it in place. The Nether
 * has none at all: {@code renderSky} returns before adding its pass, which leaves the screen as the
 * fog colour and nothing else. A pass is added here instead, before the main pass, so terrain still
 * draws over the shader.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    private DefaultFramebufferSet framebufferSet;

    /**
     * Adds the sky pass the Nether would otherwise skip.
     *
     * @param frame frame graph of the frame
     * @param camera camera of the frame
     * @param fog sky fog buffer, bound by the pass
     * @param info injection callback
     */
    @Inject(method = "renderSky", at = @At("HEAD"))
    private void stashorganizer$netherSky(
        FrameGraphBuilder frame, Camera camera, GpuBufferSlice fog, CallbackInfo info
    ) {
        Skybox module = Modules.get().get(Skybox.class);

        if (module == null || !module.isActive() || !SkyboxRenderer.available()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || client.world.getRegistryKey() != World.NETHER) return;

        var pass = frame.createPass("stashorganizer_skybox");

        this.framebufferSet.mainFramebuffer = pass.transfer(this.framebufferSet.mainFramebuffer);

        pass.setRenderer(module::draw);
    }
}
