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
    float speed = max(tint.a, 0.0);
    float drift = GameTime * 0.05 * speed;

    // Sampling the swell in three dimensions keeps the pattern attached to the compass without
    // the longitude seam that atan() leaves behind the camera.
    vec3 point = vec3(dir.x, dir.y * 0.45, dir.z) * 3.0;
    float swell = sky_bands3(point + vec3(drift, -drift * 0.3, drift * 0.2));
    float chop = sky_bands3(point * 2.4 - vec3(drift * 1.7, drift * 0.6, 0.0));
    float crest = smoothstep(0.55, 0.85, swell * 0.7 + chop * 0.45);

    vec3 deep = vec3(0.01, 0.05, 0.11);
    vec3 mid = vec3(0.03, 0.22, 0.34);
    vec3 foam = vec3(0.62, 0.9, 0.95);

    // The gradient keeps going below the horizon instead of flattening at y = 0, which is what
    // drew a straight line across the water at eye level.
    vec3 sky = mix(deep, mid, smoothstep(-0.35, 0.75, dir.y));
    float surface = smoothstep(-0.1, 0.5, dir.y);
    sky = mix(sky, foam, crest * (1.0 - surface) * 0.55);

    float sun = pow(max(dot(dir, normalize(vec3(0.3, 0.55, 0.78))), 0.0), 90.0);
    sky += vec3(1.0, 0.95, 0.8) * sun;

    fragColor = vec4(sky * tint.rgb + sky_dither(gl_FragCoord.xy), 1.0);
}
