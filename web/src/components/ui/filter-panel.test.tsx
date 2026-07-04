import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { FilterPanel } from "./filter-panel";

describe("FilterPanel", () => {
  it("renders the default title and children", () => {
    render(
      <FilterPanel hasFilters={false} onClear={vi.fn()}>
        <div>Filter controls</div>
      </FilterPanel>,
    );

    expect(screen.getByText("Filters")).toBeInTheDocument();
    expect(screen.getByText("Filter controls")).toBeInTheDocument();
  });

  it("hides the clear action when no filters are active", () => {
    render(
      <FilterPanel hasFilters={false} onClear={vi.fn()}>
        <div />
      </FilterPanel>,
    );

    expect(screen.queryByRole("button", { name: "Clear" })).toBeNull();
  });

  it("calls onClear from the clear action", async () => {
    const handleClear = vi.fn();
    render(
      <FilterPanel hasFilters onClear={handleClear}>
        <div />
      </FilterPanel>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Clear" }));
    expect(handleClear).toHaveBeenCalledOnce();
  });
});
