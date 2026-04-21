import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ProgressBar } from "./progress-bar";

describe("ProgressBar", () => {
  it("renders a progressbar element with the correct aria attributes", () => {
    render(<ProgressBar value={3} max={10} ariaLabel="3 of 10 games owned" />);
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "3");
    expect(bar).toHaveAttribute("aria-valuemin", "0");
    expect(bar).toHaveAttribute("aria-valuemax", "10");
    expect(bar).toHaveAttribute("aria-label", "3 of 10 games owned");
  });

  it("clamps value to [0, max]", () => {
    render(<ProgressBar value={-5} max={10} ariaLabel="clamped low" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "0");
  });

  it("clamps value above max down to max", () => {
    render(<ProgressBar value={99} max={10} ariaLabel="clamped high" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "10");
  });

  it("handles max=0 without dividing by zero", () => {
    render(<ProgressBar value={0} max={0} ariaLabel="empty" />);
    const bar = screen.getByRole("progressbar");
    // max=0 is treated as max=1 internally; value clamps to 0 → 0%
    expect(bar).toHaveAttribute("aria-valuemax", "1");
    expect(bar.getAttribute("style")).toContain("width: 0%");
  });

  it("renders the label and percentage when requested", () => {
    render(
      <ProgressBar
        value={3}
        max={10}
        label="Progress"
        showPercentage
        ariaLabel="3 of 10"
      />,
    );
    expect(screen.getByText("Progress")).toBeInTheDocument();
    expect(screen.getByText("30%")).toBeInTheDocument();
  });

  it("applies the onHero tone when requested", () => {
    const { container } = render(
      <ProgressBar value={5} max={10} tone="onHero" ariaLabel="on hero" />,
    );
    // The track div should carry the onHero track class; the fill the onHero fill class.
    expect(container.innerHTML).toContain("bg-white/20");
    expect(container.innerHTML).toContain("bg-brand-400");
  });
});
