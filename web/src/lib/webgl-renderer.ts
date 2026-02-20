import {
  VERTEX_SHADER,
  FRAGMENT_SHADERS,
  LINEAR_FILTER_PRESETS,
} from "./shader-sources";

export interface WebGLRenderer {
  setImage(img: HTMLImageElement): void;
  setShader(preset: string): void;
  resize(width: number, height: number): void;
  draw(): void;
  destroy(): void;
}

export function createWebGLRenderer(
  canvas: HTMLCanvasElement,
): WebGLRenderer | null {
  let maybeGl: WebGL2RenderingContext | null;
  try {
    maybeGl = canvas.getContext("webgl2", {
      alpha: false,
      preserveDrawingBuffer: true,
    });
  } catch {
    return null;
  }
  if (!maybeGl || typeof maybeGl.createVertexArray !== "function") return null;
  const gl = maybeGl;

  const programCache = new Map<string, WebGLProgram>();
  let activeProgram: WebGLProgram | null = null;
  let activePreset = "";
  let texture: WebGLTexture | null = null;
  let currentImage: HTMLImageElement | null = null;
  let sourceWidth = 0;
  let sourceHeight = 0;
  let canvasWidth = 0;
  let canvasHeight = 0;

  const vao = gl.createVertexArray();

  function compileShader(type: number, source: string): WebGLShader {
    const shader = gl.createShader(type)!;
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(shader);
      gl.deleteShader(shader);
      throw new Error(`Shader compile error: ${log}`);
    }
    return shader;
  }

  function getProgram(preset: string): WebGLProgram {
    const cached = programCache.get(preset);
    if (cached) return cached;

    const fragSource = FRAGMENT_SHADERS[preset] ?? FRAGMENT_SHADERS["none"];
    const vs = compileShader(gl.VERTEX_SHADER, VERTEX_SHADER);
    const fs = compileShader(gl.FRAGMENT_SHADER, fragSource);

    const program = gl.createProgram()!;
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    gl.deleteShader(vs);
    gl.deleteShader(fs);

    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      const log = gl.getProgramInfoLog(program);
      gl.deleteProgram(program);
      throw new Error(`Program link error: ${log}`);
    }

    programCache.set(preset, program);
    return program;
  }

  function applyTextureFilter(preset: string) {
    if (!texture) return;
    gl.bindTexture(gl.TEXTURE_2D, texture);
    const filter = LINEAR_FILTER_PRESETS.has(preset) ? gl.LINEAR : gl.NEAREST;
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, filter);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, filter);
  }

  return {
    setImage(img: HTMLImageElement) {
      if (img === currentImage) return;
      currentImage = img;

      if (!texture) {
        texture = gl.createTexture();
      }
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      applyTextureFilter(activePreset);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);

      sourceWidth = img.naturalWidth;
      sourceHeight = img.naturalHeight;
    },

    setShader(preset: string) {
      if (preset === activePreset && activeProgram) return;
      activePreset = preset;
      activeProgram = getProgram(preset);
      applyTextureFilter(preset);
    },

    resize(width: number, height: number) {
      const dpr = window.devicePixelRatio || 1;
      const w = Math.round(width * dpr);
      const h = Math.round(height * dpr);
      if (canvas.width === w && canvas.height === h) return;
      canvas.width = w;
      canvas.height = h;
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      canvasWidth = w;
      canvasHeight = h;
    },

    draw() {
      if (!texture || sourceWidth === 0 || sourceHeight === 0) return;
      if (!activeProgram) activeProgram = getProgram(activePreset || "none");

      // Clear full canvas with near-black (#0a0a0a ≈ 0.039)
      gl.viewport(0, 0, canvasWidth, canvasHeight);
      gl.clearColor(0.04, 0.04, 0.04, 1.0);
      gl.clear(gl.COLOR_BUFFER_BIT);

      // Compute letterbox rect preserving image aspect ratio
      const imgAspect = sourceWidth / sourceHeight;
      const canvasAspect = canvasWidth / canvasHeight;
      let drawW: number, drawH: number, drawX: number, drawY: number;

      if (imgAspect > canvasAspect) {
        drawW = canvasWidth;
        drawH = canvasWidth / imgAspect;
        drawX = 0;
        drawY = (canvasHeight - drawH) / 2;
      } else {
        drawH = canvasHeight;
        drawW = canvasHeight * imgAspect;
        drawX = (canvasWidth - drawW) / 2;
        drawY = 0;
      }

      // Set viewport to image rect so shaders operate in image-local coords
      gl.viewport(
        Math.round(drawX),
        Math.round(drawY),
        Math.round(drawW),
        Math.round(drawH),
      );

      gl.useProgram(activeProgram);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.uniform1i(gl.getUniformLocation(activeProgram, "u_texture"), 0);
      gl.uniform2f(
        gl.getUniformLocation(activeProgram, "u_resolution"),
        Math.round(drawW),
        Math.round(drawH),
      );
      gl.uniform2f(
        gl.getUniformLocation(activeProgram, "u_sourceSize"),
        sourceWidth,
        sourceHeight,
      );

      gl.bindVertexArray(vao);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.bindVertexArray(null);
    },

    destroy() {
      for (const program of programCache.values()) {
        gl.deleteProgram(program);
      }
      programCache.clear();
      if (texture) {
        gl.deleteTexture(texture);
        texture = null;
      }
      if (vao) {
        gl.deleteVertexArray(vao);
      }
      currentImage = null;
      activeProgram = null;
    },
  };
}
