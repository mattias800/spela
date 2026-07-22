import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Banner } from "./banner";

describe("Banner", () => {
  it("renders its message", () => {
    render(<Banner data-testid="test-banner">Something important</Banner>);

    const banner = screen.getByTestId("test-banner");
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent("Something important");
  });

  it("appends custom className", () => {
    render(
      <Banner data-testid="test-banner" className="mb-6">
        Message
      </Banner>,
    );

    expect(screen.getByTestId("test-banner").className).toContain("mb-6");
  });
});
