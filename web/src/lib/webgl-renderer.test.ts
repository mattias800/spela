import { describe, it, expect, vi, beforeEach } from "vitest";
import { createWebGLRenderer } from "./webgl-renderer";

function createMockGL(): Record<string, unknown> {
  const programs = new Map<object, boolean>();
  const shaders = new Map<object, boolean>();

  return {
    VERTEX_SHADER: 0x8b31,
    FRAGMENT_SHADER: 0x8b30,
    COMPILE_STATUS: 0x8b81,
    LINK_STATUS: 0x8b82,
    COLOR_BUFFER_BIT: 0x4000,
    TEXTURE_2D: 0x0de1,
    TEXTURE0: 0x84c0,
    TEXTURE_WRAP_S: 0x2802,
    TEXTURE_WRAP_T: 0x2803,
    TEXTURE_MIN_FILTER: 0x2801,
    TEXTURE_MAG_FILTER: 0x2800,
    CLAMP_TO_EDGE: 0x812f,
    NEAREST: 0x2600,
    LINEAR: 0x2601,
    RGBA: 0x1908,
    UNSIGNED_BYTE: 0x1401,
    TRIANGLES: 0x0004,

    createVertexArray: vi.fn(() => ({})),
    deleteVertexArray: vi.fn(),
    bindVertexArray: vi.fn(),

    createShader: vi.fn(() => {
      const s = {};
      shaders.set(s, true);
      return s;
    }),
    shaderSource: vi.fn(),
    compileShader: vi.fn(),
    getShaderParameter: vi.fn(() => true),
    getShaderInfoLog: vi.fn(() => ""),
    deleteShader: vi.fn(),

    createProgram: vi.fn(() => {
      const p = {};
      programs.set(p, true);
      return p;
    }),
    attachShader: vi.fn(),
    linkProgram: vi.fn(),
    getProgramParameter: vi.fn(() => true),
    getProgramInfoLog: vi.fn(() => ""),
    deleteProgram: vi.fn(),
    useProgram: vi.fn(),
    getUniformLocation: vi.fn(() => ({})),
    uniform1i: vi.fn(),
    uniform2f: vi.fn(),

    createTexture: vi.fn(() => ({})),
    deleteTexture: vi.fn(),
    bindTexture: vi.fn(),
    texParameteri: vi.fn(),
    texImage2D: vi.fn(),
    activeTexture: vi.fn(),

    viewport: vi.fn(),
    clearColor: vi.fn(),
    clear: vi.fn(),
    drawArrays: vi.fn(),

    _programs: programs,
    _shaders: shaders,
  };
}

describe("createWebGLRenderer", () => {
  let canvas: HTMLCanvasElement;

  beforeEach(() => {
    canvas = document.createElement("canvas");
  });

  it("returns null when WebGL2 is unavailable", () => {
    vi.spyOn(canvas, "getContext").mockReturnValue(null);
    const renderer = createWebGLRenderer(canvas);
    expect(renderer).toBeNull();
  });

  it("returns a renderer when WebGL2 is available", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas);
    expect(renderer).not.toBeNull();
    expect(renderer).toHaveProperty("setImage");
    expect(renderer).toHaveProperty("setShader");
    expect(renderer).toHaveProperty("resize");
    expect(renderer).toHaveProperty("draw");
    expect(renderer).toHaveProperty("destroy");
  });

  it("creates and caches shader programs per preset", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    renderer.setShader("scanlines");
    const firstCallCount = (mockGL.createProgram as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(firstCallCount).toBe(1);

    // Same preset should reuse cached program
    renderer.setShader("none");
    renderer.setShader("scanlines");
    const secondCallCount = (mockGL.createProgram as ReturnType<typeof vi.fn>).mock.calls.length;
    // "none" creates a new program (2), but "scanlines" reuses cache (still 2)
    expect(secondCallCount).toBe(2);
  });

  it("uploads texture on setImage", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });

    renderer.setImage(img);
    expect(mockGL.createTexture).toHaveBeenCalled();
    expect(mockGL.texImage2D).toHaveBeenCalled();
    expect(mockGL.texParameteri).toHaveBeenCalled();
  });

  it("skips texture re-upload for same image", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });

    renderer.setImage(img);
    renderer.setImage(img);
    expect((mockGL.texImage2D as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
  });

  it("applies LINEAR filter for bilinear presets", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });

    renderer.setShader("bilinear");
    renderer.setImage(img);

    const texParamCalls = (mockGL.texParameteri as ReturnType<typeof vi.fn>).mock.calls;
    const filterCalls = texParamCalls.filter(
      (c: unknown[]) => c[1] === mockGL.TEXTURE_MIN_FILTER || c[1] === mockGL.TEXTURE_MAG_FILTER,
    );
    const hasLinear = filterCalls.some((c: unknown[]) => c[2] === mockGL.LINEAR);
    expect(hasLinear).toBe(true);
  });

  it("applies NEAREST filter for non-bilinear presets", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });

    renderer.setShader("scanlines");
    renderer.setImage(img);

    const texParamCalls = (mockGL.texParameteri as ReturnType<typeof vi.fn>).mock.calls;
    const filterCalls = texParamCalls.filter(
      (c: unknown[]) => c[1] === mockGL.TEXTURE_MIN_FILTER || c[1] === mockGL.TEXTURE_MAG_FILTER,
    );
    const allNearest = filterCalls.every((c: unknown[]) => c[2] === mockGL.NEAREST);
    expect(allNearest).toBe(true);
  });

  it("cleans up resources on destroy", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });

    renderer.setShader("scanlines");
    renderer.setImage(img);
    renderer.destroy();

    expect(mockGL.deleteProgram).toHaveBeenCalled();
    expect(mockGL.deleteTexture).toHaveBeenCalled();
    expect(mockGL.deleteVertexArray).toHaveBeenCalled();
  });

  it("draw does nothing without a texture", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    renderer.draw();
    expect(mockGL.drawArrays).not.toHaveBeenCalled();
  });

  it("draw calls drawArrays when image and shader are set", () => {
    const mockGL = createMockGL();
    vi.spyOn(canvas, "getContext").mockReturnValue(
      mockGL as unknown as WebGL2RenderingContext,
    );
    const renderer = createWebGLRenderer(canvas)!;

    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });

    renderer.setShader("none");
    renderer.setImage(img);
    renderer.resize(400, 300);
    renderer.draw();

    expect(mockGL.drawArrays).toHaveBeenCalled();
    expect(mockGL.viewport).toHaveBeenCalled();
    expect(mockGL.useProgram).toHaveBeenCalled();
  });
});
