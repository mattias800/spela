import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AdminScanPage } from "../scan-page";

const mockScanMutate = vi.fn();
const mockScrapeMutate = vi.fn();
const mockScanDismiss = vi.fn();
const mockScrapeDismiss = vi.fn();

vi.mock("@/hooks/use-admin", () => ({
  useScanLibrary: vi.fn(),
  useScrapeMetadata: vi.fn(),
  useCancelScrape: vi.fn(),
}));

vi.mock("@/hooks/use-scrape-progress", () => ({
  useScrapeProgress: vi.fn(),
}));

vi.mock("@/hooks/use-scan-progress", () => ({
  useScanProgress: vi.fn(),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

import { useScanLibrary, useScrapeMetadata, useCancelScrape } from "@/hooks/use-admin";
import { useScrapeProgress } from "@/hooks/use-scrape-progress";
import { useScanProgress } from "@/hooks/use-scan-progress";
import { useConsoles } from "@/hooks/use-consoles";

const mockUseScanLibrary = useScanLibrary as ReturnType<typeof vi.fn>;
const mockUseScrapeMetadata = useScrapeMetadata as ReturnType<typeof vi.fn>;
const mockUseCancelScrape = useCancelScrape as ReturnType<typeof vi.fn>;
const mockUseScrapeProgress = useScrapeProgress as ReturnType<typeof vi.fn>;
const mockUseScanProgress = useScanProgress as ReturnType<typeof vi.fn>;
const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminScanPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseScanLibrary.mockReturnValue({
    mutate: mockScanMutate,
    isPending: false,
    data: undefined,
  });
  mockUseScrapeMetadata.mockReturnValue({
    mutate: mockScrapeMutate,
    isPending: false,
  });
  mockUseCancelScrape.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseScanProgress.mockReturnValue({
    phase: "idle",
    message: "",
    current: 0,
    total: 0,
    result: null,
    error: null,
    dismiss: mockScanDismiss,
  });
  mockUseScrapeProgress.mockReturnValue({
    phase: "idle",
    current: 0,
    total: 0,
    gameName: "",
    successes: 0,
    failures: 0,
    error: null,
    dismiss: mockScrapeDismiss,
  });
  mockUseConsoles.mockReturnValue({
    data: [
      { id: 1, name: "Super Nintendo", abbreviation: "snes" },
      { id: 2, name: "Nintendo Entertainment System", abbreviation: "nes" },
      { id: 3, name: "Game Boy Advance", abbreviation: "gba" },
    ],
  });
});

describe("AdminScanPage", () => {
  it("renders both scan and scrape cards", () => {
    renderPage();
    expect(screen.getByText("Scan for Games")).toBeInTheDocument();
    expect(screen.getByText("Scrape Metadata")).toBeInTheDocument();
  });

  it("renders page heading", () => {
    renderPage();
    expect(screen.getByText("Library Scan")).toBeInTheDocument();
  });

  it("renders Start Scan button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /Start Scan/ }),
    ).toBeInTheDocument();
  });

  it("renders all three scrape buttons", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /Scrape New Games/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Rescrape Fallback Only/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Rescrape All Games/ }),
    ).toBeInTheDocument();
  });

  it("calls scan mutation when Start Scan is clicked", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Start Scan/ }),
    );
    expect(mockScanMutate).toHaveBeenCalled();
  });

  it("calls scrape mutation with 'new' when Scrape New Games is clicked", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Scrape New Games/ }),
    );
    expect(mockScrapeMutate).toHaveBeenCalledWith(
      { mode: "new", console: undefined },
      expect.any(Object),
    );
  });

  it("calls scrape mutation with 'fallback' when Rescrape Fallback Only is clicked", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Rescrape Fallback Only/ }),
    );
    expect(mockScrapeMutate).toHaveBeenCalledWith(
      { mode: "fallback", console: undefined },
      expect.any(Object),
    );
  });

  it("calls scrape mutation with 'all' when Rescrape All Games is clicked", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Rescrape All Games/ }),
    );
    expect(mockScrapeMutate).toHaveBeenCalledWith(
      { mode: "all", console: undefined },
      expect.any(Object),
    );
  });

  it("shows scanning progress when scan is active", () => {
    mockUseScanProgress.mockReturnValue({
      phase: "active",
      message: "Scanning game directories... (12 new so far)",
      current: 12,
      total: 0,
      result: null,
      error: null,
      dismiss: mockScanDismiss,
    });
    renderPage();
    expect(
      screen.getByText("Scanning game directories... (12 new so far)"),
    ).toBeInTheDocument();
  });

  it("disables Start Scan button when scan is active", () => {
    mockUseScanProgress.mockReturnValue({
      phase: "active",
      message: "Scanning...",
      current: 0,
      total: 0,
      result: null,
      error: null,
      dismiss: mockScanDismiss,
    });
    renderPage();
    expect(
      screen.getByRole("button", { name: /Start Scan/ }),
    ).toBeDisabled();
  });

  it("shows scan results when scan completes", () => {
    mockUseScanProgress.mockReturnValue({
      phase: "complete",
      message: "",
      current: 0,
      total: 0,
      result: { totalGames: 42, newGames: 5, updatedGames: 3, removedGames: 1 },
      error: null,
      dismiss: mockScanDismiss,
    });
    renderPage();
    expect(screen.getByText("Scan complete")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
  });

  it("shows scan error state", () => {
    mockUseScanProgress.mockReturnValue({
      phase: "error",
      message: "",
      current: 0,
      total: 0,
      result: null,
      error: "library scan failed",
      dismiss: mockScanDismiss,
    });
    renderPage();
    expect(screen.getByText("Scan failed")).toBeInTheDocument();
    expect(screen.getByText("library scan failed")).toBeInTheDocument();
  });

  it("shows active scrape progress panel", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "active",
      current: 3,
      total: 10,
      gameId: 42,
      gameName: "Super Mario Bros.",
      consoleName: "Nintendo Entertainment System",
      consoleAbbr: "nes",
      successes: 2,
      failures: 1,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByText(/Scraping game 3 of 10/)).toBeInTheDocument();
    // Game name should be a link to the game detail page
    const gameLink = screen.getByRole("link", { name: "Super Mario Bros." });
    expect(gameLink).toBeInTheDocument();
    expect(gameLink).toHaveAttribute("href", "/games/42");
    // Console name should be shown
    expect(screen.getByText("(Nintendo Entertainment System)")).toBeInTheDocument();
    expect(screen.getByText("2 succeeded")).toBeInTheDocument();
    expect(screen.getByText("1 failed")).toBeInTheDocument();
  });

  it("disables scrape buttons when scrape is active", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "active",
      current: 1,
      total: 5,
      gameName: "Test Game",
      successes: 0,
      failures: 0,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(
      screen.getByRole("button", { name: /Scrape New Games/ }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: /Rescrape All Games/ }),
    ).toBeDisabled();
  });

  it("shows completion summary", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "complete",
      current: 10,
      total: 10,
      gameName: "",
      successes: 8,
      failures: 2,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByText("Scraping complete")).toBeInTheDocument();
    expect(screen.getByText("8 scraped")).toBeInTheDocument();
    expect(screen.getByText("2 failed")).toBeInTheDocument();
  });

  it("shows dismiss button on scrape completion and calls dismiss", async () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "complete",
      current: 10,
      total: 10,
      gameName: "",
      successes: 10,
      failures: 0,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    const dismissButtons = screen.getAllByRole("button", { name: /Dismiss/ });
    expect(dismissButtons.length).toBeGreaterThan(0);
    await userEvent.click(dismissButtons[0]);
    expect(mockScrapeDismiss).toHaveBeenCalled();
  });

  it("shows scrape error state with error message", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "error",
      current: 0,
      total: 0,
      gameName: "",
      successes: 0,
      failures: 0,
      error: "IGDB rate limit exceeded",
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByText("Scraping failed")).toBeInTheDocument();
    expect(screen.getByText("IGDB rate limit exceeded")).toBeInTheDocument();
  });

  it("shows dismiss button on scrape error and calls dismiss", async () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "error",
      current: 0,
      total: 0,
      gameName: "",
      successes: 0,
      failures: 0,
      error: "Some error",
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    const dismissButtons = screen.getAllByRole("button", { name: /Dismiss/ });
    await userEvent.click(dismissButtons[0]);
    expect(mockScrapeDismiss).toHaveBeenCalled();
  });

  it("does not show failures count when there are zero failures during active scrape", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "active",
      current: 3,
      total: 10,
      gameName: "Test Game",
      successes: 3,
      failures: 0,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByText("3 succeeded")).toBeInTheDocument();
    expect(screen.queryByText(/failed/)).not.toBeInTheDocument();
  });

  it("does not show failures count in completion when there are zero failures", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "complete",
      current: 10,
      total: 10,
      gameName: "",
      successes: 10,
      failures: 0,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByText("10 scraped")).toBeInTheDocument();
    expect(screen.queryByText(/failed/)).not.toBeInTheDocument();
  });

  it("shows 'No unscraped games found' when 0 successes and 0 failures", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "complete",
      current: 0,
      total: 0,
      gameName: "",
      successes: 0,
      failures: 0,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByText("No unscraped games found")).toBeInTheDocument();
    expect(screen.queryByText(/\d+ scraped/)).not.toBeInTheDocument();
  });

  it("renders console filter dropdown with sorted consoles", () => {
    renderPage();
    const select = screen.getByLabelText("Console filter");
    expect(select).toBeInTheDocument();
    const options = select.querySelectorAll("option");
    // "All consoles" + 3 consoles
    expect(options).toHaveLength(4);
    // Sorted alphabetically: GBA, NES, SNES
    expect(options[1].textContent).toBe("Game Boy Advance");
    expect(options[2].textContent).toBe("Nintendo Entertainment System");
    expect(options[3].textContent).toBe("Super Nintendo");
  });

  it("passes selected console to scrape mutation", async () => {
    renderPage();
    const select = screen.getByLabelText("Console filter");
    await userEvent.selectOptions(select, "snes");
    await userEvent.click(
      screen.getByRole("button", { name: /Scrape New Games/ }),
    );
    expect(mockScrapeMutate).toHaveBeenCalledWith(
      { mode: "new", console: "snes" },
      expect.any(Object),
    );
  });

  it("disables console filter when scrape is active", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "active",
      current: 1,
      total: 5,
      gameName: "Test Game",
      successes: 0,
      failures: 0,
      error: null,
      dismiss: mockScrapeDismiss,
    });
    renderPage();
    expect(screen.getByLabelText("Console filter")).toBeDisabled();
  });
});
