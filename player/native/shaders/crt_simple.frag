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

    // Scanlines: darken between game pixel rows using output resolution
    float game_row = uv.y * pc.texture_size.y;
    float pos_in_row = fract(game_row);
    float scanline_mask = smoothstep(0.0, 0.3, pos_in_row) * smoothstep(1.0, 0.7, pos_in_row);
    scanline_mask = mix(0.6, 1.0, scanline_mask);
    color.rgb *= scanline_mask;

    // Radial vignette: darken edges
    vec2 center = uv - 0.5;
    float dist = length(center) * 1.414; // normalize so corners = 1.0
    float vignette = 1.0 - dist * dist * 0.5;
    color.rgb *= clamp(vignette, 0.0, 1.0);

    fragColor = color;
}
