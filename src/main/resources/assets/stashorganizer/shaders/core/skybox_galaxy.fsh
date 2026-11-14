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

float stars(vec2 uv, float scale, float threshold) {
    vec2 p = uv * scale;
    vec2 cell = floor(p);
    vec2 offset = vec2(hash(cell + 1.7), hash(cell + 9.3));
    float seed = hash(cell);

    return step(threshold, seed) * pow(clamp(1.0 - length(fract(p) - offset), 0.0, 1.0), 9.0);
}

void main() {
    vec3 dir = view_direction(screen);
    float height = clamp(dir.y, -1.0, 1.0);

    // Spherical coordinates keep the stars the same size wherever the camera looks.
    vec2 sphere = vec2(atan(dir.z, dir.x), asin(height));
    float speed = max(tint.a, 0.0);
    float spin = GameTime * 0.012 * speed;

    // The galactic band is a slab of noise crossing the sky at an angle, with a dust lane in it.
    float slab = dot(dir, normalize(vec3(0.42, 0.38, -0.82)));
    float cloud = bands(vec2(sphere.x * 1.6 + spin, sphere.y * 2.2) * 1.1);
    float band = exp(-pow(slab / 0.24, 2.0)) * (0.35 + 0.65 * cloud);
    float lane = exp(-pow(slab / 0.05, 2.0)) * 0.5;

    vec3 base = mix(vec3(0.01, 0.012, 0.03), vec3(0.03, 0.04, 0.09), smoothstep(-0.3, 0.8, height));
    vec3 haze = mix(vec3(0.16, 0.2, 0.55), vec3(0.5, 0.32, 0.6), cloud) * band;
    haze *= 1.0 - lane;

    float twinkle = 0.75 + 0.25 * sin(GameTime * 2.0 + hash(floor(sphere * 40.0)) * 6.28);
    float star = stars(sphere, 90.0, 0.972) + stars(sphere + 3.1, 160.0, 0.985) * 0.7;
    star *= twinkle;

    vec3 sky = base + haze + vec3(0.9, 0.93, 1.0) * star;

    fragColor = vec4(sky * tint.rgb, 1.0);
}
