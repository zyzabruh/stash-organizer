#version 330

// Copies the finished effect back into the entity outline target.
//
// The single-pass effects cannot read and write the outline target in the same draw, so they write
// their result into the mask target and this pass moves it across. Straight copy, alpha included:
// the game composites the outline target as premultiplied coverage.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(InSampler, texCoord);
}
