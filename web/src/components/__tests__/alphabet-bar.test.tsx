import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { AlphabetBar } from "../alphabet-bar";

describe("AlphabetBar", () => {
  it("renders all 27 buttons (# + A-Z)", () => {
    render(<AlphabetBar onLetterClick={vi.fn()} />);
    const buttons = screen.getAllByRole("button");
    expect(buttons).toHaveLength(27);
    expect(buttons[0]).toHaveTextContent("#");
    expect(buttons[1]).toHaveTextContent("A");
    expect(buttons[26]).toHaveTextContent("Z");
  });

  it("calls onLetterClick with the letter when clicked", () => {
    const onClick = vi.fn();
    render(<AlphabetBar onLetterClick={onClick} />);

    fireEvent.click(screen.getByLabelText("Jump to M"));
    expect(onClick).toHaveBeenCalledWith("M");
  });

  it("calls onLetterClick with undefined when active letter is clicked again", () => {
    const onClick = vi.fn();
    render(<AlphabetBar activeLetter="M" onLetterClick={onClick} />);

    fireEvent.click(screen.getByLabelText("Jump to M"));
    expect(onClick).toHaveBeenCalledWith(undefined);
  });

  it("highlights the active letter", () => {
    render(<AlphabetBar activeLetter="A" onLetterClick={vi.fn()} />);

    const aButton = screen.getByLabelText("Jump to A");
    expect(aButton).toHaveAttribute("aria-pressed", "true");

    const bButton = screen.getByLabelText("Jump to B");
    expect(bButton).toHaveAttribute("aria-pressed", "false");
  });

  it("renders # button with numbers label", () => {
    render(<AlphabetBar onLetterClick={vi.fn()} />);

    const hashButton = screen.getByLabelText("Jump to numbers");
    expect(hashButton).toBeInTheDocument();
    expect(hashButton).toHaveTextContent("#");
  });

  it("supports vertical orientation", () => {
    render(<AlphabetBar orientation="vertical" onLetterClick={vi.fn()} />);

    const nav = screen.getByRole("navigation");
    expect(nav.className).toContain("flex-col");
  });

  it("defaults to horizontal orientation", () => {
    render(<AlphabetBar onLetterClick={vi.fn()} />);

    const nav = screen.getByRole("navigation");
    expect(nav.className).toContain("flex-wrap");
  });
});
