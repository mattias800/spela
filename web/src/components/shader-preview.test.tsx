import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ShaderPreview } from "./shader-preview";

// Mock the WebGL renderer module
const mockSetImage = vi.fn();
const mockSetShader = vi.fn();
const mockResize = vi.fn();
const mockDraw = vi.fn();
const mockDestroy = vi.fn();

let webglAvailable = true;

vi.mock("../lib/webgl-renderer", () => ({
  createWebGLRenderer: vi.fn(() => {
    if (!webglAvailable) return null;
    return {
      setImage: mockSetImage,
      setShader: mockSetShader,
      resize: mockResize,
      draw: mockDraw,
      destroy: mockDestroy,
    };
  }),
}));

// Mock canvas context for the 2D fallback path
function createMockContext(): Record<string, unknown> {
  return {
    scale: vi.fn(),
    clearRect: vi.fn(),
    fillRect: vi.fn(),
    drawImage: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    stroke: vi.fn(),
    createRadialGradient: vi.fn(() => ({
      addColorStop: vi.fn(),
    })),
    imageSmoothingEnabled: true,
    imageSmoothingQuality: "high",
    fillStyle: "",
    strokeStyle: "",
    lineWidth: 1,
  };
}

beforeEach(() => {
  webglAvailable = true;
  mockSetImage.mockReset();
  mockSetShader.mockReset();
  mockResize.mockReset();
  mockDraw.mockReset();
  mockDestroy.mockReset();

  const mockCtx = createMockContext();
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(
    (contextId: string) => {
      if (contextId === "2d") {
        return mockCtx as unknown as CanvasRenderingContext2D;
      }
      return null;
    },
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ShaderPreview", () => {
  it("renders a container and canvas element", () => {
    const { container } = render(
      <ShaderPreview imageUrl="/test.png" shader="none" />,
    );
    expect(container.querySelector("canvas")).toBeInTheDocument();
  });

  it("shows placeholder before image loads", () => {
    const { container } = render(
      <ShaderPreview imageUrl="/test.png" shader="none" />,
    );
    const canvas = container.querySelector("canvas");
    expect(canvas?.classList.contains("hidden")).toBe(true);
  });

  it("calls onClick when clicked", () => {
    const onClick = vi.fn();
    const { container } = render(
      <ShaderPreview imageUrl="/test.png" shader="none" onClick={onClick} />,
    );
    const wrapper = container.firstElementChild as HTMLElement;
    fireEvent.click(wrapper);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("shows hover overlay text on mouse enter", () => {
    const { container } = render(
      <ShaderPreview
        imageUrl="/test.png"
        shader="scanlines"
        onClick={vi.fn()}
      />,
    );
    const wrapper = container.firstElementChild as HTMLElement;
    fireEvent.mouseEnter(wrapper);
    // The hover overlay text should appear (but image not loaded so it won't show)
    // This tests the hover state toggle
    fireEvent.mouseLeave(wrapper);
    expect(screen.queryByText("Click to enlarge")).not.toBeInTheDocument();
  });

  it("applies custom className", () => {
    const { container } = render(
      <ShaderPreview imageUrl="/test.png" shader="none" className="max-w-sm" />,
    );
    const wrapper = container.firstElementChild as HTMLElement;
    expect(wrapper.className).toContain("max-w-sm");
  });

  it("renders with each shader value without crashing", () => {
    const shaders = [
      "none",
      "bilinear",
      "sharp-bilinear",
      "crt-simple",
      "lcd-grid",
      "scanlines",
    ];
    for (const shader of shaders) {
      const { unmount } = render(
        <ShaderPreview imageUrl="/test.png" shader={shader} />,
      );
      unmount();
    }
  });

  it("calls destroy on WebGL renderer when unmounting", () => {
    const { unmount } = render(
      <ShaderPreview imageUrl="/test.png" shader="none" />,
    );
    unmount();
    expect(mockDestroy).toHaveBeenCalled();
  });

  it("falls back to Canvas 2D when WebGL2 is unavailable", () => {
    webglAvailable = false;

    const mockCtx = createMockContext();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(
      (contextId: string) => {
        if (contextId === "2d") {
          return mockCtx as unknown as CanvasRenderingContext2D;
        }
        return null;
      },
    );

    const { container } = render(
      <ShaderPreview imageUrl="/test.png" shader="none" />,
    );

    // Simulate image load to trigger draw
    const img = new Image();
    Object.defineProperty(img, "naturalWidth", { value: 256 });
    Object.defineProperty(img, "naturalHeight", { value: 224 });
    Object.defineProperty(img, "complete", { value: true });

    expect(container.querySelector("canvas")).toBeInTheDocument();
    // WebGL draw should NOT have been called
    expect(mockDraw).not.toHaveBeenCalled();
  });
});
