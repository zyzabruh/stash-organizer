#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <stashorganizer:sky.glsl>

in vec2 screen;
in vec4 tint;

out vec4 fragColor;

float stars3(vec3 dir, float scale, float threshold) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    vec3 offset = vec3(sky_hash13(cell + 1.7), sky_hash13(cell + 9.3), sky_hash13(cell + 4.1));
    // The star sits in the direction of a random point inside its cell, so the field has no
    // longitude seam and no rows that pinch together at the poles.
    vec3 centre = normalize(cell + offset);
    float distance_to_star = length(dir - centre) * scale;
    float seed = sky_hash13(cell);

    return step(threshold, seed) * pow(clamp(1.0 - distance_to_star, 0.0, 1.0), 9.0);
}

void main() {
    vec3 dir = sky_view_direction(screen);
    float height = clamp(dir.y, -1.0, 1.0);

    float speed = max(tint.a, 0.0);
    float spin = GameTime * 0.012 * speed;

    // Rotate the direction instead of shifting a longitude: a shifted longitude wraps at the back
    // of the sky and leaves a straight seam there.
    float c = cos(spin);
    float s = sin(spin);
    vec3 spun = vec3(dir.x * c - dir.z * s, dir.y, dir.x * s + dir.z * c);
    float cloud = sky_bands3(spun * 3.0);

    // The galactic band is a slab of noise crossing the sky at an angle, with a dust lane in it.
    float slab = dot(dir, normalize(vec3(0.42, 0.38, -0.82)));
    float band = exp(-pow(slab / 0.24, 2.0)) * (0.35 + 0.65 * cloud);
    float lane = exp(-pow(slab / 0.05, 2.0)) * 0.5;

    vec3 base = mix(vec3(0.01, 0.012, 0.03), vec3(0.03, 0.04, 0.09), smoothstep(-0.3, 0.8, height));
    vec3 haze = mix(vec3(0.16, 0.2, 0.55), vec3(0.5, 0.32, 0.6), cloud) * band;
    haze *= 1.0 - lane;

    float twinkle = 0.75 + 0.25 * sin(GameTime * 2.0 + sky_noise3(dir * 30.0) * 6.2831853);
    float star = stars3(dir, 90.0, 0.972) + stars3(dir, 160.0, 0.985) * 0.7;
    star *= twinkle;

    vec3 sky = base + haze + vec3(0.9, 0.93, 1.0) * star;

    fragColor = vec4(sky * tint.rgb + sky_dither(gl_FragCoord.xy), 1.0);
}
