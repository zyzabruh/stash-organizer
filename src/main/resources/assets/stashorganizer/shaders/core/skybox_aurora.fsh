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
    vec2 smooth = frac * frac * (3.0 - 2.0 * frac);

    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));

    return mix(mix(a, b, smooth.x), mix(c, d, smooth.x), smooth.y);
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

void main() {
    vec3 dir = view_direction(screen);
    float height = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    vec2 uv = vec2(dir.x, dir.z) / max(abs(dir.y) + 0.35, 0.01);
    float drift = GameTime * 0.03;
    float ribbon = bands(uv * 1.4 + vec2(drift, drift * 0.5));
    ribbon = pow(clamp(ribbon * 1.6 - 0.35, 0.0, 1.0), 1.8);

    vec3 sky = mix(vec3(0.02, 0.03, 0.06), vec3(0.03, 0.09, 0.2), smoothstep(0.35, 1.0, height));
    vec3 glow = vec3(0.1, 0.85, 0.45) * ribbon;

    fragColor = vec4((sky + glow) * tint.rgb, 1.0);
}
