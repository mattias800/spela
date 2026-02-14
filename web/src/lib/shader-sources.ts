export const VERTEX_SHADER = `#version 300 es
out vec2 v_texCoord;
void main() {
  // Fullscreen triangle from gl_VertexID — covers clip space with 3 vertices, no buffer
  vec2 pos = vec2(
    float((gl_VertexID & 1) * 4 - 1),
    float((gl_VertexID >> 1) * 4 - 1)
  );
  // Flip Y so texCoord (0,0)=top-left, (1,1)=bottom-right (matches screen/image coords)
  v_texCoord = vec2(pos.x * 0.5 + 0.5, 0.5 - pos.y * 0.5);
  gl_Position = vec4(pos, 0.0, 1.0);
}`;

const PASSTHROUGH_FRAGMENT = `#version 300 es
precision mediump float;
uniform sampler2D u_texture;
in vec2 v_texCoord;
out vec4 fragColor;
void main() {
  fragColor = texture(u_texture, v_texCoord);
}`;

const SCANLINES_FRAGMENT = `#version 300 es
precision mediump float;
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_sourceSize;
in vec2 v_texCoord;
out vec4 fragColor;
void main() {
  vec4 color = texture(u_texture, v_texCoord);
  // Scanline at 75% down each source pixel row, alpha 0.25, width 0.35
  float texY = v_texCoord.y * u_sourceSize.y;
  float rowFrac = fract(texY);
  float edge = fwidth(texY) * 0.5;
  float lineHalfWidth = 0.175; // 0.35 / 2
  float mask = 1.0 - smoothstep(lineHalfWidth - edge, lineHalfWidth + edge, abs(rowFrac - 0.75));
  fragColor = vec4(color.rgb * (1.0 - 0.25 * mask), color.a);
}`;

const CRT_SIMPLE_FRAGMENT = `#version 300 es
precision mediump float;
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_sourceSize;
in vec2 v_texCoord;
out vec4 fragColor;
void main() {
  vec4 color = texture(u_texture, v_texCoord);
  // Scanlines: alpha 0.35, position 0.6, width 0.4
  float texY = v_texCoord.y * u_sourceSize.y;
  float rowFrac = fract(texY);
  float edge = fwidth(texY) * 0.5;
  float lineHalfWidth = 0.2; // 0.4 / 2
  float scanMask = 1.0 - smoothstep(lineHalfWidth - edge, lineHalfWidth + edge, abs(rowFrac - 0.6));
  // Vignette: radial gradient center->edge, radius = max(w,h)/1.5, alpha 0.4
  vec2 pixelPos = v_texCoord * u_resolution;
  vec2 center = u_resolution * 0.5;
  float radius = max(u_resolution.x, u_resolution.y) / 1.5;
  float dist = length(pixelPos - center) / radius;
  float vignette = clamp(dist, 0.0, 1.0) * 0.4;
  float darkening = (1.0 - 0.35 * scanMask) * (1.0 - vignette);
  fragColor = vec4(color.rgb * darkening, color.a);
}`;

const LCD_GRID_FRAGMENT = `#version 300 es
precision mediump float;
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_sourceSize;
in vec2 v_texCoord;
out vec4 fragColor;
void main() {
  vec4 color = texture(u_texture, v_texCoord);
  float scaleX = u_resolution.x / u_sourceSize.x;
  float scaleY = u_resolution.y / u_sourceSize.y;
  // Line width: max(1, min(scaleX, scaleY) * 0.15) in screen pixels
  float lineWidthPx = max(1.0, min(scaleX, scaleY) * 0.15);
  // Convert to fraction of source pixel for boundary detection
  float hHalfWidth = lineWidthPx / (scaleY * 2.0);
  float vHalfWidth = lineWidthPx / (scaleX * 2.0);
  float texY = v_texCoord.y * u_sourceSize.y;
  float texX = v_texCoord.x * u_sourceSize.x;
  float rowFrac = fract(texY);
  float colFrac = fract(texX);
  float hEdge = fwidth(texY) * 0.5;
  float vEdge = fwidth(texX) * 0.5;
  // Distance from nearest pixel boundary (boundaries are at frac=0 and frac=1)
  float hDist = min(rowFrac, 1.0 - rowFrac);
  float vDist = min(colFrac, 1.0 - colFrac);
  float hMask = 1.0 - smoothstep(hHalfWidth - hEdge, hHalfWidth + hEdge, hDist);
  float vMask = 1.0 - smoothstep(vHalfWidth - vEdge, vHalfWidth + vEdge, vDist);
  float gridMask = max(hMask, vMask);
  fragColor = vec4(color.rgb * (1.0 - 0.2 * gridMask), color.a);
}`;

export const FRAGMENT_SHADERS: Record<string, string> = {
  none: PASSTHROUGH_FRAGMENT,
  bilinear: PASSTHROUGH_FRAGMENT,
  "sharp-bilinear": PASSTHROUGH_FRAGMENT,
  scanlines: SCANLINES_FRAGMENT,
  "crt-simple": CRT_SIMPLE_FRAGMENT,
  "lcd-grid": LCD_GRID_FRAGMENT,
};

/** Presets that use GL_LINEAR texture filtering; all others use GL_NEAREST. */
export const LINEAR_FILTER_PRESETS = new Set(["bilinear", "sharp-bilinear"]);
