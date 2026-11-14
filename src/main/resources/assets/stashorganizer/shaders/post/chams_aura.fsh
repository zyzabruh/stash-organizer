#version 330

// Radial aura. Instead of a separable gaussian, the neighbourhood is sampled on a golden-angle
// spiral: sixteen taps spread over a disc, with the radius taken from the square root of the step so
// the outer rings, which cover more area, get proportionally more samples. The weight of a tap falls
// off with its distance, which turns the accumulated alpha into a soft skirt rather than a ring.
//
// The result is a halo that fades out with distance and keeps the entity colour at its core. There
// is no rim: an aura that ends on a hard pixel edge stops reading as an aura.

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

    for (int i = 0; i < 16; i++) {
        float portion = (float(i) + 0.5) / 16.0;
        float distance = sqrt(portion);
        float angle = float(i) * 2.39996323;
        vec2 offset = vec2(cos(angle), sin(angle)) * distance * radius * texel;

        vec4 sampleColor = texture(InSampler, texCoord + offset);
        float weight = 1.0 - distance;

        colourSum += sampleColor.rgb * sampleColor.a * weight;
        alphaSum += sampleColor.a * weight;
        weightSum += weight;
    }

    float halo = weightSum > 0.0 ? alphaSum / weightSum : 0.0;
    vec3 haloColour = alphaSum > 0.0001 ? colourSum / alphaSum : vec3(0.0);

    float haloAlpha = clamp(pow(halo, 0.8) * glowIntensity, 0.0, 1.0);
    float body = original.a * clamp(fillAlpha + halo * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(haloAlpha, body), 0.0, 1.0);

    vec3 outColour = original.a > 0.001 ? original.rgb * fillBrightness : haloColour;

    fragColor = vec4(outColour, outAlpha);
}
