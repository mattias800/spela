#version 450
layout(set = 0, binding = 0) uniform sampler2D tex;
layout(push_constant) uniform PushConstants {
    vec2 texture_size;
} pc;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(tex, uv);

    // Scanlines: darken every other pixel row based on source resolution
    float scanline = floor(uv.y * pc.texture_size.y);
    float scanline_mask = 1.0 - 0.35 * mod(scanline, 2.0);
    color.rgb *= scanline_mask;

    // Radial vignette: darken edges
    vec2 center = uv - 0.5;
    float dist = length(center) * 1.414; // normalize so corners = 1.0
    float vignette = 1.0 - dist * dist * 0.5;
    color.rgb *= clamp(vignette, 0.0, 1.0);

    fragColor = color;
}
