#version 330

// Rainbow gradient. The colour is computed here instead of being read from the silhouette: the hue
// comes from the position on screen, so a single body shows a whole spectrum rather than one flat
// tint, and two entities never line up on the same colour by accident.
//
// The rim is the eight-direction dilation of the crisp edge pass, the halo is a single wide tap, and
// both are painted with the same gradient so the two layers read as one effect.

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

/** Fully saturated hue. */
vec3 hue_colour(float hue) {
    vec3 channels = abs(fract(vec3(hue) + vec3(0.0, 0.66666667, 0.33333333)) * 6.0 - 3.0);
    return clamp(channels - 1.0, 0.0, 1.0);
}

/** Averages eight taps on a circle, returning the premultiplied colour and alpha. */
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

    vec3 gradient = hue_colour(fract(texCoord.x * 0.45 + texCoord.y * 0.25));

    float width = max(float(outlineWidth), 1.0);
    vec4 band = ring(texel * width);
    float rim = clamp(band.a - original.a, 0.0, 1.0);

    float radius = max(float(glowRadius), 1.0);
    vec4 spread = ring(texel * radius);
    float halo = clamp(pow(max(spread.a - original.a, 0.0), 0.7) * glowIntensity, 0.0, 1.0);

    float body = original.a * clamp(fillAlpha + spread.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, halo), body), 0.0, 1.0);

    vec3 outColour = gradient * fillBrightness;

    fragColor = vec4(outColour, outAlpha);
}
