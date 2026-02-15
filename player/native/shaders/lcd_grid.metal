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

// LCD Grid: simulates LCD subpixel structure
fragment float4 fragment_lcd_grid(VertexOut in [[stage_in]],
                                   texture2d<float> tex [[texture(0)]],
                                   sampler smp [[sampler(0)]],
                                   constant PushConstants &pc [[buffer(0)]]) {
    float4 color = tex.sample(smp, in.uv);

    float2 grid_pos = fract(in.uv * pc.texture_size);

    float h_line = smoothstep(0.0, 0.05, grid_pos.y) * smoothstep(1.0, 0.95, grid_pos.y);
    float v_line = smoothstep(0.0, 0.05, grid_pos.x) * smoothstep(1.0, 0.95, grid_pos.x);

    float grid_mask = mix(0.75f, 1.0f, h_line * v_line);
    color.rgb *= grid_mask;

    return color;
}
