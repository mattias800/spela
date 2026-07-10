import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CoverFrame } from "../cover-frame";

describe("CoverFrame", () => {
  it("fixed-height mode: image is a direct child of the height-locked frame (#1672)", () => {
    render(<CoverFrame src="http://x/cover.jpg" alt="Test Game" coverHeight={192} />);

    const img = screen.getByRole("img", { name: "Test Game" });
    expect(img).toHaveClass("h-full", "w-auto");
    // The percentage-height chain only resolves for a direct child of the
    // element that carries the fixed height — this is the #1672 regression.
    expect(img.parentElement).toHaveAttribute("data-comp", "CoverFrame");
    expect(img.parentElement).toHaveStyle({
      height: "192px",
      width: "fit-content",
    });
  });

  it("fixed-height mode: placeholder box keeps the height and aspect ratio", () => {
    render(
      <CoverFrame alt="Test Game" coverHeight={192} placeholderAspectRatio={0.75} />,
    );

    const placeholder = screen.getByText("T").parentElement;
    expect(placeholder).toHaveStyle({ height: "192px" });
  });

  it("aspect-ratio mode: frame keeps the ratio and the image fills it", () => {
    render(<CoverFrame src="http://x/cover.jpg" alt="Test Game" aspectRatio="3/4" />);

    const img = screen.getByRole("img", { name: "Test Game" });
    expect(img).toHaveClass("h-full", "w-full", "object-cover");
    expect(img.parentElement).toHaveStyle({ aspectRatio: "3/4" });
  });

  it("fluid mode: image spans the parent width", () => {
    render(<CoverFrame src="http://x/cover.jpg" alt="Test Game" />);

    expect(screen.getByRole("img", { name: "Test Game" })).toHaveClass("w-full");
  });

  it("renders custom placeholder content", () => {
    render(
      <CoverFrame alt="Test Game" placeholder={<span data-testid="spinner" />} />,
    );

    expect(screen.getByTestId("spinner")).toBeInTheDocument();
    expect(screen.queryByText("T")).not.toBeInTheDocument();
  });

  it("renders overlays as children inside the frame", () => {
    render(
      <CoverFrame src="http://x/cover.jpg" alt="Test Game" coverHeight={192}>
        <div data-testid="overlay" />
      </CoverFrame>,
    );

    const overlay = screen.getByTestId("overlay");
    expect(overlay.parentElement).toHaveAttribute("data-comp", "CoverFrame");
  });
});
