import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ConsoleBreakdownTable } from "../console-breakdown-table";

const mockConsoles = [
  {
    consoleId: "nes",
    consoleName: "Nintendo Entertainment System",
    bytes: 1048576,
    saveCount: 3,
  },
  {
    consoleId: "snes",
    consoleName: "Super Nintendo",
    bytes: 5242880,
    saveCount: 10,
  },
  {
    consoleId: "gba",
    consoleName: "Game Boy Advance",
    bytes: 2097152,
    saveCount: 5,
  },
];

describe("ConsoleBreakdownTable", () => {
  it("renders column headers", () => {
    render(<ConsoleBreakdownTable consoles={mockConsoles} />);
    expect(screen.getByText("Console")).toBeInTheDocument();
    expect(screen.getByText("Saves")).toBeInTheDocument();
    expect(screen.getByText("Size")).toBeInTheDocument();
  });

  it("sorts consoles by bytes descending", () => {
    render(<ConsoleBreakdownTable consoles={mockConsoles} />);
    const rows = screen.getAllByRole("row");
    // rows[0] = header, rows[1] = SNES (largest), rows[2] = GBA, rows[3] = NES
    expect(
      within(rows[1]).getByText("Super Nintendo"),
    ).toBeInTheDocument();
    expect(
      within(rows[2]).getByText("Game Boy Advance"),
    ).toBeInTheDocument();
    expect(
      within(rows[3]).getByText("Nintendo Entertainment System"),
    ).toBeInTheDocument();
  });

  it("displays save counts and formatted sizes", () => {
    render(<ConsoleBreakdownTable consoles={mockConsoles} />);
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText("5.0 MB")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("2.0 MB")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("1.0 MB")).toBeInTheDocument();
  });

  it("renders empty table body when no consoles", () => {
    render(<ConsoleBreakdownTable consoles={[]} />);
    const rows = screen.getAllByRole("row");
    // Only header row
    expect(rows).toHaveLength(1);
  });
});
