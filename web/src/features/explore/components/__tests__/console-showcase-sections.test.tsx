import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  ConsoleRecentlyAdded,
  ConsoleEssentials,
} from "../console-showcase-sections";
import type { ConsoleShowcase } from "@/types/api";

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
import { makeGame, makeConsole } from "@/test-utils/fixtures";

const mockUseConsoleShowcase = useConsoleShowcase as ReturnType<typeof vi.fn>;



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
