#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Storm: slow dark cloud layers drifting overhead, with a rare flash that lights the cloud tops.
// The flash comes from a hash on a coarse time cell, so it is not periodic in a way the eye catches
// and it costs nothing when no cell is lit.

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
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
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

float fbm(vec2 p) {
    float total = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 5; i++) {
        total += amplitude * noise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }

    return total;
}

void main() {
    vec3 dir = view_direction(screen);
    float up = clamp(dir.y, 0.0, 1.0);
    float speed = max(tint.a, 0.0);

    vec2 plane = dir.xz / max(up + 0.3, 0.001);
    float time = GameTime * 0.6 * speed;

    float cloud = fbm(plane * 1.5 + vec2(time, time * 0.35));
    float heavy = smoothstep(0.34, 0.86, cloud);

    vec3 clear = mix(vec3(0.05, 0.06, 0.09), vec3(0.13, 0.15, 0.21), up);
    vec3 storm = vec3(0.025, 0.028, 0.045);

    vec3 sky = mix(clear, storm, heavy * 0.9);

    // One coarse cell every few seconds may light up; the flash decays within the same cell.
    float cellTime = time * 0.9;
    float cell = hash(vec2(floor(cellTime), floor(cellTime * 0.37)));
    float flash = step(0.972, cell) * (1.0 - fract(cellTime)) * smoothstep(0.55, 0.95, cloud);
    sky += vec3(0.55, 0.6, 0.85) * flash;

    float below = smoothstep(0.0, -0.14, dir.y);
    sky = mix(sky, vec3(0.02, 0.02, 0.03), below);

    fragColor = vec4(sky * tint.rgb, 1.0);
}
