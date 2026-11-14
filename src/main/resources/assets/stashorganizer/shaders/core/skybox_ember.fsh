#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec2 screen;
in vec4 tint;

out vec4 fragColor;

vec3 view_direction(vec2 ndc) {
    mat4 inv = inverse(ProjMat * ModelViewMat);
    vec4 near = inv * vec4(ndc, -1.0, 1.0);
    vec4 far = inv * vec4(ndc, 1.0, 1.0);

    return normalize(far.xyz / far.w - near.xyz / near.w);
}

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

float bands(vec2 p) {
    float total = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 4; i++) {
        total += amplitude * noise(p);
        p *= 2.07;
        amplitude *= 0.5;
    }

    return total;
}

float sparks(vec3 dir, float scale, float rise, float threshold) {
    vec2 uv = vec2(atan(dir.z, dir.x) * 2.0, dir.y * 2.0);
    vec2 p = uv * scale;
    // Scrolling the field downwards through time lifts the embers into the sky.
    p.y += GameTime * rise;

    vec2 cell = floor(p);
    vec2 offset = vec2(hash(cell + 3.3), hash(cell + 11.9));
    float seed = hash(cell + 27.0);
    float distance_to_spark = length(fract(p) - offset);

    return step(threshold, seed) * pow(clamp(1.0 - distance_to_spark * 2.2, 0.0, 1.0), 5.0);
}

void main() {
    vec3 dir = view_direction(screen);
    float up = clamp(dir.y, 0.0, 1.0);
    float speed = max(tint.a, 0.0);

    // Heat pools at the horizon and the sky above it turns to cooled basalt.
    vec3 base = mix(vec3(0.16, 0.03, 0.01), vec3(0.03, 0.01, 0.02), smoothstep(0.0, 0.55, up));
    float glow = exp(-up * 5.0) * 0.6;
    vec3 sky = base + vec3(0.9, 0.3, 0.06) * glow;

    float ember = sparks(dir, 26.0, 0.35 * speed, 0.955);
    ember += sparks(dir, 55.0, 0.15 * speed, 0.93) * 0.6;
    sky += mix(vec3(1.0, 0.55, 0.12), vec3(1.0, 0.85, 0.4), hash(floor(dir.xz * 30.0))) * ember;

    sky += vec3(0.35, 0.08, 0.02) * bands(vec2(dir.x, dir.z) * 3.0 + GameTime * 0.02 * speed) * 0.25;

    fragColor = vec4(sky * tint.rgb, 1.0);
}
