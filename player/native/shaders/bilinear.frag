#version 450
// Bilinear filtering is set on the VkSampler, not in the shader.
// This shader is identical to passthrough but used with a linear sampler.
layout(set = 0, binding = 0) uniform sampler2D tex;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = texture(tex, uv);
}
