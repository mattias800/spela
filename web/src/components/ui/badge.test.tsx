import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Badge } from "./badge";

describe("Badge", () => {
  it("renders children", () => {
    render(<Badge>NES</Badge>);
    expect(screen.getByText("NES")).toBeInTheDocument();
  });

  it("applies variant styles", () => {
    const { rerender } = render(<Badge variant="brand">Brand</Badge>);
    expect(screen.getByText("Brand").className).toContain("text-brand-400");

    rerender(<Badge variant="success">Success</Badge>);
    expect(screen.getByText("Success").className).toContain("text-success-500");

    rerender(<Badge variant="danger">Danger</Badge>);
    expect(screen.getByText("Danger").className).toContain("text-danger-500");
  });

  it("defaults to default variant", () => {
    render(<Badge>Default</Badge>);
    expect(screen.getByText("Default").className).toContain("bg-surface-800");
  });
});
