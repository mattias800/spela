#version 450
layout(push_constant) uniform PushConstants {
    vec2 texture_size;
    float flip_y;
    float _pad;
    vec2 output_size;
} pc;
layout(location = 0) out vec2 uv;
void main() {
    // Fullscreen triangle using vertex index
    uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    // flip_y=1.0: flip for software-uploaded textures (top-left origin)
    // flip_y=0.0: no flip for HW render cores (Vulkan native)
    if (pc.flip_y > 0.5) {
        uv.y = 1.0 - uv.y;
    }
}
