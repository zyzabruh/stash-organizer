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
 * pass on top of it: the silhouettes the game already renders are read, blurred, coloured and
 * written straight back into the outline target, which the game then draws as usual. That keeps one
 * source of truth for what is outlined, and it is why the glow follows the outline colours set on
 * the entities instead of a flat tint.
 *
 * <p>Two passes, not three. {@code chams_h} blurs the silhouette sideways with a gaussian kernel and
 * keeps the colour premultiplied; {@code chams_v} finishes the vertical half, subtracts the
 * silhouette from the blurred alpha so the glow is a ring rather than a fill, and writes the result
 * back into the outline target. A separate colour pass would have cost a full screen of texture
 * reads for information the first pass can carry for free.
 *
 * <p>The chain is rebuilt when one of its scalars changes, because the uniforms are baked into the
 * pipeline at load time, and when the window size changes, because the internal targets are sized
 * from the frame they were created in. A key that failed to build is remembered rather than retried,
 * so a broken shader costs one warning instead of a recompile every frame; the game then falls back
 * to its own effect, or keeps the last chain that worked.
 */
public final class ChamsChain {
    private static final Logger LOG = LogUtils.getLogger();

    private static final Identifier SCREENQUAD = Identifier.of("minecraft", "core/screenquad");
    private static final Identifier H_FRAGMENT = Identifier.of("stashorganizer", "post/chams_h");
    private static final Identifier V_FRAGMENT = Identifier.of("stashorganizer", "post/chams_v");

    private static final Identifier MASK_TARGET = Identifier.of("stashorganizer", "chams_mask");
    private static final Identifier CHAIN_NAME = Identifier.of("stashorganizer", "chams");

    /** Target the game renders entity silhouettes into, and the one the chain writes back to. */
    public static final Identifier OUTLINE_TARGET = Identifier.of("minecraft", "entity_outline");

    private static ProjectionMatrix2 projection;
    private static PostEffectProcessor cached;

    private static int lastRadius = Integer.MIN_VALUE;
    private static int lastOutlineWidth = Integer.MIN_VALUE;
    private static float lastIntensity = Float.NaN;
    private static float lastFill = Float.NaN;
    private static float lastFillBrightness = Float.NaN;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean loggedFailure;

    private ChamsChain() {
    }

    /**
     * Returns the chain for the requested scalars, rebuilding it when they or the frame size changed.
     *
     * @param externalTargets targets the game provides to the effect
     * @param radius glow radius in pixels
     * @param intensity glow strength
     * @param fillAlpha opacity of the solid fill
     * @param outlineWidth rim thickness in pixels
     * @param fillBrightness multiplier applied to the body colour
     * @return the chain, or null when it could not be built
     */
    public static PostEffectProcessor get(Set<Identifier> externalTargets, int radius, float intensity, float fillAlpha, int outlineWidth, float fillBrightness) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (radius == lastRadius && intensity == lastIntensity && fillAlpha == lastFill
            && outlineWidth == lastOutlineWidth && fillBrightness == lastFillBrightness
            && width == lastWidth && height == lastHeight) {
            return cached;
        }

        PostEffectProcessor rebuilt = build(externalTargets, radius, intensity, fillAlpha, outlineWidth, fillBrightness);

        if (rebuilt == null) {
            // Remember the failing key so a broken shader is not recompiled on every frame; the
            // previous chain, if any, stays in use until a scalar or the window size changes.
            lastRadius = radius;
            lastIntensity = intensity;
            lastFill = fillAlpha;
            lastFillBrightness = fillBrightness;
            lastOutlineWidth = outlineWidth;
            lastWidth = width;
            lastHeight = height;

            return cached;
        }

        if (cached != null) cached.close();
        cached = rebuilt;
        loggedFailure = false;
        lastRadius = radius;
        lastIntensity = intensity;
        lastFill = fillAlpha;
        lastFillBrightness = fillBrightness;
        lastOutlineWidth = outlineWidth;
        lastWidth = width;
        lastHeight = height;

        return cached;
    }

    /**
     * Builds the chain from scratch.
     *
     * @param externalTargets targets the game provides to the effect
     * @param radius glow radius in pixels
     * @param intensity glow strength
     * @param fillAlpha opacity of the solid fill
     * @param outlineWidth rim thickness in pixels
     * @param fillBrightness multiplier applied to the body colour
     * @return the chain, or null when loading failed
     */
    private static PostEffectProcessor build(Set<Identifier> externalTargets, int radius, float intensity, float fillAlpha, int outlineWidth, float fillBrightness) {
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

            PostEffectPipeline.Pass composite = new PostEffectPipeline.Pass(
                SCREENQUAD, V_FRAGMENT,
                List.of(
                    new PostEffectPipeline.TargetSampler("In", MASK_TARGET, false, false),
                    new PostEffectPipeline.TargetSampler("Orig", OUTLINE_TARGET, false, false)
                ),
                OUTLINE_TARGET,
                Map.of("ChamsConfig", List.of(
                    new UniformValue.IntValue(outlineWidth),
                    new UniformValue.IntValue(radius),
                    new UniformValue.FloatValue(intensity),
                    new UniformValue.FloatValue(fillAlpha),
                    new UniformValue.FloatValue(fillBrightness)
                ))
            );

            PostEffectPipeline pipeline = new PostEffectPipeline(
                Map.of(MASK_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
                List.of(horizontal, composite)
            );

            return PostEffectProcessor.parseEffect(
                pipeline, MinecraftClient.getInstance().getTextureManager(), externalTargets, CHAIN_NAME, projection
            );
        } catch (ShaderLoader.LoadException | RuntimeException e) {
            if (!loggedFailure) {
                LOG.warn("Could not build the chams chain, keeping vanilla outlines", e);
                loggedFailure = true;
            }

            return null;
        }
    }

    /** Releases the cached chain, for a resource reload or a shutdown. */
    public static void clear() {
        if (cached != null) {
            cached.close();
            cached = null;
        }

        loggedFailure = false;
        lastRadius = Integer.MIN_VALUE;
        lastOutlineWidth = Integer.MIN_VALUE;
        lastIntensity = Float.NaN;
        lastFill = Float.NaN;
        lastFillBrightness = Float.NaN;
        lastWidth = -1;
        lastHeight = -1;
    }
}
