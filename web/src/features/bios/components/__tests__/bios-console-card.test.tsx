import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { BiosConsoleCard } from "../bios-console-card";
import type { BiosConsole } from "@/types/api";

function makeConsole(overrides: Partial<BiosConsole> = {}): BiosConsole {
  return {
    consoleId: "psx",
    consoleName: "PlayStation",
    biosRequired: true,
    status: "ready",
    requiredPresent: 1,
    requiredTotal: 1,
    optionalPresent: 0,
    optionalTotal: 2,
    files: [
      {
        fileName: "scph5501.bin",
        description: "PlayStation BIOS (North America)",
        required: true,
        md5: "abc123",
        status: "valid",
      },
      {
        fileName: "scph5500.bin",
        description: "PlayStation BIOS (Japan)",
        required: false,
        md5: "def456",
        status: "missing",
      },
    ],
    ...overrides,
  };
}

describe("BiosConsoleCard", () => {
  it("renders console name and status badge", () => {
    render(<BiosConsoleCard console={makeConsole()} />);
    expect(screen.getByText("PlayStation")).toBeInTheDocument();
    expect(screen.getByText("Ready")).toBeInTheDocument();
    expect(screen.getByText("1/3 files present")).toBeInTheDocument();
  });

  it("shows Missing BIOS badge when status is missing", () => {
    render(
      <BiosConsoleCard
        console={makeConsole({ status: "missing", requiredPresent: 0 })}
      />,
    );
    expect(screen.getByText("Missing BIOS")).toBeInTheDocument();
  });

  it("shows Invalid BIOS badge when status is invalid", () => {
    render(
      <BiosConsoleCard console={makeConsole({ status: "invalid" })} />,
    );
    expect(screen.getByText("Invalid BIOS")).toBeInTheDocument();
  });

  it("shows Not Required badge when status is not_required", () => {
    render(
      <BiosConsoleCard console={makeConsole({ status: "not_required" })} />,
    );
    expect(screen.getByText("Not Required")).toBeInTheDocument();
  });

  it("expands to show file list on click", async () => {
    const user = userEvent.setup();
    render(<BiosConsoleCard console={makeConsole()} />);

    // Files should not be visible initially
    expect(screen.queryByText("scph5501.bin")).not.toBeInTheDocument();

    // Click to expand
    await user.click(screen.getByText("PlayStation"));

    // Files should now be visible
    expect(screen.getByText("scph5501.bin")).toBeInTheDocument();
    expect(screen.getByText("scph5500.bin")).toBeInTheDocument();
  });

  it("calls onDeleteFile when delete button is clicked for present files", async () => {
    const user = userEvent.setup();
    const onDelete = vi.fn();
    render(
      <BiosConsoleCard console={makeConsole()} onDeleteFile={onDelete} />,
    );

    // Expand first
    await user.click(screen.getByText("PlayStation"));

    // Click delete on present file
    const deleteButton = screen.getByLabelText("Delete scph5501.bin");
    await user.click(deleteButton);

    expect(onDelete).toHaveBeenCalledWith("scph5501.bin");
  });

  it("does not show delete button for missing files", async () => {
    const user = userEvent.setup();
    render(
      <BiosConsoleCard console={makeConsole()} onDeleteFile={vi.fn()} />,
    );

    await user.click(screen.getByText("PlayStation"));

    // The missing file should not have a delete button
    expect(
      screen.queryByLabelText("Delete scph5500.bin"),
    ).not.toBeInTheDocument();
  });

  it("applies de-emphasized styling", () => {
    render(<BiosConsoleCard console={makeConsole()} deEmphasized />);
    const card = screen.getByTestId("bios-console-card-psx");
    expect(card.className).toContain("opacity-60");
  });
});
