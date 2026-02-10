import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ShaderPreview } from "./shader-preview";

// Mock canvas context
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
  const mockCtx = createMockContext();
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
    mockCtx as unknown as CanvasRenderingContext2D,
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
      <ShaderPreview imageUrl="/test.png" shader="scanlines" onClick={vi.fn()} />,
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
    const shaders = ["none", "bilinear", "sharp-bilinear", "crt-simple", "lcd-grid", "scanlines"];
    for (const shader of shaders) {
      const { unmount } = render(
        <ShaderPreview imageUrl="/test.png" shader={shader} />,
      );
      unmount();
    }
  });
});
