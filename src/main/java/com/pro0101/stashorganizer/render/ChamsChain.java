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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the post-effect chain that turns the vanilla entity silhouette into a glowing, filled
 * outline.
 *
 * <p>The chain replaces the game's own {@code entity_outline} effect rather than adding a second
 * pass on top of it: the silhouettes the game already renders are read, processed and written
 * straight back into the outline target, which the game then draws as usual. That keeps one source
 * of truth for what is outlined, and it is why the effect follows the outline colours set on the
 * entities instead of a flat tint.
 *
 * <p>Four shaders are available, each with its own fragment code rather than a different set of
 * uniforms on the same maths:
 * <ul>
 *   <li>{@link Style#Soft} — the separable gaussian, in two passes;</li>
 *   <li>{@link Style#Edge} — an exact-pixel rim from eight-direction taps;</li>
 *   <li>{@link Style#Aura} — a golden-angle spiral whose alpha falls off with distance;</li>
 *   <li>{@link Style#Bloom} — a wide spiral halo around a white-hot core;</li>
 *   <li>{@link Style#Rainbow} — a hue gradient computed in the shader;</li>
 *   <li>{@link Style#Frost}, {@link Style#Ember}, {@link Style#Void} and {@link Style#Solar} —
 *       themed looks whose colours are built from ice, lava, portal rings and a corona.</li>
 * </ul>
 * The last three finish in a single pass, so they render into the mask target and a small copy pass
 * moves the result into the outline target, which cannot be read and written in the same draw.
 *
 * <p>The chain is rebuilt when one of its scalars or its shader changes, because the uniforms are
 * baked into the pipeline at load time, and when the window size changes, because the internal
 * targets are sized from the frame they were created in. A key that failed to build is remembered
 * rather than retried, so a broken shader costs one warning instead of a recompile every frame; the
 * game then falls back to its own effect, or keeps the last chain that worked.
 */
public final class ChamsChain {
    private static final Logger LOG = LogUtils.getLogger();

    private static final Identifier SCREENQUAD = Identifier.of("minecraft", "core/screenquad");
    private static final Identifier H_FRAGMENT = Identifier.of("stashorganizer", "post/chams_h");
    private static final Identifier V_FRAGMENT = Identifier.of("stashorganizer", "post/chams_v");
    private static final Identifier EDGE_FRAGMENT = Identifier.of("stashorganizer", "post/chams_edge");
    private static final Identifier AURA_FRAGMENT = Identifier.of("stashorganizer", "post/chams_aura");
    private static final Identifier BLOOM_FRAGMENT = Identifier.of("stashorganizer", "post/chams_bloom");
    private static final Identifier RAINBOW_FRAGMENT = Identifier.of("stashorganizer", "post/chams_rainbow");
    private static final Identifier COPY_FRAGMENT = Identifier.of("stashorganizer", "post/chams_copy");
    private static final Identifier FROST_FRAGMENT = Identifier.of("stashorganizer", "post/chams_frost");
    private static final Identifier EMBER_FRAGMENT = Identifier.of("stashorganizer", "post/chams_ember");
    private static final Identifier VOID_FRAGMENT = Identifier.of("stashorganizer", "post/chams_void");
    private static final Identifier SOLAR_FRAGMENT = Identifier.of("stashorganizer", "post/chams_solar");
    private static final Identifier PIXEL_FRAGMENT = Identifier.of("stashorganizer", "post/chams_pixel");
    private static final Identifier SKETCH_FRAGMENT = Identifier.of("stashorganizer", "post/chams_sketch");
    private static final Identifier CRYSTAL_FRAGMENT = Identifier.of("stashorganizer", "post/chams_crystal");
    private static final Identifier TOXIC_FRAGMENT = Identifier.of("stashorganizer", "post/chams_toxic");

    private static final Identifier MASK_TARGET = Identifier.of("stashorganizer", "chams_mask");
    private static final Identifier CHAIN_NAME = Identifier.of("stashorganizer", "chams");

    /** Target the game renders entity silhouettes into, and the one the chain writes back to. */
    public static final Identifier OUTLINE_TARGET = Identifier.of("minecraft", "entity_outline");

    /** Fragment shader the chain is built around. */
    public enum Style {
        /** Separable gaussian: a rim, a soft halo and a fill, in two passes. */
        Soft,
        /** Exact-pixel rim, no blur, for a crisp neon edge. */
        Edge,
        /** Radial halo that fades with distance, for a soft aura. */
        Aura,
        /** Wide halo spilling off a white-hot core, for a bloom around the model. */
        Bloom,
        /** Hue gradient computed on screen, for a multi-colour body. */
        Rainbow,
        /** Pale ice that catches the light, for a frost theme. */
        Frost,
        /** Fractal lava field between red and white-hot, for a Nether theme. */
        Ember,
        /** Two concentric rings, violet inside and teal outside, for an End theme. */
        Void,
        /** Thick warm rim under a long corona, for a sun theme. */
        Solar,
        /** Block resolution fill with a dithered rim, for a pixel art look. */
        Pixel,
        /** Diagonal gradient edge broken into ink strokes, for a pencil look. */
        Sketch,
        /** Rippled samples with split colour channels, for a refracted glass look. */
        Crystal,
        /** Scanlines and rings over radioactive green, for a monitor look. */
        Toxic
    }

    private static ProjectionMatrix2 projection;
    private static PostEffectProcessor cached;

    private static Style lastStyle;
    private static int lastRadius = Integer.MIN_VALUE;
    private static int lastOutlineWidth = Integer.MIN_VALUE;
    private static float lastIntensity = Float.NaN;
    private static float lastFill = Float.NaN;
    private static float lastFillBrightness = Float.NaN;
    private static float lastInnerGlow = Float.NaN;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean loggedFailure;

    private ChamsChain() {
    }

    /**
     * Returns the chain for the requested shader and scalars, rebuilding it when they or the frame
     * size changed.
     *
     * @param externalTargets targets the game provides to the effect
     * @param style shader the chain is built around
     * @param radius glow radius in pixels
     * @param intensity glow strength
     * @param fillAlpha opacity of the solid fill
     * @param outlineWidth rim thickness in pixels
     * @param fillBrightness multiplier applied to the body colour
     * @param innerGlow how much the body is brightened by the spread silhouette
     * @return the chain, or null when it could not be built
     */
    public static PostEffectProcessor get(Set<Identifier> externalTargets, Style style, int radius, float intensity, float fillAlpha, int outlineWidth, float fillBrightness, float innerGlow) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (style == lastStyle && radius == lastRadius && intensity == lastIntensity
            && fillAlpha == lastFill && outlineWidth == lastOutlineWidth
            && fillBrightness == lastFillBrightness && innerGlow == lastInnerGlow
            && width == lastWidth && height == lastHeight) {
            return cached;
        }

        PostEffectProcessor rebuilt = build(externalTargets, style, radius, intensity, fillAlpha, outlineWidth, fillBrightness, innerGlow);

        // Remember the failing key so a broken shader is not recompiled on every frame; the
        // previous chain, if any, stays in use until a scalar or the window size changes.
        lastStyle = style;
        lastRadius = radius;
        lastIntensity = intensity;
        lastFill = fillAlpha;
        lastFillBrightness = fillBrightness;
        lastInnerGlow = innerGlow;
        lastOutlineWidth = outlineWidth;
        lastWidth = width;
        lastHeight = height;

        if (rebuilt == null) return cached;

        if (cached != null) cached.close();
        cached = rebuilt;
        loggedFailure = false;

        return cached;
    }

    /**
     * Builds the chain from scratch.
     *
     * @param externalTargets targets the game provides to the effect
     * @param style shader the chain is built around
     * @param radius glow radius in pixels
     * @param intensity glow strength
     * @param fillAlpha opacity of the solid fill
     * @param outlineWidth rim thickness in pixels
     * @param fillBrightness multiplier applied to the body colour
     * @param innerGlow how much the body is brightened by the spread silhouette
     * @return the chain, or null when loading failed
     */
    private static PostEffectProcessor build(Set<Identifier> externalTargets, Style style, int radius, float intensity, float fillAlpha, int outlineWidth, float fillBrightness, float innerGlow) {
        try {
            if (projection == null) {
                projection = new ProjectionMatrix2("stash-organizer chams", 0.1F, 1000.0F, false);
            }

            Map<String, List<UniformValue>> config = Map.of("ChamsConfig", List.of(
                new UniformValue.IntValue(outlineWidth),
                new UniformValue.IntValue(radius),
                new UniformValue.FloatValue(intensity),
                new UniformValue.FloatValue(fillAlpha),
                new UniformValue.FloatValue(fillBrightness),
                new UniformValue.FloatValue(innerGlow)
            ));

            List<PostEffectPipeline.Pass> passes = new ArrayList<>();

            if (style == Style.Soft) {
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
                    config
                );

                passes.add(horizontal);
                passes.add(composite);
            } else {
                // The single-pass shaders read the silhouette and write their whole result, so they
                // cannot write into the target they read from; the copy pass puts it back.
                passes.add(new PostEffectPipeline.Pass(
                    SCREENQUAD, fragment(style),
                    List.of(new PostEffectPipeline.TargetSampler("In", OUTLINE_TARGET, false, false)),
                    MASK_TARGET,
                    config
                ));

                passes.add(new PostEffectPipeline.Pass(
                    SCREENQUAD, COPY_FRAGMENT,
                    List.of(new PostEffectPipeline.TargetSampler("In", MASK_TARGET, false, false)),
                    OUTLINE_TARGET,
                    Map.of()
                ));
            }

            PostEffectPipeline pipeline = new PostEffectPipeline(
                Map.of(MASK_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
                passes
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

    /**
     * Returns the fragment shader of a single-pass style.
     *
     * @param style style to map
     * @return fragment shader identifier
     */
    private static Identifier fragment(Style style) {
        return switch (style) {
            case Edge -> EDGE_FRAGMENT;
            case Aura -> AURA_FRAGMENT;
            case Bloom -> BLOOM_FRAGMENT;
            case Rainbow -> RAINBOW_FRAGMENT;
            case Frost -> FROST_FRAGMENT;
            case Ember -> EMBER_FRAGMENT;
            case Void -> VOID_FRAGMENT;
            case Solar -> SOLAR_FRAGMENT;
            case Pixel -> PIXEL_FRAGMENT;
            case Sketch -> SKETCH_FRAGMENT;
            case Crystal -> CRYSTAL_FRAGMENT;
            case Toxic -> TOXIC_FRAGMENT;
            case Soft -> H_FRAGMENT;
        };
    }

    /** Releases the cached chain, for a resource reload or a shutdown. */
    public static void clear() {
        if (cached != null) {
            cached.close();
            cached = null;
        }

        loggedFailure = false;
        lastStyle = null;
        lastRadius = Integer.MIN_VALUE;
        lastOutlineWidth = Integer.MIN_VALUE;
        lastIntensity = Float.NaN;
        lastFill = Float.NaN;
        lastFillBrightness = Float.NaN;
        lastInnerGlow = Float.NaN;
        lastWidth = -1;
        lastHeight = -1;
    }
}
