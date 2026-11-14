#version 330

// Sketch. No dilation at all: the rim comes from a diagonal gradient — four alpha taps on the
// diagonals minus the centre — which lights up exactly on the silhouette's border and stays dark
// both inside and outside it. The body is a paper wash, and the rim is broken into ink strokes by a
// diagonal hatch, so the result looks drawn rather than shaded.
//
// The other styles add light around the body; this one removes it, which is why it reads so
// differently from them at a glance.

uniform sampler2D InSampler;

layout(std140) uniform ChamsConfig {
    int outlineWidth;
    int glowRadius;
    float glowIntensity;
    float fillAlpha;
    float fillBrightness;
    float innerGlow;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 original = texture(InSampler, texCoord);
    vec2 texel = 1.0 / max(vec2(textureSize(InSampler, 0)), vec2(1.0));

    float reach = max(float(outlineWidth), 1.0);
    vec2 offset = texel * reach;

    float a0 = texture(InSampler, texCoord + vec2(offset.x, offset.y)).a;
    float a1 = texture(InSampler, texCoord + vec2(-offset.x, offset.y)).a;
    float a2 = texture(InSampler, texCoord + vec2(offset.x, -offset.y)).a;
    float a3 = texture(InSampler, texCoord + vec2(-offset.x, -offset.y)).a;

    float edge = clamp((a0 + a1 + a2 + a3) * 0.25 - original.a, 0.0, 1.0);

    // Hatching: diagonal strokes of two pixels, offset per row so the lines never align into a grid.
    float row = floor(gl_FragCoord.y / 2.0);
    float stroke = fract((gl_FragCoord.x + gl_FragCoord.y * 0.5 + mod(row, 2.0) * 1.5) / 5.0);
    float ink = smoothstep(0.15, 0.55, stroke) * (0.55 + 0.45 * fract(row * 0.37));

    float rim = edge * mix(0.55, 1.0, ink);

    vec3 paper = vec3(0.94, 0.93, 0.88);
    vec3 graphite = vec3(0.10, 0.10, 0.13);

    float body = original.a * fillAlpha;
    float outAlpha = clamp(max(rim, body), 0.0, 1.0);

    vec3 inside = mix(graphite, paper, clamp(fillAlpha, 0.0, 1.0));
    vec3 outColour = original.a > 0.001 ? mix(inside, paper, 0.35) : graphite;

    outColour = mix(outColour, graphite, rim);

    fragColor = vec4(outColour, outAlpha);
}
