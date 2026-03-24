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
    vec2 texel = uv * pc.texture_size;
    vec2 texel_floor = floor(texel);
    vec2 s = fract(texel);

    // Sharp bilinear: clamp the fractional part to create sharp pixel edges
    // with smooth transitions at borders
    vec2 region_range = vec2(0.5) - vec2(0.5) / (pc.texture_size / vec2(textureSize(tex, 0)));
    vec2 center_dist = s - 0.5;
    vec2 f = (center_dist - clamp(center_dist, -region_range, region_range)) * 2.0 + 0.5;

    vec2 mod_texel = texel_floor + f;
    fragColor = texture(tex, mod_texel / pc.texture_size);
}
