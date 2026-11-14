package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Skybox;
import com.pro0101.stashorganizer.modules.visual.Ambience;
import com.pro0101.stashorganizer.render.SkyboxRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Gives the Nether and the End a fog colour that matches the shader sky, and lets the atmosphere
 * module drive the fog range.
 *
 * <p>{@code FogRenderer} has two methods called {@code applyFog}: one works out the colour of the
 * frame, the other writes the fog numbers into the buffer the terrain shader reads. Both are named
 * here with their full descriptor, because selecting by name alone would bind whichever overload
 * Mixin finds first and reject the handler for having the wrong parameters.
 *
 * <p>The Nether and the End fade the terrain into a fog colour of their own, which sits against a
 * shader sky as a wall. Replacing the colour returned at the end of the first method tints that fade
 * without touching how far the fog reaches.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    /**
     * Replaces the fog colour of the dark dimensions.
     *
     * @param camera camera of the frame
     * @param color render distance in chunks
     * @param counter render tick counter
     * @param tickDelta partial tick
     * @param world client world
     * @param info injection callback
     */
    @Inject(
        method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void stashorganizer$fogColor(
        Camera camera, int color, RenderTickCounter counter, float tickDelta,
        ClientWorld world, CallbackInfoReturnable<Vector4f> info
    ) {
        Skybox module = Modules.get().get(Skybox.class);

        if (module == null || !module.isActive() || !SkyboxRenderer.available()) return;
        if (!module.hideDarkFog() || world == null) return;

        RegistryKey<World> dimension = world.getRegistryKey();

        if (dimension != World.NETHER && dimension != World.END) return;

        SettingColor fog = module.fog();
        info.setReturnValue(new Vector4f(
            fog.r / 255.0F, fog.g / 255.0F, fog.b / 255.0F, 1.0F
        ));
    }

    /**
     * Rewrites the fog numbers before they reach the shader.
     *
     * <p>The buffer holds the colour followed by six distances: where the environmental fog starts
     * and ends, where the render distance fog starts and ends, and where the sky and the clouds fade
     * out. Only the colour and the environmental pair are touched, so the render distance fade and
     * the cloud fade stay as the game computed them.
     *
     * @param args arguments of the buffer write: buffer, offset, colour, then the six distances
     */
    @ModifyArgs(
        method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
        require = 0
    )
    private void stashorganizer$fogRange(Args args) {
        Ambience module = Modules.get().get(Ambience.class);

        if (module == null || !module.isActive() || !module.fogEnabled()) return;

        SettingColor fog = module.fog();

        args.set(2, new Vector4f(fog.r / 255.0F, fog.g / 255.0F, fog.b / 255.0F, 1.0F));
        args.set(3, module.fogStart());
        args.set(4, module.fogEnd());

        if (module.coverHorizon()) args.set(7, module.fogEnd());
    }
}
