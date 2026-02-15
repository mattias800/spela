#include <metal_stdlib>
using namespace metal;

struct VertexOut {
    float4 position [[position]];
    float2 uv;
};

struct PushConstants {
    float2 texture_size;
};

vertex VertexOut vertex_main(uint vertexID [[vertex_id]]) {
    VertexOut out;
    float2 uv = float2((vertexID << 1) & 2, vertexID & 2);
    out.position = float4(uv * 2.0 - 1.0, 0.0, 1.0);
    out.uv = float2(uv.x, 1.0 - uv.y);
    return out;
}

fragment float4 fragment_sharp_bilinear(VertexOut in [[stage_in]],
                                         texture2d<float> tex [[texture(0)]],
                                         sampler smp [[sampler(0)]],
                                         constant PushConstants &pc [[buffer(0)]]) {
    float2 texel = in.uv * pc.texture_size;
    float2 texel_floor = floor(texel);
    float2 s = fract(texel);

    float2 tex_size = float2(tex.get_width(), tex.get_height());
    float2 region_range = float2(0.5) - float2(0.5) / (pc.texture_size / tex_size);
    float2 center_dist = s - 0.5;
    float2 f = (center_dist - clamp(center_dist, -region_range, region_range)) * 2.0 + 0.5;

    float2 mod_texel = texel_floor + f;
    return tex.sample(smp, mod_texel / pc.texture_size);
}
