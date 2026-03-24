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

    // LCD dot matrix grid — darken edges of each game pixel
    vec2 pixelPos = fract(uv * pc.texture_size);

    float edgeX = 0.1;
    float edgeY = 0.15;
    float px = smoothstep(0.0, edgeX, pixelPos.x) * smoothstep(1.0, 1.0 - edgeX, pixelPos.x);
    float py = smoothstep(0.0, edgeY, pixelPos.y) * smoothstep(1.0, 1.0 - edgeY, pixelPos.y);
    float grid = mix(0.5, 1.0, px * py);
    color.rgb *= grid;

    fragColor = color;
}
