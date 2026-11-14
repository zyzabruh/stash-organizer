#version 330

// Bloom. A wide halo is gathered on a golden-angle spiral over 1.6 times the configured radius,
// with a quadratic falloff so the skirt fades instead of ending on a ring. The body keeps its own
// colour and is pulled towards white at its core, which is what makes the result read as light
// spilling off the model rather than a second outline.

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

    float radius = max(float(glowRadius), 1.0);

    vec3 colourSum = vec3(0.0);
    float alphaSum = 0.0;
    float weightSum = 0.0;

    for (int i = 0; i < 24; i++) {
        float portion = (float(i) + 0.5) / 24.0;
        float distance = sqrt(portion);
        float angle = float(i) * 2.39996323;
        vec2 offset = vec2(cos(angle), sin(angle)) * distance * radius * 1.6 * texel;

        vec4 sampleColor = texture(InSampler, texCoord + offset);
        float weight = (1.0 - distance) * (1.0 - distance);

        colourSum += sampleColor.rgb * sampleColor.a * weight;
        alphaSum += sampleColor.a * weight;
        weightSum += weight;
    }

    float halo = weightSum > 0.0 ? alphaSum / weightSum : 0.0;
    vec3 haloColour = alphaSum > 0.0001 ? colourSum / alphaSum : vec3(0.0);

    float bloom = clamp(pow(halo, 0.55) * glowIntensity, 0.0, 1.0);
    float core = original.a * clamp(fillAlpha + innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(bloom, core), 0.0, 1.0);

    vec3 outColour = original.a > 0.001
        ? mix(original.rgb * fillBrightness, vec3(1.0), 0.35 * original.a)
        : haloColour * 1.15;

    // The halo covers a large area, so its soft edge is the first thing an 8-bit target bands.
    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    outAlpha = clamp(outAlpha + (dither - 0.5) / 255.0, 0.0, 1.0);

    fragColor = vec4(outColour, outAlpha);
}
