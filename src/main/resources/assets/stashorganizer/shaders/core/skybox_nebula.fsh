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
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
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

float clouds(vec2 p) {
    float total = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 5; i++) {
        total += amplitude * noise(p);
        p *= 2.11;
        amplitude *= 0.5;
    }

    return total;
}

void main() {
    vec3 dir = view_direction(screen);
    float height = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    vec2 plane = dir.xz / max(abs(dir.y) + 0.25, 0.001);
    float time = GameTime * 0.02 * max(tint.a, 0.0);

    float low = clouds(plane * 1.7 + vec2(time, 0.0));
    float high = clouds(plane * 3.4 - vec2(0.0, time * 0.8));

    vec3 base = mix(vec3(0.03, 0.0, 0.06), vec3(0.05, 0.02, 0.12), height);
    vec3 violet = vec3(0.55, 0.15, 0.65) * pow(low, 1.7);
    vec3 teal = vec3(0.1, 0.35, 0.6) * pow(high, 2.4);

    // Stars are a sparse threshold on the same hash, so they never move with the clouds.
    vec2 star_plane = floor(plane * 180.0);
    float star = step(0.9985, hash(star_plane)) * smoothstep(0.05, 0.4, dir.y);

    fragColor = vec4((base + violet + teal + star) * tint.rgb, 1.0);
}
