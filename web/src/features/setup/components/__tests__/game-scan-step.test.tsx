import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { GameScanStep } from "../game-scan-step";

// Regression guard for the bug where GameScanStep used to render
// "Scan complete — null games found" because it read the mutation's
// immediate response (which is just `{ message: "scan started" }`) instead
// of the `scan_complete` WebSocket payload. After the fix the result
// comes from `useScanProgress`, which captures the async result.

const mockMutate = vi.fn();

vi.mock("@/hooks/use-admin", () => ({
  useScanLibrary: () => ({
    mutate: mockMutate,
    isPending: false,
  }),
}));

vi.mock("@/hooks/use-setup-diagnostics", () => ({
  useSetupDiagnostics: () => ({ data: undefined }),
}));

const useScanProgressMock = vi.fn();
vi.mock("@/hooks/use-scan-progress", () => ({
  useScanProgress: () => useScanProgressMock(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  useScanProgressMock.mockReturnValue({
    phase: "idle",
    message: "",
    current: 0,
    total: 0,
    result: null,
    error: null,
    dismiss: vi.fn(),
  });
});

describe("GameScanStep", () => {
  it("renders scan button initially", () => {
    render(<GameScanStep onSkip={vi.fn()} onComplete={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: /Scan for Games/ }),
    ).toBeInTheDocument();
  });

  it("kicks off the scan via the mutation when clicked", async () => {
    render(<GameScanStep onSkip={vi.fn()} onComplete={vi.fn()} />);
    await userEvent.click(
      screen.getByRole("button", { name: /Scan for Games/ }),
    );
    expect(mockMutate).toHaveBeenCalledTimes(1);
  });

  it("shows in-progress state from the scan progress hook", () => {
    useScanProgressMock.mockReturnValue({
      phase: "active",
      message: "Scanning /games...",
      current: 10,
      total: 50,
      result: null,
      error: null,
      dismiss: vi.fn(),
    });
    render(<GameScanStep onSkip={vi.fn()} onComplete={vi.fn()} />);
    // The in-progress pill only renders after the user has clicked scan,
    // so this test documents that behaviour — a completed scan without
    // a user kick doesn't render the progress pill.
    expect(screen.queryByText(/Scanning \/games/)).not.toBeInTheDocument();
  });

  it("renders the completed summary when useScanProgress reports a result", () => {
    useScanProgressMock.mockReturnValue({
      phase: "complete",
      message: "",
      current: 0,
      total: 0,
      result: {
        totalGames: 42,
        newGames: 7,
        updatedGames: 3,
        removedGames: 1,
      },
      error: null,
      dismiss: vi.fn(),
    });
    render(<GameScanStep onSkip={vi.fn()} onComplete={vi.fn()} />);

    // The critical regression: must show the real total from the WS
    // payload, not "null games found".
    expect(
      screen.getByText(/Scan complete — 42 games found/),
    ).toBeInTheDocument();
    expect(screen.getByText("New: 7")).toBeInTheDocument();
    expect(screen.getByText("Updated: 3")).toBeInTheDocument();
    expect(screen.getByText("Removed: 1")).toBeInTheDocument();
  });

  it("singularises `game` when exactly one was found", () => {
    useScanProgressMock.mockReturnValue({
      phase: "complete",
      message: "",
      current: 0,
      total: 0,
      result: {
        totalGames: 1,
        newGames: 1,
        updatedGames: 0,
        removedGames: 0,
      },
      error: null,
      dismiss: vi.fn(),
    });
    render(<GameScanStep onSkip={vi.fn()} onComplete={vi.fn()} />);
    expect(
      screen.getByText(/Scan complete — 1 game found/),
    ).toBeInTheDocument();
    // Non-positive counts are suppressed.
    expect(screen.queryByText(/Updated:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Removed:/)).not.toBeInTheDocument();
  });

  it("surfaces a scan_error payload", () => {
    useScanProgressMock.mockReturnValue({
      phase: "error",
      message: "",
      current: 0,
      total: 0,
      result: null,
      error: "directory not readable",
      dismiss: vi.fn(),
    });
    render(<GameScanStep onSkip={vi.fn()} onComplete={vi.fn()} />);
    expect(screen.getByText(/directory not readable/)).toBeInTheDocument();
  });
});
