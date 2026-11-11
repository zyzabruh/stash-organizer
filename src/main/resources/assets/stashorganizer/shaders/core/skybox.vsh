#version 330

in vec3 Position;
in vec4 Color;

out vec2 screen;
out vec4 tint;

void main() {
    // The quad is already in clip space: x and y are the NDC corners, so the
    // fragment shader can turn them back into a view direction.
    screen = Position.xy;
    tint = Color;
    gl_Position = vec4(Position.xy, 1.0, 1.0);
}
