import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { StoragePage } from "../storage-page";

const mockToast = vi.fn();

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: mockToast }),
  };
});

const mockStorageData = {
  usedBytes: 52428800, // 50 MB
  quotaBytes: 104857600, // 100 MB
  byConsole: [
    {
      consoleId: "snes",
      consoleName: "Super Nintendo",
      bytes: 31457280,
      saveCount: 15,
    },
    {
      consoleId: "nes",
      consoleName: "Nintendo Entertainment System",
      bytes: 10485760,
      saveCount: 8,
    },
    {
      consoleId: "gba",
      consoleName: "Game Boy Advance",
      bytes: 10485760,
      saveCount: 5,
    },
  ],
};

const mockCompactMutate = vi.fn();

vi.mock("@/hooks/use-storage", () => ({
  useStorage: vi.fn(),
  useCompactSaves: vi.fn(),
}));

import { useStorage, useCompactSaves } from "@/hooks/use-storage";

const mockUseStorage = useStorage as ReturnType<typeof vi.fn>;
const mockUseCompactSaves = useCompactSaves as ReturnType<typeof vi.fn>;

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <StoragePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseStorage.mockReturnValue({
    data: mockStorageData,
    isLoading: false,
    error: null,
  });
  mockUseCompactSaves.mockReturnValue({
    mutate: mockCompactMutate,
    isPending: false,
  });
});

describe("StoragePage", () => {
  it("renders page heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Storage" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Manage your save state storage."),
    ).toBeInTheDocument();
  });

  it("shows loading skeleton when data is loading", () => {
    mockUseStorage.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    });
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Storage" }),
    ).toBeInTheDocument();
    // Should not show storage usage data
    expect(screen.queryByText("Storage Usage")).not.toBeInTheDocument();
  });

  it("shows error state when fetch fails", () => {
    mockUseStorage.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error("Network error"),
    });
    renderPage();
    expect(
      screen.getByText("Unable to load storage info"),
    ).toBeInTheDocument();
  });

  it("displays storage usage bar with correct values", () => {
    renderPage();
    expect(screen.getByText("Storage Usage")).toBeInTheDocument();
    expect(screen.getByText(/50\.0 MB/)).toBeInTheDocument();
    expect(screen.getByText(/100\.0 MB/)).toBeInTheDocument();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "50");
  });

  it("displays console breakdown sorted by bytes descending", () => {
    renderPage();
    expect(screen.getByText("By Console")).toBeInTheDocument();
    const rows = screen.getAllByRole("row");
    // First data row (after header) should be SNES (largest)
    const firstDataRow = rows[1];
    expect(
      within(firstDataRow).getByText("Super Nintendo"),
    ).toBeInTheDocument();
    expect(within(firstDataRow).getByText("15")).toBeInTheDocument();
    expect(within(firstDataRow).getByText("30.0 MB")).toBeInTheDocument();
  });

  it("shows all consoles in the breakdown", () => {
    renderPage();
    expect(screen.getByText("Super Nintendo")).toBeInTheDocument();
    expect(
      screen.getByText("Nintendo Entertainment System"),
    ).toBeInTheDocument();
    expect(screen.getByText("Game Boy Advance")).toBeInTheDocument();
  });

  it("shows compact button when saves exist", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /Compact All Saves/ }),
    ).toBeInTheDocument();
  });

  it("calls compact mutation on button click", async () => {
    renderPage();
    const compactBtn = screen.getByRole("button", {
      name: /Compact All Saves/,
    });
    await userEvent.click(compactBtn);
    expect(mockCompactMutate).toHaveBeenCalledWith(undefined, {
      onSuccess: expect.any(Function),
      onError: expect.any(Function),
    });
  });

  it("shows success toast with freed bytes after compact", async () => {
    mockCompactMutate.mockImplementation(
      (_: undefined, opts: { onSuccess: (r: { deletedCount: number; freedBytes: number }) => void }) => {
        opts.onSuccess({ deletedCount: 5, freedBytes: 2097152 });
      },
    );
    renderPage();
    const compactBtn = screen.getByRole("button", {
      name: /Compact All Saves/,
    });
    await userEvent.click(compactBtn);
    expect(mockToast).toHaveBeenCalledWith(
      "success",
      "Compacted 5 saves, freed 2.0 MB.",
    );
  });

  it("shows info toast when no saves to compact", async () => {
    mockCompactMutate.mockImplementation(
      (_: undefined, opts: { onSuccess: (r: { deletedCount: number; freedBytes: number }) => void }) => {
        opts.onSuccess({ deletedCount: 0, freedBytes: 0 });
      },
    );
    renderPage();
    const compactBtn = screen.getByRole("button", {
      name: /Compact All Saves/,
    });
    await userEvent.click(compactBtn);
    expect(mockToast).toHaveBeenCalledWith(
      "info",
      "No saves to compact. Storage is already optimized.",
    );
  });

  it("shows error toast on compact failure", async () => {
    mockCompactMutate.mockImplementation(
      (_: undefined, opts: { onError: (e: Error) => void }) => {
        opts.onError(new Error("Server error"));
      },
    );
    renderPage();
    const compactBtn = screen.getByRole("button", {
      name: /Compact All Saves/,
    });
    await userEvent.click(compactBtn);
    expect(mockToast).toHaveBeenCalledWith("error", "Server error");
  });

  it("shows loading state on compact button when pending", () => {
    mockUseCompactSaves.mockReturnValue({
      mutate: mockCompactMutate,
      isPending: true,
    });
    renderPage();
    const compactBtn = screen.getByRole("button", {
      name: /Compact All Saves/,
    });
    expect(compactBtn).toBeDisabled();
  });

  it("shows empty state when no saves exist", () => {
    mockUseStorage.mockReturnValue({
      data: {
        usedBytes: 0,
        quotaBytes: 104857600,
        byConsole: [],
      },
      isLoading: false,
      error: null,
    });
    renderPage();
    expect(screen.getByText("No saves yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Play some games and your save state storage usage will appear here.",
      ),
    ).toBeInTheDocument();
    // Compact button should not appear when there are no saves
    expect(
      screen.queryByRole("button", { name: /Compact All Saves/ }),
    ).not.toBeInTheDocument();
  });

  it("uses green bar color when usage is below 80%", () => {
    renderPage();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "50");
    expect(bar.getAttribute("aria-label")).toContain("50.0%");
  });

  it("uses amber bar when usage is 80-99%", () => {
    mockUseStorage.mockReturnValue({
      data: {
        usedBytes: 94371840, // ~90 MB = 90%
        quotaBytes: 104857600,
        byConsole: [
          { consoleId: "snes", consoleName: "SNES", bytes: 94371840, saveCount: 50 },
        ],
      },
      isLoading: false,
      error: null,
    });
    renderPage();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "90");
  });

  it("uses red bar when usage hits 100%", () => {
    mockUseStorage.mockReturnValue({
      data: {
        usedBytes: 104857600,
        quotaBytes: 104857600,
        byConsole: [
          { consoleId: "snes", consoleName: "SNES", bytes: 104857600, saveCount: 100 },
        ],
      },
      isLoading: false,
      error: null,
    });
    renderPage();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "100");
  });

  it("handles singular save count in compact toast", async () => {
    mockCompactMutate.mockImplementation(
      (_: undefined, opts: { onSuccess: (r: { deletedCount: number; freedBytes: number }) => void }) => {
        opts.onSuccess({ deletedCount: 1, freedBytes: 1048576 });
      },
    );
    renderPage();
    const compactBtn = screen.getByRole("button", {
      name: /Compact All Saves/,
    });
    await userEvent.click(compactBtn);
    expect(mockToast).toHaveBeenCalledWith(
      "success",
      "Compacted 1 save, freed 1.0 MB.",
    );
  });
});
