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

// Scanlines: simple horizontal scanline darkening
fragment float4 fragment_scanlines(VertexOut in [[stage_in]],
                                    texture2d<float> tex [[texture(0)]],
                                    sampler smp [[sampler(0)]],
                                    constant PushConstants &pc [[buffer(0)]]) {
    float4 color = tex.sample(smp, in.uv);

    float scanline = floor(in.uv.y * pc.texture_size.y);
    float scanline_mask = 1.0 - 0.25 * fmod(scanline, 2.0);
    color.rgb *= scanline_mask;

    return color;
}
