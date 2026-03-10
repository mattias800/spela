import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { DeveloperDetailPage } from "@/pages/developer-detail-page";
import type { DeveloperDetailResponse, Game } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useDeveloperDetail: vi.fn(),
}));

vi.mock("@/hooks/use-games", () => ({
  useToggleFavorite: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

import { useDeveloperDetail } from "@/hooks/use-explore";

const mockUseDeveloperDetail = useDeveloperDetail as ReturnType<typeof vi.fn>;

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "1",
    title: "Test Game",
    consoleId: "snes",
    consoleName: "SNES",
    fileName: "test.sfc",
    fileSize: 1024,
    discCount: 1,
    screenshotUrls: [],
    scrapeAttempts: 1,
    coverAspectRatio: 0.75,
    playable: true,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

const mockDeveloperDetail: DeveloperDetailResponse = {
  name: "Capcom",
  gameCount: 5,
  avgRating: 88.5,
  consoles: ["SNES", "GBA"],
  games: [
    makeGame({ id: "1", title: "Mega Man X", consoleName: "SNES", developer: "Capcom" }),
    makeGame({ id: "2", title: "Mega Man X2", consoleName: "SNES", developer: "Capcom" }),
    makeGame({ id: "3", title: "Mega Man Zero", consoleName: "GBA", developer: "Capcom" }),
    makeGame({ id: "4", title: "Street Fighter II", consoleName: "SNES", developer: "Capcom" }),
    makeGame({ id: "5", title: "Breath of Fire", consoleName: "SNES", developer: "Capcom" }),
  ],
};

function renderPage(name = "Capcom") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[`/explore/developers/${encodeURIComponent(name)}`]}
      >
        <Routes>
          <Route
            path="/explore/developers/:name"
            element={<DeveloperDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("DeveloperDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseDeveloperDetail.mockReturnValue({
      data: mockDeveloperDetail,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("developer-detail-page")).toBeInTheDocument();
  });

  it("renders developer name as heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Capcom", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /back to explore/i }),
    ).toBeInTheDocument();
  });

  it("renders stats row with game count and avg rating", () => {
    renderPage();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).toHaveTextContent("5 games");
    expect(stats).toHaveTextContent("Avg rating: 88.5");
    expect(stats).toHaveTextContent("Consoles: SNES, GBA");
  });

  it("renders console filter chips", () => {
    renderPage();
    const filters = screen.getByTestId("developer-console-filters");
    expect(within(filters).getByText(/All \(/)).toBeInTheDocument();
    const buttons = within(filters).getAllByRole("button");
    const buttonTexts = buttons.map((b) => b.textContent);
    expect(buttonTexts).toContainEqual(expect.stringContaining("SNES"));
    expect(buttonTexts).toContainEqual(expect.stringContaining("GBA"));
  });

  it("renders all games in grid", () => {
    renderPage();
    expect(screen.getByText("Mega Man X")).toBeInTheDocument();
    expect(screen.getByText("Mega Man X2")).toBeInTheDocument();
    expect(screen.getByText("Mega Man Zero")).toBeInTheDocument();
    expect(screen.getByText("Street Fighter II")).toBeInTheDocument();
    expect(screen.getByText("Breath of Fire")).toBeInTheDocument();
  });

  it("filters games by console", async () => {
    const user = userEvent.setup();
    renderPage();

    const filters = screen.getByTestId("developer-console-filters");
    const buttons = within(filters).getAllByRole("button");
    const gbaButton = buttons.find(
      (b) => b.textContent?.trim().startsWith("GBA"),
    );
    expect(gbaButton).toBeDefined();
    await user.click(gbaButton!);

    const grid = screen.getByTestId("developer-game-grid");
    const gameLinks = within(grid).getAllByRole("link");
    expect(gameLinks).toHaveLength(1);
    expect(screen.getByText("Mega Man Zero")).toBeInTheDocument();
    expect(screen.queryByText("Mega Man X2")).not.toBeInTheDocument();
  });

  it("shows loading skeleton while loading", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("developer-detail-skeleton")).toBeInTheDocument();
  });

  it("shows empty state when developer not found", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("Unknown");
    expect(
      screen.getByText("No games found for this developer"),
    ).toBeInTheDocument();
  });

  it("hides console filters when only one console", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        consoles: ["SNES"],
        games: [makeGame({ id: "1", title: "Mega Man X", consoleName: "SNES" })],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("developer-console-filters"),
    ).not.toBeInTheDocument();
  });

  it("renders singular game count correctly", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        gameCount: 1,
        consoles: ["SNES"],
        games: [makeGame({ id: "1", title: "Test", consoleName: "SNES" })],
      },
      isLoading: false,
    });
    renderPage();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).toHaveTextContent("1 game");
  });

  it("hides avg rating when zero", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        avgRating: 0,
      },
      isLoading: false,
    });
    renderPage();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).not.toHaveTextContent("Avg rating");
  });
});
