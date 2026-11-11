#version 330

// Horizontal pass of the chams chain: widens the entity silhouette sideways and keeps its alpha as
// a mask. The vertical half of the same dilation happens in the composite pass, so the two passes
// cover a square neighbourhood while sampling each pixel once per axis.

uniform sampler2D InSampler;

layout(std140) uniform DilateConfig {
    int glowRadius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float mask = 0.0;

    for (int i = -glowRadius; i <= glowRadius; i++) {
        float sample_alpha = texture(InSampler, texCoord + vec2(float(i) * texel.x, 0.0)).a;
        mask = max(mask, sample_alpha);
    }

    fragColor = vec4(mask);
}
