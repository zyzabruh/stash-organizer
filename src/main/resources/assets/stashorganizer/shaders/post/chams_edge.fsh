#version 330

// Crisp edge outline. No gaussian at all: the silhouette is sampled in eight directions at an exact
// pixel distance, twice — at the integer part of the width and one pixel further — and the two are
// mixed by the fractional part. That gives a rim whose thickness is a real pixel count with a
// one-pixel anti-aliased outer edge, which a blur cannot produce at any radius.
//
// The colour is carried by the same taps: the outline target holds one flat colour per entity, so a
// tap that lands on the body also brings the colour to paint the rim with.

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

/** Averages eight taps on a circle. */
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
    float whole = floor(width);
    float fraction = width - whole;

    vec4 inner = ring(texel * whole);
    vec4 outer = ring(texel * (whole + 1.0));
    vec4 band = mix(inner, outer, fraction);

    float rim = clamp(band.a - original.a, 0.0, 1.0);
    vec3 rimColour = band.a > 0.001 ? band.rgb / band.a : vec3(0.0);

    float body = original.a * fillAlpha;
    float outAlpha = clamp(max(rim, body), 0.0, 1.0);
    vec3 outColour = original.a > 0.001 ? original.rgb * fillBrightness : rimColour;

    outColour = mix(outColour, rimColour, rim * clamp(glowIntensity, 0.0, 1.0));

    fragColor = vec4(outColour, outAlpha);
}
