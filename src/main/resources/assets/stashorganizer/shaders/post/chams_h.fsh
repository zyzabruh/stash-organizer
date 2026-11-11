#version 330

// Horizontal half of the glow. The silhouette is blurred with a gaussian kernel and kept
// premultiplied: colour is accumulated weighted by alpha and divided back at the end, so the halo
// that reaches the composite keeps the outline colour instead of fading towards black.
//
// The texel size comes from textureSize rather than from the SamplerInfo block. SamplerInfo is
// filled by the game for its own effects, but a pipeline built in code has no guarantees about it,
// and an empty InSize turns every offset into a whole screen: the halo then samples pixels that hold
// nothing and the result is invisible.

uniform sampler2D InSampler;

layout(std140) uniform DilateConfig {
    int glowRadius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / max(vec2(textureSize(InSampler, 0)), vec2(1.0));

    // A narrow kernel for a small radius keeps the halo close to the silhouette; widening the
    // radius softens it instead of turning it into a hard ring.
    float radius = max(float(glowRadius), 1.0);
    float falloff = 0.5 / (radius * radius * 0.35);

    vec3 colorSum = vec3(0.0);
    float alphaSum = 0.0;
    float weightSum = 0.0;

    for (int i = -glowRadius; i <= glowRadius; i++) {
        float distance = float(i);
        float weight = exp(-distance * distance * falloff);
        vec4 sampleColor = texture(InSampler, texCoord + vec2(distance * texel.x, 0.0));

        colorSum += sampleColor.rgb * sampleColor.a * weight;
        alphaSum += sampleColor.a * weight;
        weightSum += weight;
    }

    float alpha = weightSum > 0.0 ? alphaSum / weightSum : 0.0;
    vec3 color = alphaSum > 0.0001 ? colorSum / alphaSum : vec3(0.0);

    fragColor = vec4(color, alpha);
}
