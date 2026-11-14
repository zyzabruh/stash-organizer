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

void main() {
    vec3 dir = view_direction(screen);
    float up = clamp(dir.y, 0.0, 1.0);
    float speed = max(tint.a, 0.0);
    float drift = GameTime * 0.05 * speed;

    // Horizontal angle keeps the swells attached to the compass, not to the camera.
    vec2 uv = vec2(atan(dir.z, dir.x) * 1.4, up * 3.0);

    // Two swells crossing at different speeds read as water rather than as fog.
    float swell = bands(uv * vec2(2.0, 3.0) + vec2(drift, -drift * 0.3));
    float chop = bands(uv * vec2(5.0, 6.0) - vec2(drift * 1.7, drift * 0.6));
    float crest = smoothstep(0.55, 0.85, swell * 0.7 + chop * 0.45);

    vec3 deep = vec3(0.01, 0.05, 0.11);
    vec3 mid = vec3(0.03, 0.22, 0.34);
    vec3 foam = vec3(0.62, 0.9, 0.95);

    vec3 sky = mix(deep, mid, smoothstep(0.0, 0.7, up));
    sky = mix(sky, foam, crest * (1.0 - up) * 0.55);

    float sun = pow(max(dot(dir, normalize(vec3(0.3, 0.55, 0.78))), 0.0), 90.0);
    sky += vec3(1.0, 0.95, 0.8) * sun;

    fragColor = vec4(sky * tint.rgb, 1.0);
}
