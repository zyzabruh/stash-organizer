#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Twilight: a two tone gradient with slow veils of haze crossing it. Cheaper than the nebula on
// purpose, three octaves and no stars, so it reads as calm instead of busy.

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
    return fract(sin(dot(p, vec2(269.5, 183.3))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 frac = fract(p);
    vec2 ease = frac * frac * (3.0 - 2.0 * frac);

    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));

    return mix(mix(a, b, ease.x), mix(c, d, ease.x), ease.y);
}

void main() {
    vec3 dir = view_direction(screen);
    float up = clamp(dir.y, 0.0, 1.0);
    float speed = max(tint.a, 0.0);

    vec3 horizon = vec3(0.98, 0.55, 0.42);
    vec3 middle = vec3(0.42, 0.22, 0.52);
    vec3 zenith = vec3(0.05, 0.07, 0.22);

    vec3 sky = mix(horizon, middle, smoothstep(0.0, 0.35, up));
    sky = mix(sky, zenith, smoothstep(0.3, 0.95, up));

    // Veils sweep sideways at different speeds, which is what sells the depth.
    vec2 plane = dir.xz / max(up + 0.4, 0.001);
    float time = GameTime * 0.05 * speed;

    float lowVeil = noise(plane * 1.1 + vec2(time, 0.0));
    float highVeil = noise(plane * 2.4 - vec2(time * 0.7, time * 0.3));

    sky += vec3(0.28, 0.10, 0.18) * lowVeil * (1.0 - up) * 0.55;
    sky += vec3(0.10, 0.16, 0.30) * highVeil * up * 0.35;

    float below = smoothstep(0.0, -0.12, dir.y);
    sky = mix(sky, vec3(0.02, 0.02, 0.04), below);

    fragColor = vec4(sky * tint.rgb, 1.0);
}
