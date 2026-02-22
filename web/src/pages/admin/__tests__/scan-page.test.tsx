import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AdminScanPage } from "../scan-page";

const mockScanMutate = vi.fn();
const mockScrapeMutate = vi.fn();
const mockDismiss = vi.fn();

vi.mock("@/hooks/use-admin", () => ({
  useScanLibrary: vi.fn(),
  useScrapeMetadata: vi.fn(),
}));

vi.mock("@/hooks/use-scrape-progress", () => ({
  useScrapeProgress: vi.fn(),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

import { useScanLibrary, useScrapeMetadata } from "@/hooks/use-admin";
import { useScrapeProgress } from "@/hooks/use-scrape-progress";

const mockUseScanLibrary = useScanLibrary as ReturnType<typeof vi.fn>;
const mockUseScrapeMetadata = useScrapeMetadata as ReturnType<typeof vi.fn>;
const mockUseScrapeProgress = useScrapeProgress as ReturnType<typeof vi.fn>;

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
  mockUseScrapeProgress.mockReturnValue({
    phase: "idle",
    current: 0,
    total: 0,
    gameName: "",
    successes: 0,
    failures: 0,
    error: null,
    dismiss: mockDismiss,
  });
});

describe("AdminScanPage", () => {
  it("renders both scan and scrape cards", () => {
    renderPage();
    expect(screen.getByText("Scan for Games")).toBeInTheDocument();
    // "Scrape Metadata" appears as both a heading and button text
    expect(screen.getAllByText("Scrape Metadata").length).toBeGreaterThanOrEqual(1);
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

  it("renders Scrape Metadata button", () => {
    renderPage();
    const buttons = screen.getAllByText("Scrape Metadata");
    // One is the heading, one is the button
    const buttonEl = buttons.find(
      (el) => el.closest("button") !== null,
    );
    expect(buttonEl).toBeTruthy();
  });

  it("calls scan mutation when Start Scan is clicked", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Start Scan/ }),
    );
    expect(mockScanMutate).toHaveBeenCalled();
  });

  it("calls scrape mutation when Scrape Metadata button is clicked", async () => {
    renderPage();
    const scrapeButton = screen
      .getAllByText("Scrape Metadata")
      .find((el) => el.closest("button") !== null)!
      .closest("button")!;
    await userEvent.click(scrapeButton);
    expect(mockScrapeMutate).toHaveBeenCalled();
  });

  it("shows scanning indicator when scan is pending", () => {
    mockUseScanLibrary.mockReturnValue({
      mutate: mockScanMutate,
      isPending: true,
      data: undefined,
    });
    renderPage();
    expect(
      screen.getByText("Scanning game directories..."),
    ).toBeInTheDocument();
  });

  it("shows scan results when scan completes", () => {
    mockUseScanLibrary.mockReturnValue({
      mutate: mockScanMutate,
      isPending: false,
      data: { totalGames: 42, newGames: 5, updatedGames: 3, removedGames: 1 },
    });
    renderPage();
    expect(screen.getByText("Scan complete")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
  });

  it("shows active scrape progress panel", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "active",
      current: 3,
      total: 10,
      gameName: "Super Mario Bros.",
      successes: 2,
      failures: 1,
      error: null,
      dismiss: mockDismiss,
    });
    renderPage();
    expect(screen.getByText(/Scraping game 3 of 10/)).toBeInTheDocument();
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("2 succeeded")).toBeInTheDocument();
    expect(screen.getByText("1 failed")).toBeInTheDocument();
  });

  it("disables Scrape Metadata button when scrape is active", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "active",
      current: 1,
      total: 5,
      gameName: "Test Game",
      successes: 0,
      failures: 0,
      error: null,
      dismiss: mockDismiss,
    });
    renderPage();
    const scrapeButton = screen
      .getAllByText("Scrape Metadata")
      .find((el) => el.closest("button") !== null)!
      .closest("button")!;
    expect(scrapeButton).toBeDisabled();
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
      dismiss: mockDismiss,
    });
    renderPage();
    expect(screen.getByText("Scraping complete")).toBeInTheDocument();
    expect(screen.getByText("8 scraped")).toBeInTheDocument();
    expect(screen.getByText("2 failed")).toBeInTheDocument();
  });

  it("shows dismiss button on completion and calls dismiss", async () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "complete",
      current: 10,
      total: 10,
      gameName: "",
      successes: 10,
      failures: 0,
      error: null,
      dismiss: mockDismiss,
    });
    renderPage();
    const dismissButton = screen.getByRole("button", { name: /Dismiss/ });
    expect(dismissButton).toBeInTheDocument();
    await userEvent.click(dismissButton);
    expect(mockDismiss).toHaveBeenCalled();
  });

  it("shows error state with error message", () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "error",
      current: 0,
      total: 0,
      gameName: "",
      successes: 0,
      failures: 0,
      error: "IGDB rate limit exceeded",
      dismiss: mockDismiss,
    });
    renderPage();
    expect(screen.getByText("Scraping failed")).toBeInTheDocument();
    expect(screen.getByText("IGDB rate limit exceeded")).toBeInTheDocument();
  });

  it("shows dismiss button on error and calls dismiss", async () => {
    mockUseScrapeProgress.mockReturnValue({
      phase: "error",
      current: 0,
      total: 0,
      gameName: "",
      successes: 0,
      failures: 0,
      error: "Some error",
      dismiss: mockDismiss,
    });
    renderPage();
    const dismissButton = screen.getByRole("button", { name: /Dismiss/ });
    await userEvent.click(dismissButton);
    expect(mockDismiss).toHaveBeenCalled();
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
      dismiss: mockDismiss,
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
      dismiss: mockDismiss,
    });
    renderPage();
    expect(screen.getByText("10 scraped")).toBeInTheDocument();
    expect(screen.queryByText(/failed/)).not.toBeInTheDocument();
  });
});
