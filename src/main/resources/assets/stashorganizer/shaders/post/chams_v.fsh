#version 330

// Vertical half of the glow, then the composite written back into the entity outline target.
//
// The glow is the blurred alpha MINUS the silhouette: what is left is the ring outside the body.
// Without that subtraction the blur covers the interior too, and switching the glow on fills the
// character instead of drawing a halo around it. The fill is applied separately, from the untouched
// silhouette, so the two effects can be tuned and switched independently.

uniform sampler2D InSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ChamsConfig {
    int glowRadius;
    float glowIntensity;
    float fillAlpha;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / max(InSize, vec2(1.0));

    float radius = max(float(glowRadius), 1.0);
    float falloff = 0.5 / (radius * radius * 0.35);

    vec3 colorSum = vec3(0.0);
    float alphaSum = 0.0;
    float weightSum = 0.0;

    for (int i = -glowRadius; i <= glowRadius; i++) {
        float distance = float(i);
        float weight = exp(-distance * distance * falloff);
        vec4 sampleColor = texture(InSampler, texCoord + vec2(0.0, distance * texel.y));

        colorSum += sampleColor.rgb * sampleColor.a * weight;
        alphaSum += sampleColor.a * weight;
        weightSum += weight;
    }

    float blurred = weightSum > 0.0 ? alphaSum / weightSum : 0.0;
    vec3 blurredColor = alphaSum > 0.0001 ? colorSum / alphaSum : vec3(0.0);

    vec4 original = texture(OrigSampler, texCoord);

    float ring = max(blurred - original.a, 0.0);
    float outAlpha = clamp(ring * glowIntensity + original.a * fillAlpha, 0.0, 1.0);
    vec3 outColor = original.a > 0.001 ? original.rgb : blurredColor;

    fragColor = vec4(outColor, outAlpha);
}
