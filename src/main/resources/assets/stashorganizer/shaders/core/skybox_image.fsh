#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Draws a user supplied PNG as an equirectangular panorama: u comes from the heading around the
// camera, v from the elevation, so a normal 2:1 panorama maps straight onto the sky without any
// cube faces. The texture is bound as Sampler0 by the render setup, not by a uniform block.

uniform sampler2D Sampler0;

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

    // A slow drift keeps a still image from looking like a screenshot pasted behind the world. The
    // speed arrives in the tint alpha, so the module's animation-speed setting drives it directly.
    float spin = GameTime * 0.004 * max(tint.a, 0.0);

    float heading = fract(atan(dir.z, dir.x) / 6.28318530718 + 0.5 + spin);
    float elevation = acos(clamp(dir.y, -1.0, 1.0)) / 3.14159265359;

    vec3 sky = texture(Sampler0, vec2(heading, elevation)).rgb;

    fragColor = vec4(sky * tint.rgb, 1.0);
}
