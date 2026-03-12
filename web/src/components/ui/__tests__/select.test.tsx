import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Select } from "../select";

const options = [
  { value: "a", label: "Alpha" },
  { value: "b", label: "Beta" },
];

describe("Select", () => {
  it("renders options", () => {
    render(<Select options={options} />);
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    expect(screen.getAllByRole("option")).toHaveLength(2);
  });

  it("renders label when provided", () => {
    render(<Select id="test" label="Pick one" options={options} />);
    expect(screen.getByText("Pick one")).toBeInTheDocument();
  });

  it("applies disabled styling when disabled", () => {
    render(<Select options={options} disabled />);
    const select = screen.getByRole("combobox");
    expect(select).toBeDisabled();
    expect(select.className).toContain("disabled:opacity-50");
    expect(select.className).toContain("disabled:cursor-not-allowed");
  });
});
