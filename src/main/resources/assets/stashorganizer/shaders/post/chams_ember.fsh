#version 330

// Ember. The colours ignore the silhouette and come from a fractal noise field sampled in screen
// space, between deep ember red and white-hot yellow: the rim looks like a cooling lava edge and the
// body like something still burning.
//
// The halo is the strongest of the theme shaders and is taken from the same field, so the glow
// around the model moves with the veins instead of sitting on top as a uniform ring.

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

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 frac = fract(p);
    // "smooth" is a reserved GLSL qualifier and cannot name a variable.
    vec2 ease = frac * frac * (3.0 - 2.0 * frac);

    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));

    return mix(mix(a, b, ease.x), mix(c, d, ease.x), ease.y);
}

float veins(vec2 p) {
    float total = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 3; i++) {
        total += amplitude * noise(p);
        p *= 2.13;
        amplitude *= 0.5;
    }

    return total;
}

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
    vec2 screen = texCoord * vec2(textureSize(InSampler, 0));

    float width = max(float(outlineWidth), 1.0);
    vec4 band = ring(texel * width);
    float rim = clamp(band.a - original.a, 0.0, 1.0);

    float heat = clamp(veins(screen * 0.02) * 1.4, 0.0, 1.0);

    vec3 fire = mix(vec3(1.0, 0.35, 0.05), vec3(1.0, 0.85, 0.35), heat);
    fire = mix(fire, vec3(1.0), pow(heat, 4.0) * 0.7);

    float radius = max(float(glowRadius), 1.0);
    vec4 spread = ring(texel * radius);
    float halo = clamp(pow(max(spread.a - original.a, 0.0), 0.6) * glowIntensity, 0.0, 1.0);

    float body = original.a * clamp(fillAlpha + spread.a * innerGlow, 0.0, 1.0);
    float outAlpha = clamp(max(max(rim, halo), body), 0.0, 1.0);

    vec3 inside = mix(original.rgb * fillBrightness, mix(vec3(0.35, 0.06, 0.03), fire, heat), 0.6);
    vec3 outColour = original.a > 0.001 ? inside : fire;

    fragColor = vec4(outColour, outAlpha);
}
