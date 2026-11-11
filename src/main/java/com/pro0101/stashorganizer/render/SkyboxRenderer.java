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
 * <p>Every layer is built once, at class load. A pipeline that cannot be created leaves
 * {@link #available()} false and the vanilla sky in place: a shader that fails to compile must not
 * take the sky or the addon down.
 */
public final class SkyboxRenderer {
    private static final Map<Skybox.Mode, RenderLayer> LAYERS = new EnumMap<>(Skybox.Mode.class);

    private static boolean available;

    static {
        boolean built = true;

        for (Skybox.Mode mode : Skybox.Mode.values()) {
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
     * Reports whether every skybox pipeline was built.
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
     */
    public static void render(Skybox.Mode mode, float red, float green, float blue) {
        RenderLayer layer = LAYERS.get(mode);

        if (layer == null || !available) return;

        BufferBuilder buffer = Tessellator.getInstance().begin(
            VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR
        );

        buffer.vertex(-1.0F, -1.0F, 0.0F).color(red, green, blue, 1.0F);
        buffer.vertex(1.0F, -1.0F, 0.0F).color(red, green, blue, 1.0F);
        buffer.vertex(1.0F, 1.0F, 0.0F).color(red, green, blue, 1.0F);
        buffer.vertex(-1.0F, 1.0F, 0.0F).color(red, green, blue, 1.0F);

        BuiltBuffer built = buffer.end();

        if (built == null) return;

        layer.draw(built);
    }

    /**
     * Builds the pipeline and layer of one mode.
     *
     * @param mode mode to build
     * @return layer, or null when the pipeline could not be created
     */
    private static RenderLayer build(Skybox.Mode mode) {
        String name = mode.name().toLowerCase(Locale.ROOT);

        try {
            RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("stashorganizer", "pipeline/skybox_" + name))
                .withVertexShader(Identifier.of("stashorganizer", "core/skybox"))
                .withFragmentShader(Identifier.of("stashorganizer", "core/skybox_" + name))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();

            return RenderLayer.of(
                "stashorganizer_skybox_" + name, RenderSetup.builder(pipeline).build()
            );
        } catch (RuntimeException exception) {
            StashOrganizerAddon.LOG.error("Skybox mode " + name + " failed to build", exception);
            return null;
        }
    }
}
