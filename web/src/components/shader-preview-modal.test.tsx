import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ShaderPreviewModal } from "./shader-preview-modal";

beforeEach(() => {
  const mockCtx: Record<string, unknown> = {
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
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
    mockCtx as unknown as CanvasRenderingContext2D,
  );
});

afterEach(() => {
  document.body.style.overflow = "";
  vi.restoreAllMocks();
});

describe("ShaderPreviewModal", () => {
  it("renders nothing when closed", () => {
    render(
      <ShaderPreviewModal open={false} onClose={vi.fn()} imageUrl="/test.png" shader="none" />,
    );
    expect(screen.queryByText("Click anywhere to close")).not.toBeInTheDocument();
  });

  it("renders content when open", () => {
    render(
      <ShaderPreviewModal open onClose={vi.fn()} imageUrl="/test.png" shader="none" />,
    );
    expect(screen.getByText("Click anywhere to close")).toBeInTheDocument();
  });

  it("calls onClose when clicking the overlay", () => {
    const onClose = vi.fn();
    render(
      <ShaderPreviewModal open onClose={onClose} imageUrl="/test.png" shader="none" />,
    );
    const closeHint = screen.getByText("Click anywhere to close");
    fireEvent.click(closeHint.closest("[class*='fixed']") as HTMLElement);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("calls onClose when pressing Escape", async () => {
    const onClose = vi.fn();
    render(
      <ShaderPreviewModal open onClose={onClose} imageUrl="/test.png" shader="none" />,
    );
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("locks body scroll when open", () => {
    const { rerender } = render(
      <ShaderPreviewModal open onClose={vi.fn()} imageUrl="/test.png" shader="none" />,
    );
    expect(document.body.style.overflow).toBe("hidden");

    rerender(
      <ShaderPreviewModal open={false} onClose={vi.fn()} imageUrl="/test.png" shader="none" />,
    );
    expect(document.body.style.overflow).toBe("");
  });

  it("renders the overlay when open", () => {
    const { container } = render(
      <ShaderPreviewModal open onClose={vi.fn()} imageUrl="/test.png" shader="none" />,
    );
    const overlay = container.querySelector("[class*='fixed']") as HTMLElement;
    expect(overlay).toBeTruthy();
  });
});
