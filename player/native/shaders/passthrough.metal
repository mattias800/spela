#include <metal_stdlib>
using namespace metal;

struct VertexOut {
    float4 position [[position]];
    float2 uv;
};

// Fullscreen triangle vertex shader (no vertex buffer needed)
vertex VertexOut vertex_main(uint vertexID [[vertex_id]]) {
    VertexOut out;
    float2 uv = float2((vertexID << 1) & 2, vertexID & 2);
    out.position = float4(uv * 2.0 - 1.0, 0.0, 1.0);
    out.uv = float2(uv.x, 1.0 - uv.y); // Flip Y for top-left origin
    return out;
}

// Passthrough: nearest-neighbor sampling, no effects
fragment float4 fragment_passthrough(VertexOut in [[stage_in]],
                                      texture2d<float> tex [[texture(0)]],
                                      sampler smp [[sampler(0)]]) {
    return tex.sample(smp, in.uv);
}
