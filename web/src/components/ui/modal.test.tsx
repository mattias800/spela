import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { Modal } from "./modal";

describe("Modal", () => {
  it("renders nothing when closed", () => {
    render(
      <Modal open={false} onClose={vi.fn()}>
        Content
      </Modal>,
    );
    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders content when open", () => {
    render(
      <Modal open onClose={vi.fn()}>
        Modal content
      </Modal>,
    );
    expect(screen.getByText("Modal content")).toBeInTheDocument();
  });

  it("renders title when provided", () => {
    render(
      <Modal open onClose={vi.fn()} title="My Modal">
        Content
      </Modal>,
    );
    expect(screen.getByText("My Modal")).toBeInTheDocument();
  });

  it("calls onClose when escape is pressed", async () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose}>
        Content
      </Modal>,
    );
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("calls onClose when close button is clicked", async () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="Test">
        Content
      </Modal>,
    );
    const closeButton = screen.getByRole("button");
    await userEvent.click(closeButton);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("caps height at the viewport and scrolls the body, keeping the header pinned (#1290)", () => {
    render(
      <Modal open onClose={vi.fn()} title="Devices">
        <div>device row</div>
      </Modal>,
    );
    const dialog = screen.getByRole("dialog");
    // Dialog is bounded to the viewport and laid out as a column.
    expect(dialog.className).toContain("max-h-[calc(100vh-2rem)]");
    expect(dialog.className).toContain("flex-col");

    // Header doesn't shrink, so the title + close button stay reachable.
    const header = dialog.querySelector("div.shrink-0");
    expect(header).not.toBeNull();
    expect(header).toHaveTextContent("Devices");

    // The body scrolls internally instead of overflowing the viewport.
    const body = screen.getByText("device row").parentElement;
    expect(body?.className).toContain("overflow-y-auto");
    expect(body?.className).toContain("min-h-0");
  });

  it("preserves the bodyClassName escape hatch alongside scroll handling", () => {
    render(
      <Modal open onClose={vi.fn()} title="Edge to edge" bodyClassName="p-0">
        <div>flush content</div>
      </Modal>,
    );
    const body = screen.getByText("flush content").parentElement;
    expect(body?.className).toContain("p-0");
    expect(body?.className).toContain("overflow-y-auto");
  });
});
