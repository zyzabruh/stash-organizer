#version 330

// Toxic. A flat radioactive green broken by two patterns that do not exist in any other style:
// horizontal scanlines every three pixels, and concentric rings centred on the screen. The rim is
// drawn last so the patterns never hide the edge, and the body is what carries them.
//
// The patterns are in screen space, so they stay the same size whatever the entity's distance and
// read as a monitor overlay rather than as part of the model.

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

    float lines = 0.55 + 0.45 * step(0.5, fract(gl_FragCoord.y / 3.0));
    float rings = 0.55 + 0.45 * sin(length(texCoord - vec2(0.5)) * 160.0);

    float radius = max(float(glowRadius), 1.0);
    vec4 spread = ring(texel * radius);
    float halo = clamp(pow(max(spread.a - original.a, 0.0), 0.65) * glowIntensity, 0.0, 1.0);

    float width = max(float(outlineWidth), 1.0);
    float rim = clamp(ring(texel * width).a - original.a, 0.0, 1.0);

    vec3 toxic = vec3(0.35, 1.0, 0.22);
    vec3 acid = vec3(0.85, 1.0, 0.35);

    float pattern = lines * rings;
    float body = original.a * clamp(fillAlpha + spread.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, halo * pattern), body), 0.0, 1.0);

    vec3 outColour = original.a > 0.001 ? mix(original.rgb * fillBrightness, acid, 0.5) : toxic * pattern;
    outColour = mix(outColour, acid, rim);

    fragColor = vec4(outColour, outAlpha);
}
