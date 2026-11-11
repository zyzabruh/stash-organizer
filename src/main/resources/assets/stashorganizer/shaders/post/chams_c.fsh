#version 330

// Colour pass of the chams chain: carries the silhouette colour outwards alongside the mask, so the
// glow that the composite pass paints keeps the outline colour of the entity it belongs to instead
// of a flat tint. The colour with the strongest alpha in the row wins.

uniform sampler2D InSampler;

layout(std140) uniform ColorConfig {
    int glowRadius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec4 best = vec4(0.0);

    for (int i = -glowRadius; i <= glowRadius; i++) {
        vec4 sample_color = texture(InSampler, texCoord + vec2(float(i) * texel.x, 0.0));
        if (sample_color.a > best.a) best = sample_color;
    }

    fragColor = best;
}
