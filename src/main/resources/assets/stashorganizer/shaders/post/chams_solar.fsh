#version 330

// Solar. A thick warm rim around a body pushed towards white, and a corona taken from two spreads at
// different radii so the light has a bright inner band and a long soft tail instead of one uniform
// halo. The rim is widened by half again: the sun has no crisp edge, and a thin outline would fight
// the corona rather than feed it.

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

vec4 ring(vec2 offset) {
    vec2 diagonal = offset * 0.70710678;

    vec4 total = texture(InSampler, texCoord + vec2(offset.x, 0.0));
    total += texture(InSampler, texCoord - vec2(offset.x, 0.0));
    total += texture(InSampler, texCoord + vec2(0.0, offset.y));
    total += texture(InSampler, texCoord - vec2(0.0, offset.y));
    total += texture(InSampler, texCoord + diagonal);
    total += texture(InSampler, texCoord - diagonal);
    total += texture(InSampler, texCoord + vec2(diagonal.x, -diagonal.y));
    total += texture(InSampler, texCoord + vec2(-diagonal.x, diagonal.y));

    return total * 0.125;
}

void main() {
    vec4 original = texture(InSampler, texCoord);
    vec2 texel = 1.0 / max(vec2(textureSize(InSampler, 0)), vec2(1.0));

    float width = max(float(outlineWidth), 1.0) * 1.5;
    float rim = clamp(ring(texel * width).a - original.a, 0.0, 1.0);

    float radius = max(float(glowRadius), 1.0);
    vec4 inner = ring(texel * radius * 0.35);
    vec4 outer = ring(texel * radius);

    float band = clamp(max(inner.a - original.a, 0.0) * glowIntensity, 0.0, 1.0);
    float corona = clamp(pow(max(outer.a - original.a, 0.0), 0.5) * glowIntensity * 0.9, 0.0, 1.0);

    vec3 gold = vec3(1.0, 0.78, 0.25);
    vec3 white = vec3(1.0, 0.97, 0.85);

    float body = original.a * clamp(fillAlpha + inner.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, max(band, corona)), body), 0.0, 1.0);

    vec3 inside = mix(original.rgb * fillBrightness, white, 0.4);
    vec3 glow = mix(gold, white, band);
    vec3 outColour = original.a > 0.001 ? mix(inside, glow, rim) : glow;

    fragColor = vec4(outColour, outAlpha);
}
