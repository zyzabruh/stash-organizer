package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.render.Skybox;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the Nether and the End a fog colour that matches the shader sky.
 *
 * <p>Those dimensions fade the terrain and the horizon into a fog colour of their own, which sits
 * against a shader sky as a wall: the colour keeps its vanilla hue while everything behind it
 * changed. The colour returned here is the one handed to the shaders for the frame, so replacing it
 * at the end of {@code applyFog} tints the fade without touching how far the fog reaches.
 *
 * <p>The overworld is left alone on purpose: its fog is daylight and weather, not a dimension tint.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    /**
     * Replaces the fog colour of the dark dimensions.
     *
     * @param camera camera of the frame
     * @param color vanilla fog colour
     * @param counter render tick counter
     * @param tickDelta partial tick
     * @param world client world
     * @param info injection callback
     */
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
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
}
