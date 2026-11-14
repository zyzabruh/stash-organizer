#version 330

// Void. Two rings at different radii — a tight violet one and a wide teal one — which is the
// end-portal look: colour that falls through the outer ring instead of fading out of it. The body is
// pushed towards near-black so the rings carry the picture.
//
// The rings are built by subtracting the closer spread from the wider one, so the teal only appears
// where the violet has already stopped and the two never wash into a single gradient.

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

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.7, 289.3))) * 18643.21);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 frac = fract(p);
    // "smooth" is a reserved GLSL qualifier and cannot name a variable.
    vec2 ease = frac * frac * (3.0 - 2.0 * frac);

    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));

    return mix(mix(a, b, ease.x), mix(c, d, ease.x), ease.y);
}

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

    float radius = max(float(glowRadius), 1.0);
    vec4 nearRing = ring(texel * radius * 0.45);
    vec4 wideRing = ring(texel * radius);

    float rim = clamp(ring(texel * max(float(outlineWidth), 1.0)).a - original.a, 0.0, 1.0);
    float violetRing = clamp(nearRing.a - original.a, 0.0, 1.0);
    float tealRing = clamp(wideRing.a - nearRing.a, 0.0, 1.0) * 0.85;

    // Faint, large-scale shimmer: the portal surface is not a flat colour.
    float shimmer = 0.8 + 0.2 * noise(texCoord * 420.0);

    vec3 violet = vec3(0.62, 0.35, 0.95) * shimmer;
    vec3 teal = vec3(0.2, 0.85, 0.8) * shimmer;

    float halo = clamp(max(violetRing, tealRing) * glowIntensity, 0.0, 1.0);
    float body = original.a * clamp(fillAlpha + nearRing.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, halo), body), 0.0, 1.0);

    vec3 inside = mix(vec3(0.06, 0.02, 0.12), original.rgb * fillBrightness, 0.35);
    vec3 rings = mix(violet, teal, tealRing);
    vec3 outColour = original.a > 0.001 ? mix(inside, rings, rim) : rings;

    fragColor = vec4(outColour, outAlpha);
}
