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

void main() {
    vec3 dir = view_direction(screen);

    // Horizon at y = 0, zenith at y = 1, floor below the horizon kept flat and dark.
    float up = clamp(dir.y, 0.0, 1.0);
    float horizon = smoothstep(0.0, 0.22, up);

    vec3 low = vec3(0.95, 0.42, 0.18);
    vec3 mid = vec3(0.45, 0.14, 0.32);
    vec3 high = vec3(0.05, 0.05, 0.18);

    vec3 sky = mix(mix(low, mid, horizon), high, smoothstep(0.12, 0.85, up));

    // The sun sits on the +X horizon and only its halo is drawn; the vanilla disc is cancelled.
    float sun = pow(max(dot(dir, normalize(vec3(1.0, 0.12, 0.0))), 0.0), 24.0);
    sky += vec3(1.0, 0.75, 0.4) * sun * 0.8;

    float floor = smoothstep(0.0, -0.12, dir.y);
    sky = mix(sky, vec3(0.02, 0.02, 0.04), floor);

    fragColor = vec4(sky * tint.rgb, 1.0);
}
