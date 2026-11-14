#version 330

// Pixel. The whole effect is evaluated at block resolution: the screen is snapped to a grid whose
// cell is derived from the glow radius, the silhouette is read once per cell, and the rim is one
// cell wide instead of one pixel. The edge is then dithered with interleaved gradient noise, so it
// reads as a staircase of pixels rather than as an anti-aliased line.
//
// This is the only style that survives being looked at from very close: at block resolution the
// silhouette is still a silhouette, where the smooth shaders turn into a fog of texels.

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

void main() {
    vec2 size = max(vec2(textureSize(InSampler, 0)), vec2(1.0));
    vec4 original = texture(InSampler, texCoord);

    float cell = max(floor(max(float(glowRadius), 1.0) * 0.75), 1.0);
    vec2 grid = floor(gl_FragCoord.xy / cell);
    vec2 centre = (grid * cell + cell * 0.5) / size;

    vec4 snap = texture(InSampler, centre);

    // One cell outward in four directions: the block equivalent of a one pixel dilation.
    vec2 stride = vec2(cell) / size;
    float spread = max(
        max(texture(InSampler, centre + vec2(stride.x, 0.0)).a, texture(InSampler, centre - vec2(stride.x, 0.0)).a),
        max(texture(InSampler, centre + vec2(0.0, stride.y)).a, texture(InSampler, centre - vec2(0.0, stride.y)).a)
    );

    float rim = clamp(spread - snap.a, 0.0, 1.0);
    float dither = fract(52.9829189 * fract(0.06711056 * gl_FragCoord.x + 0.00583715 * gl_FragCoord.y));

    // Block hue, so neighbouring cells read as separate tiles rather than as one flat band.
    vec3 tile = vec3(
        0.35 + 0.65 * hash(grid),
        0.55 + 0.45 * hash(grid + 3.7),
        0.75 + 0.25 * hash(grid + 9.1)
    );

    float body = snap.a * fillAlpha;
    float outAlpha = clamp(max(rim * step(dither, 0.85), body), 0.0, 1.0);

    vec3 outColour = original.a > 0.001 ? original.rgb * fillBrightness : tile * glowIntensity;

    fragColor = vec4(outColour, outAlpha);
}
