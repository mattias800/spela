#version 450
layout(set = 0, binding = 0) uniform sampler2D tex;
layout(push_constant) uniform PushConstants {
    vec2 texture_size;
    float flip_y;
    float _pad;
    vec2 output_size;
} pc;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(tex, uv);

    // Scanlines: darken between game pixel rows.
    // Use output_size so scanlines appear between game rows, not every output row.
    float game_row = uv.y * pc.texture_size.y;
    float pos_in_row = fract(game_row);

    // Darken the gap between rows (center of each game pixel is bright, edges dark)
    float scanline_mask = smoothstep(0.0, 0.35, pos_in_row) * smoothstep(1.0, 0.65, pos_in_row);
    scanline_mask = mix(0.7, 1.0, scanline_mask);

    color.rgb *= scanline_mask;

    fragColor = color;
}
