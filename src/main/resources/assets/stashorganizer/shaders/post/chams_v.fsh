#version 330

// Composite pass of the chams chain: finishes the vertical half of the dilation, then writes the
// result back into the entity outline target the game is about to draw. The glow comes from the
// widened mask and the fill from the untouched silhouette, so an entity with the glow switched off
// still keeps its solid fill.

uniform sampler2D InSampler;
uniform sampler2D ColorSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform ChamsConfig {
    float glowIntensity;
    float fillAlpha;
    int glowRadius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float glow = 0.0;
    vec3 color = vec3(0.0);

    for (int i = -glowRadius; i <= glowRadius; i++) {
        vec2 offset = vec2(0.0, float(i) * texel.y);
        float sample_alpha = texture(InSampler, texCoord + offset).a;

        if (sample_alpha > glow) {
            glow = sample_alpha;
            color = texture(ColorSampler, texCoord + offset).rgb;
        }
    }

    vec4 original = texture(OrigSampler, texCoord);

    // The silhouette itself keeps its own colour at full strength; the widened ring fades with the
    // configured intensity so a large radius still reads as a halo instead of a flat blob.
    vec3 outColor = original.a > 0.0 ? original.rgb : color;
    float outAlpha = max(original.a * fillAlpha, glow * glowIntensity);

    fragColor = vec4(outColor, clamp(outAlpha, 0.0, 1.0));
}
