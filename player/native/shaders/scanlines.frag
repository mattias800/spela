#version 450
layout(set = 0, binding = 0) uniform sampler2D tex;
layout(push_constant) uniform PushConstants {
    vec2 texture_size;
} pc;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(tex, uv);

    // Scanlines: darken every other pixel row
    float scanline = floor(uv.y * pc.texture_size.y);
    float scanline_mask = 1.0 - 0.25 * mod(scanline, 2.0);
    color.rgb *= scanline_mask;

    fragColor = color;
}
