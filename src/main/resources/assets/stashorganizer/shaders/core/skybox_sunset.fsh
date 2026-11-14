#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <stashorganizer:sky.glsl>

in vec2 screen;
in vec4 tint;

out vec4 fragColor;

void main() {
    vec3 dir = sky_view_direction(screen);

    // Horizon at y = 0 and zenith at y = 1; the gradient is not clamped at the horizon, so the
    // sky keeps changing smoothly below it instead of stopping on a straight line.
    float up = dir.y;
    float horizon = smoothstep(0.0, 0.22, up);

    vec3 low = vec3(0.95, 0.42, 0.18);
    vec3 mid = vec3(0.45, 0.14, 0.32);
    vec3 high = vec3(0.05, 0.05, 0.18);

    vec3 sky = mix(mix(low, mid, horizon), high, smoothstep(0.12, 0.85, up));

    // The sun sits on the +X horizon and only its halo is drawn; the vanilla disc is cancelled.
    float sun = pow(max(dot(dir, normalize(vec3(1.0, 0.12, 0.0))), 0.0), 24.0);
    sky += vec3(1.0, 0.75, 0.4) * sun * 0.8;

    // The lower half darkens over a wide band rather than switching at y = 0.
    float down = 1.0 - smoothstep(-0.55, 0.0, up);
    sky = mix(sky, vec3(0.02, 0.02, 0.04), down);

    fragColor = vec4(sky * tint.rgb + sky_dither(gl_FragCoord.xy), 1.0);
}
