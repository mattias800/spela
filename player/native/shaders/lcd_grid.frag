#version 450
layout(set = 0, binding = 0) uniform sampler2D tex;
layout(push_constant) uniform PushConstants {
    vec2 texture_size;
    float flip_y;
} pc;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(tex, uv);

    // LCD grid: darken pixel edges to simulate LCD subpixel structure
    vec2 grid_pos = fract(uv * pc.texture_size);

    // Horizontal grid lines
    float h_line = smoothstep(0.0, 0.05, grid_pos.y) * smoothstep(1.0, 0.95, grid_pos.y);
    // Vertical grid lines
    float v_line = smoothstep(0.0, 0.05, grid_pos.x) * smoothstep(1.0, 0.95, grid_pos.x);

    float grid_mask = mix(0.75, 1.0, h_line * v_line);
    color.rgb *= grid_mask;

    fragColor = color;
}
