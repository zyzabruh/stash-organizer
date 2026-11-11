#version 330

// Vertical half of the glow, then the composite written back into the entity outline target.
//
// Three layers, each independent of the others:
//   - the fill, from the untouched silhouette;
//   - a solid rim, from the silhouette widened by a few texels in the composite itself;
//   - the halo, from the blurred alpha MINUS the silhouette, so it stays a ring outside the body.
// Subtracting the silhouette matters: the blurred mask is full inside the body too, and using it
// directly paints the interior instead of drawing a halo around it.
//
// The halo is raised to a power below one before it is scaled, so a thin ring stays visible instead
// of washing out to a few percent of alpha. The alpha is then dithered by a fraction of one colour
// step, because a soft gradient over a large area bands visibly on an 8-bit target.
//
// The texel size comes from textureSize, not from SamplerInfo: a pipeline built in code has no
// guarantee that block is filled, and an empty InSize makes every offset a whole screen.

uniform sampler2D InSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform ChamsConfig {
    int outlineWidth;
    int glowRadius;
    float glowIntensity;
    float fillAlpha;
    float fillBrightness;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / max(vec2(textureSize(OrigSampler, 0)), vec2(1.0));

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

    // Dilated here rather than in the blur passes: a handful of extra reads buys a crisp edge of an
    // exact pixel count, which a gaussian cannot give at any radius. Diagonals are sampled too,
    // otherwise a one pixel rim has gaps on slopes.
    float widened = original.a;
    float step_px = float(outlineWidth);

    if (step_px > 0.0) {
        vec2 offset = texel * step_px;
        vec2 diagonal = offset * 0.7071;

        widened = max(widened, texture(OrigSampler, texCoord + vec2(offset.x, 0.0)).a);
        widened = max(widened, texture(OrigSampler, texCoord - vec2(offset.x, 0.0)).a);
        widened = max(widened, texture(OrigSampler, texCoord + vec2(0.0, offset.y)).a);
        widened = max(widened, texture(OrigSampler, texCoord - vec2(0.0, offset.y)).a);
        widened = max(widened, texture(OrigSampler, texCoord + vec2(diagonal.x, diagonal.y)).a);
        widened = max(widened, texture(OrigSampler, texCoord - vec2(diagonal.x, diagonal.y)).a);
        widened = max(widened, texture(OrigSampler, texCoord + vec2(diagonal.x, -diagonal.y)).a);
        widened = max(widened, texture(OrigSampler, texCoord - vec2(diagonal.x, -diagonal.y)).a);
    }

    float rim = max(widened - original.a, 0.0);
    float halo = clamp(pow(max(blurred - original.a, 0.0), 0.7) * glowIntensity, 0.0, 1.0);

    float outAlpha = clamp(max(rim, halo) + original.a * fillAlpha, 0.0, 1.0);
    vec3 outColor = original.a > 0.001 ? original.rgb * fillBrightness : blurredColor;

    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    outAlpha = clamp(outAlpha + (dither - 0.5) / 255.0, 0.0, 1.0);

    fragColor = vec4(outColor, outAlpha);
}
