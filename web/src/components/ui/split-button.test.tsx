import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { SplitButton } from "./split-button";

describe("SplitButton", () => {
  it("renders primary action button with children", () => {
    render(
      <SplitButton menuItems={[]} onClick={vi.fn()}>
        Play
      </SplitButton>,
    );
    expect(screen.getByRole("button", { name: "Play" })).toBeInTheDocument();
  });

  it("renders as plain Button when menuItems is empty", () => {
    render(
      <SplitButton menuItems={[]} onClick={vi.fn()}>
        Play
      </SplitButton>,
    );
    // Should be a single button, no split trigger
    const buttons = screen.getAllByRole("button");
    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveTextContent("Play");
    expect(screen.queryByTestId("split-menu-btn")).not.toBeInTheDocument();
  });

  it("shows split menu trigger button when menuItems is provided", () => {
    render(
      <SplitButton
        menuItems={[{ label: "New Game", onClick: vi.fn() }]}
        onClick={vi.fn()}
      >
        Resume
      </SplitButton>,
    );
    expect(screen.getByTestId("split-menu-btn")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Resume" }),
    ).toBeInTheDocument();
  });

  it("opens dropdown when trigger is clicked, showing menu items", async () => {
    render(
      <SplitButton
        menuItems={[
          { label: "New Game", onClick: vi.fn() },
          { label: "Load Save", onClick: vi.fn() },
        ]}
        onClick={vi.fn()}
      >
        Resume
      </SplitButton>,
    );

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId("split-menu-btn"));
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "New Game" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Load Save" })).toBeInTheDocument();
  });

  it("calls menu item onClick when clicked", async () => {
    const handleNewGame = vi.fn();
    render(
      <SplitButton
        menuItems={[{ label: "New Game", onClick: handleNewGame }]}
        onClick={vi.fn()}
      >
        Resume
      </SplitButton>,
    );

    await userEvent.click(screen.getByTestId("split-menu-btn"));
    await userEvent.click(screen.getByRole("menuitem", { name: "New Game" }));
    expect(handleNewGame).toHaveBeenCalledOnce();
  });

  it("forwards disabled state to both buttons", () => {
    render(
      <SplitButton
        menuItems={[{ label: "New Game", onClick: vi.fn() }]}
        onClick={vi.fn()}
        disabled
      >
        Resume
      </SplitButton>,
    );
    const buttons = screen.getAllByRole("button");
    for (const button of buttons) {
      expect(button).toBeDisabled();
    }
  });

  it("supports loading state on primary button", () => {
    const { container } = render(
      <SplitButton
        menuItems={[{ label: "New Game", onClick: vi.fn() }]}
        onClick={vi.fn()}
        loading
      >
        Resume
      </SplitButton>,
    );
    // Primary button should be disabled and show spinner
    expect(
      screen.getByRole("button", { name: "Resume" }),
    ).toBeDisabled();
    expect(container.querySelector(".animate-spin")).toBeInTheDocument();
  });

  it('has aria-label="More options" on trigger', () => {
    render(
      <SplitButton
        menuItems={[{ label: "New Game", onClick: vi.fn() }]}
        onClick={vi.fn()}
      >
        Resume
      </SplitButton>,
    );
    expect(
      screen.getByRole("button", { name: "More options" }),
    ).toBeInTheDocument();
  });
});
