import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  ConsoleRecentlyAdded,
  ConsoleEssentials,
} from "../console-showcase-sections";
import type { Game, ConsoleShowcase, Console } from "@/types/api";

vi.mock("@/hooks/use-explore", () => ({
  useConsoleShowcase: vi.fn(),
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

import { useConsoleShowcase } from "@/hooks/use-explore";

const mockUseConsoleShowcase = useConsoleShowcase as ReturnType<typeof vi.fn>;

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

function makeConsole(overrides: Partial<Console> = {}): Console {
  return {
    id: "snes",
    name: "Super Nintendo",
    abbreviation: "snes",
    extensions: [],
    defaultCore: "",
    coverAspectRatio: 0.75,
    colorTheme: "#6366f1",
    iconUrl: "",
    logoUrl: "",
    gameCount: 100,
    saveStateSupport: true,
    browserPlayable: false,
    playable: true,
    createdAt: "",
    updatedAt: "",
    ...overrides,
  };
}

function makeShowcase(overrides: Partial<ConsoleShowcase> = {}): ConsoleShowcase {
  return {
    console: makeConsole(),
    essentials: [],
    hiddenGems: [],
    recentlyAdded: [],
    genreBreakdown: [],
    topDevelopers: [],
    recentlyPlayed: [],
    ...overrides,
  };
}

function renderComponent(component: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{component}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ConsoleRecentlyAdded", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders recently added games shelf", () => {
    const games = [
      makeGame({ id: "1", title: "Super Mario World" }),
      makeGame({ id: "2", title: "Chrono Trigger" }),
    ];
    mockUseConsoleShowcase.mockReturnValue({
      data: makeShowcase({ recentlyAdded: games }),
    });

    renderComponent(<ConsoleRecentlyAdded consoleId="snes" />);

    expect(
      screen.getByRole("heading", { name: "Recently Added", level: 2 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
  });

  it("renders nothing when recentlyAdded is empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: makeShowcase({ recentlyAdded: [] }),
    });

    const { container } = renderComponent(
      <ConsoleRecentlyAdded consoleId="snes" />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when showcase is undefined", () => {
    mockUseConsoleShowcase.mockReturnValue({ data: undefined });

    const { container } = renderComponent(
      <ConsoleRecentlyAdded consoleId="snes" />,
    );
    expect(container.innerHTML).toBe("");
  });
});

describe("ConsoleEssentials", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders essentials games shelf", () => {
    const games = [makeGame({ id: "1", title: "Zelda" })];
    mockUseConsoleShowcase.mockReturnValue({
      data: makeShowcase({ essentials: games }),
    });

    renderComponent(<ConsoleEssentials consoleId="snes" />);

    expect(
      screen.getByRole("heading", { name: "Essentials", level: 2 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Zelda")).toBeInTheDocument();
  });

  it("renders nothing when essentials is empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: makeShowcase({ essentials: [] }),
    });

    const { container } = renderComponent(
      <ConsoleEssentials consoleId="snes" />,
    );
    expect(container.innerHTML).toBe("");
  });
});
