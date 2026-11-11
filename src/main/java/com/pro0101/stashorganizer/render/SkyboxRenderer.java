package com.pro0101.stashorganizer.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.render.Skybox;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Draws the skybox as one fullscreen quad in the sky pass.
 *
 * <p>Each mode owns a render pipeline and a layer built from it. The vertex shader passes the
 * clip-space corners through untouched and the fragment shader turns them back into a view
 * direction with the inverse of the projection and model-view matrices, so the sky follows the
 * camera without any geometry of its own.
 *
 * <p>The procedural modes are built once, at class load. The image mode cannot be: its texture only
 * exists once the user's file has been read, so its layer is built on the first frame that draws it.
 * A pipeline that cannot be created leaves the vanilla sky in place rather than taking the addon
 * down, and a missing image falls back to the Sunset layer so the screen never goes black.
 */
public final class SkyboxRenderer {
    private static final Map<Skybox.Mode, RenderLayer> LAYERS = new EnumMap<>(Skybox.Mode.class);

    private static RenderLayer imageLayer;
    private static Identifier imageTexture;

    private static boolean available;

    static {
        boolean built = true;

        for (Skybox.Mode mode : Skybox.Mode.values()) {
            // The image mode binds a texture that does not exist yet, so it is built on demand.
            if (mode == Skybox.Mode.Image) continue;

            RenderLayer layer = build(mode);

            if (layer == null) {
                built = false;
                continue;
            }

            LAYERS.put(mode, layer);
        }

        available = built;

        if (!built) {
            StashOrganizerAddon.LOG.error(
                "Skybox shaders are unavailable, the vanilla sky stays in place."
            );
        }
    }

    private SkyboxRenderer() {}

    /**
     * Reports whether every procedural skybox pipeline was built.
     *
     * @return true when the skybox can be drawn
     */
    public static boolean available() {
        return available;
    }

    /**
     * Draws the sky for one mode.
     *
     * @param mode skybox mode to draw
     * @param red tint red, from 0 to 1
     * @param green tint green, from 0 to 1
     * @param blue tint blue, from 0 to 1
     * @param imageName panorama file used by the image mode
     * @param speed animation multiplier, carried to the shader in the tint alpha
     */
    public static void render(Skybox.Mode mode, float red, float green, float blue, String imageName, float speed) {
        if (mode == Skybox.Mode.Image) {
            renderImage(red, green, blue, imageName, speed);
            return;
        }

        RenderLayer layer = LAYERS.get(mode);

        if (layer == null || !available) return;

        draw(layer, red, green, blue, speed);
    }

    /**
     * Draws the user's panorama, or the Sunset sky when there is no usable image.
     *
     * @param red tint red, from 0 to 1
     * @param green tint green, from 0 to 1
     * @param blue tint blue, from 0 to 1
     * @param imageName panorama file to draw
     * @param speed animation multiplier, carried to the shader in the tint alpha
     */
    private static void renderImage(float red, float green, float blue, String imageName, float speed) {
        Identifier texture = SkyboxImage.get(imageName);

        if (texture == null) {
            RenderLayer fallback = LAYERS.get(Skybox.Mode.Sunset);

            if (fallback != null) draw(fallback, red, green, blue, speed);

            return;
        }

        if (imageLayer == null || !texture.equals(imageTexture)) {
            imageLayer = buildImage(texture);
            imageTexture = texture;
        }

        if (imageLayer != null) draw(imageLayer, red, green, blue, speed);
    }

    /**
     * Submits the fullscreen quad to a layer.
     *
     * @param layer layer to draw with
     * @param red tint red, from 0 to 1
     * @param green tint green, from 0 to 1
     * @param blue tint blue, from 0 to 1
     * @param speed animation multiplier, written to the alpha so the shader receives it without a
     *              uniform block of its own
     */
    private static void draw(RenderLayer layer, float red, float green, float blue, float speed) {
        BufferBuilder buffer = Tessellator.getInstance().begin(
            VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR
        );

        buffer.vertex(-1.0F, -1.0F, 0.0F).color(red, green, blue, speed);
        buffer.vertex(1.0F, -1.0F, 0.0F).color(red, green, blue, speed);
        buffer.vertex(1.0F, 1.0F, 0.0F).color(red, green, blue, speed);
        buffer.vertex(-1.0F, 1.0F, 0.0F).color(red, green, blue, speed);

        BuiltBuffer built = buffer.end();

        if (built == null) return;

        layer.draw(built);
    }

    /**
     * Builds the pipeline and layer of one procedural mode.
     *
     * @param mode mode to build
     * @return layer, or null when the pipeline could not be created
     */
    private static RenderLayer build(Skybox.Mode mode) {
        String name = mode.name().toLowerCase(Locale.ROOT);

        try {
            return RenderLayer.of(
                "stashorganizer_skybox_" + name,
                RenderSetup.builder(pipeline(name, false)).build()
            );
        } catch (RuntimeException exception) {
            StashOrganizerAddon.LOG.error("Skybox mode " + name + " failed to build", exception);
            return null;
        }
    }

    /**
     * Builds the layer of the image mode around the user's texture.
     *
     * @param texture panorama to bind as Sampler0
     * @return layer, or null when the pipeline could not be created
     */
    private static RenderLayer buildImage(Identifier texture) {
        try {
            return RenderLayer.of(
                "stashorganizer_skybox_image",
                RenderSetup.builder(pipeline("image", true)).texture("Sampler0", texture).build()
            );
        } catch (RuntimeException exception) {
            StashOrganizerAddon.LOG.error("Skybox image mode failed to build", exception);
            return null;
        }
    }

    /**
     * Builds the pipeline shared by every mode.
     *
     * @param name shader and pipeline suffix
     * @param sampler whether the fragment shader samples a bound texture
     * @return the pipeline
     */
    private static RenderPipeline pipeline(String name, boolean sampler) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of("stashorganizer", "pipeline/skybox_" + name))
            .withVertexShader(Identifier.of("stashorganizer", "core/skybox"))
            .withFragmentShader(Identifier.of("stashorganizer", "core/skybox_" + name))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false);

        if (sampler) builder.withSampler("Sampler0");

        return builder.build();
    }
}
