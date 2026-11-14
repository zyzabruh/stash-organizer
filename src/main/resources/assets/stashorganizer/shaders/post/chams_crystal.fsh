#version 330

// Crystal. The silhouette is not read where the pixel is: the coordinates are pushed around by a
// radial ripple first, then the red, green and blue channels are sampled at three slightly
// different offsets. That is refraction — a wavy outline with a colour fringe on its edge — and it
// is the only style whose shape depends on something other than the silhouette's own alpha.
//
// The sample that ends up used is the strongest of the three, so the fringe never eats the body.

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

    // Radial ripple: the displacement grows with the distance from the screen centre, so the body
    // bends like glass instead of jittering uniformly.
    vec2 direction = texCoord - vec2(0.5);
    float ripple = sin(length(direction) * 240.0) * 0.0025 * max(float(glowRadius), 1.0);
    vec2 warped = texCoord + direction * ripple;

    vec4 red = texture(InSampler, warped + texel * 1.5);
    vec4 green = texture(InSampler, warped);
    vec4 blue = texture(InSampler, warped - texel * 1.5);

    float alpha = max(red.a, max(green.a, blue.a));
    float rim = clamp(alpha - original.a, 0.0, 1.0);

    float radius = max(float(glowRadius), 1.0);
    vec4 spread = ring(texel * radius);
    float halo = clamp(pow(max(spread.a - original.a, 0.0), 0.7) * glowIntensity, 0.0, 1.0);

    // Prism: the fringe colour comes from the three channel alphas themselves.
    vec3 fringe = vec3(red.a, green.a, blue.a);
    fringe /= max(max(fringe.r, max(fringe.g, fringe.b)), 0.001);
    fringe = mix(fringe, vec3(0.75, 0.9, 1.0), 0.35);

    float body = original.a * clamp(fillAlpha + spread.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, halo), body), 0.0, 1.0);

    vec3 inside = original.rgb * fillBrightness;
    vec3 outColour = original.a > 0.001 ? mix(inside, fringe, 0.45) : fringe;

    fragColor = vec4(outColour, outAlpha);
}
