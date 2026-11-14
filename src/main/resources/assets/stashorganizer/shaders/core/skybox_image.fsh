#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <stashorganizer:sky.glsl>

// Draws a user supplied PNG as an equirectangular panorama: u comes from the heading around the
// camera, v from the elevation, so a normal 2:1 panorama maps straight onto the sky without any
// cube faces. The texture is bound as Sampler0 by the render setup, not by a uniform block.

uniform sampler2D Sampler0;

in vec2 screen;
in vec4 tint;

out vec4 fragColor;

void main() {
    vec3 dir = sky_view_direction(screen);

    // A slow drift keeps a still image from looking like a screenshot pasted behind the world. The
    // speed arrives in the tint alpha, so the module's animation-speed setting drives it directly.
    float spin = GameTime * 0.004 * max(tint.a, 0.0);

    float heading = atan(dir.z, dir.x) / 6.28318530718 + 0.5 + spin;
    float wrapped = fract(heading);
    float elevation = acos(clamp(dir.y, -1.0, 1.0)) / 3.14159265359;

    vec3 sky = texture(Sampler0, vec2(wrapped, elevation)).rgb;

    // A panorama whose first and last column do not match leaves a hard line where the heading
    // wraps. Mix the two edge columns into a narrow band around the seam so the join fades instead
    // of jumping; the rest of the image is untouched.
    float edge = min(wrapped, 1.0 - wrapped);
    float seam = 1.0 - smoothstep(0.0, 0.01, edge);
    vec3 left = texture(Sampler0, vec2(0.0, elevation)).rgb;
    vec3 right = texture(Sampler0, vec2(1.0, elevation)).rgb;
    sky = mix(sky, mix(left, right, 0.5), seam);

    fragColor = vec4(sky * tint.rgb + sky_dither(gl_FragCoord.xy), 1.0);
}
