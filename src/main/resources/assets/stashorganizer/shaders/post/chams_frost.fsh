#version 330

// Frost. The rim is the exact-pixel dilation of the edge pass, but painted ice: a pale blue whose
// brightness varies per three-pixel facet, and single lit pixels scattered along it, which is what
// makes a flat ring read as frost catching the light rather than as a coloured border.
//
// The halo is deliberately weak. Snow reflects instead of glowing, so the body stays nearly clear
// and the light sits on the rim.

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

    float width = max(float(outlineWidth), 1.0);
    vec4 band = ring(texel * width);
    float rim = clamp(band.a - original.a, 0.0, 1.0);

    // Frost grains: whole-pixel cells, so the sparkle does not shimmer while the camera moves.
    vec2 cell = floor(gl_FragCoord.xy);
    float crystal = step(0.982, hash(cell));
    float facet = 0.78 + 0.22 * hash(floor(gl_FragCoord.xy / 3.0));

    vec3 ice = vec3(0.72, 0.88, 1.0) * facet;
    vec3 rimColour = mix(ice, vec3(1.0), crystal);

    float radius = max(float(glowRadius), 1.0);
    vec4 spread = ring(texel * radius);
    float halo = clamp(pow(max(spread.a - original.a, 0.0), 0.8) * glowIntensity, 0.0, 1.0);

    float body = original.a * clamp(fillAlpha + spread.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, halo * 0.6), body), 0.0, 1.0);

    vec3 base = original.a > 0.001
        ? mix(original.rgb * fillBrightness, ice, 0.35)
        : mix(ice, vec3(0.45, 0.62, 0.85), 0.45);

    fragColor = vec4(mix(base, rimColour, rim), outAlpha);
}
