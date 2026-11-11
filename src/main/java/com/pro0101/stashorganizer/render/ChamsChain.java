package com.pro0101.stashorganizer.render;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the post-effect chain that turns the vanilla entity silhouette into a glowing, filled
 * outline.
 *
 * <p>The chain replaces the game's own {@code entity_outline} effect rather than adding a second
 * pass on top of it: the silhouettes the game already renders are read, widened, coloured and
 * written straight back into the outline target, which the game then draws as usual. That keeps one
 * source of truth for what is outlined, and it is why the glow follows the outline colours set on the
 * entities instead of a flat tint.
 *
 * <p>Three passes, each one half of a two-axis dilation: {@code chams_h} widens the mask sideways,
 * {@code chams_c} carries the silhouette colour outwards with it, and {@code chams_v} finishes the
 * vertical half before compositing the glow and the fill back into the outline target.
 *
 * <p>The chain is rebuilt only when one of its scalars changes, because the uniforms are baked into
 * the pipeline at load time. A failure to load leaves the previous chain in place, and if there never
 * was one the game falls back to its own effect.
 */
public final class ChamsChain {
    private static final Logger LOG = LogUtils.getLogger();

    private static final Identifier SCREENQUAD = Identifier.of("minecraft", "core/screenquad");
    private static final Identifier H_FRAGMENT = Identifier.of("stashorganizer", "post/chams_h");
    private static final Identifier C_FRAGMENT = Identifier.of("stashorganizer", "post/chams_c");
    private static final Identifier V_FRAGMENT = Identifier.of("stashorganizer", "post/chams_v");

    private static final Identifier MASK_TARGET = Identifier.of("stashorganizer", "chams_mask");
    private static final Identifier COLOR_TARGET = Identifier.of("stashorganizer", "chams_color");
    private static final Identifier CHAIN_NAME = Identifier.of("stashorganizer", "chams");

    /** Target the game renders entity silhouettes into, and the one the chain writes back to. */
    public static final Identifier OUTLINE_TARGET = Identifier.of("minecraft", "entity_outline");

    private static ProjectionMatrix2 projection;
    private static PostEffectProcessor cached;

    private static int lastRadius = Integer.MIN_VALUE;
    private static float lastIntensity = Float.NaN;
    private static float lastFill = Float.NaN;

    private ChamsChain() {
    }

    /**
     * Returns the chain for the requested scalars, rebuilding it when they changed.
     *
     * @param externalTargets targets the game provides to the effect
     * @param radius glow radius in pixels
     * @param intensity glow strength
     * @param fillAlpha opacity of the solid fill
     * @return the chain, or null when it could not be built
     */
    public static PostEffectProcessor get(Set<Identifier> externalTargets, int radius, float intensity, float fillAlpha) {
        if (cached != null && radius == lastRadius && intensity == lastIntensity && fillAlpha == lastFill) {
            return cached;
        }

        PostEffectProcessor rebuilt = build(externalTargets, radius, intensity, fillAlpha);
        if (rebuilt == null) return cached;

        if (cached != null) cached.close();
        cached = rebuilt;
        lastRadius = radius;
        lastIntensity = intensity;
        lastFill = fillAlpha;

        return cached;
    }

    /**
     * Builds the chain from scratch.
     *
     * @param externalTargets targets the game provides to the effect
     * @param radius glow radius in pixels
     * @param intensity glow strength
     * @param fillAlpha opacity of the solid fill
     * @return the chain, or null when loading failed
     */
    private static PostEffectProcessor build(Set<Identifier> externalTargets, int radius, float intensity, float fillAlpha) {
        try {
            if (projection == null) {
                projection = new ProjectionMatrix2("stash-organizer chams", 0.1F, 1000.0F, false);
            }

            PostEffectPipeline.Pass horizontal = new PostEffectPipeline.Pass(
                SCREENQUAD, H_FRAGMENT,
                List.of(new PostEffectPipeline.TargetSampler("In", OUTLINE_TARGET, false, false)),
                MASK_TARGET,
                Map.of("DilateConfig", List.of(new UniformValue.IntValue(radius)))
            );

            PostEffectPipeline.Pass color = new PostEffectPipeline.Pass(
                SCREENQUAD, C_FRAGMENT,
                List.of(new PostEffectPipeline.TargetSampler("In", OUTLINE_TARGET, false, false)),
                COLOR_TARGET,
                Map.of("ColorConfig", List.of(new UniformValue.IntValue(radius)))
            );

            PostEffectPipeline.Pass composite = new PostEffectPipeline.Pass(
                SCREENQUAD, V_FRAGMENT,
                List.of(
                    new PostEffectPipeline.TargetSampler("In", MASK_TARGET, false, false),
                    new PostEffectPipeline.TargetSampler("Color", COLOR_TARGET, false, false),
                    new PostEffectPipeline.TargetSampler("Orig", OUTLINE_TARGET, false, false)
                ),
                OUTLINE_TARGET,
                Map.of("ChamsConfig", List.of(
                    new UniformValue.FloatValue(intensity),
                    new UniformValue.FloatValue(fillAlpha),
                    new UniformValue.IntValue(radius)
                ))
            );

            PostEffectPipeline pipeline = new PostEffectPipeline(
                Map.of(
                    MASK_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0),
                    COLOR_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)
                ),
                List.of(horizontal, color, composite)
            );

            return PostEffectProcessor.parseEffect(
                pipeline, MinecraftClient.getInstance().getTextureManager(), externalTargets, CHAIN_NAME, projection
            );
        } catch (ShaderLoader.LoadException | RuntimeException e) {
            LOG.warn("Could not build the chams chain, keeping vanilla outlines", e);
            return null;
        }
    }

    /** Releases the cached chain, for a resource reload or a shutdown. */
    public static void clear() {
        if (cached != null) {
            cached.close();
            cached = null;
        }

        lastRadius = Integer.MIN_VALUE;
        lastIntensity = Float.NaN;
        lastFill = Float.NaN;
    }
}
