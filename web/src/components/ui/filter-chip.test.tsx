import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { FilterChip } from "./filter-chip";

describe("FilterChip", () => {
  it("renders label text", () => {
    render(
      <FilterChip label="Action" isSelected={false} onClick={vi.fn()} />,
    );
    expect(screen.getByRole("button")).toHaveTextContent("Action");
  });

  it("renders label with count when provided", () => {
    render(
      <FilterChip
        label="RPG"
        isSelected={false}
        onClick={vi.fn()}
        count={12}
      />,
    );
    expect(screen.getByRole("button")).toHaveTextContent("RPG (12)");
  });

  it("does not render count when omitted", () => {
    render(
      <FilterChip label="All" isSelected={true} onClick={vi.fn()} />,
    );
    expect(screen.getByRole("button")).toHaveTextContent("All");
    expect(screen.getByRole("button").textContent).not.toContain("(");
  });

  it("renders count of zero when explicitly provided", () => {
    render(
      <FilterChip
        label="Empty"
        isSelected={false}
        onClick={vi.fn()}
        count={0}
      />,
    );
    expect(screen.getByRole("button")).toHaveTextContent("Empty (0)");
  });

  it("applies selected styles when isSelected is true", () => {
    render(
      <FilterChip label="Selected" isSelected={true} onClick={vi.fn()} />,
    );
    const button = screen.getByRole("button");
    expect(button.className).toContain("text-brand-400");
    expect(button.className).toContain("bg-brand-500/15");
  });

  it("applies unselected styles when isSelected is false", () => {
    render(
      <FilterChip label="Unselected" isSelected={false} onClick={vi.fn()} />,
    );
    const button = screen.getByRole("button");
    expect(button.className).toContain("bg-surface-800");
    expect(button.className).toContain("text-surface-300");
  });

  it("calls onClick when clicked", async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    render(
      <FilterChip label="Click me" isSelected={false} onClick={handleClick} />,
    );
    await user.click(screen.getByRole("button"));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
