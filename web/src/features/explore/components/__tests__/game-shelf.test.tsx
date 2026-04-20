import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GameShelf } from "../game-shelf";
import type { Game } from "@/types/api";
import { makeGame } from "@/test-utils/fixtures";

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));


const mockGames: Game[] = [
  makeGame({ id: "1", title: "Super Mario World", consoleName: "SNES" }),
  makeGame({ id: "2", title: "Chrono Trigger", consoleName: "SNES" }),
  makeGame({ id: "3", title: "Zelda", consoleName: "NES" }),
];

function renderShelf(
  props: {
    title?: string;
    games?: Game[] | undefined;
    isLoading?: boolean;
  } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const games = "games" in props ? props.games : mockGames;
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <GameShelf
          title={props.title ?? "Top Rated"}
          games={games}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("GameShelf", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders section with title", () => {
    renderShelf();
    expect(
      screen.getByRole("heading", { name: "Top Rated", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders games in a list", () => {
    renderShelf();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
    expect(screen.getByText("Zelda")).toBeInTheDocument();
  });

  it("renders game cards as list items", () => {
    renderShelf();
    const listItems = screen.getAllByRole("listitem");
    expect(listItems).toHaveLength(3);
  });

  it("renders the scrollable list with title as label", () => {
    renderShelf();
    expect(screen.getByRole("list", { name: "Top Rated" })).toBeInTheDocument();
  });

  it("renders nothing when games array is empty", () => {
    const { container } = renderShelf({ games: [] });
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when games is undefined and not loading", () => {
    const { container } = renderShelf({ games: undefined, isLoading: false });
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when loading", () => {
    renderShelf({ isLoading: true, games: undefined });
    expect(
      screen.getByTestId("shelf-Top Rated-skeleton"),
    ).toBeInTheDocument();
  });

  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderShelf({ isLoading: true, games: undefined });
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders different section titles", () => {
    renderShelf({ title: "Hidden Gems" });
    expect(
      screen.getByRole("heading", { name: "Hidden Gems", level: 2 }),
    ).toBeInTheDocument();
  });

  it("links game cards to game detail pages", () => {
    renderShelf();
    const links = screen.getAllByRole("link");
    const hrefs = links.map((l) => l.getAttribute("href"));
    expect(hrefs).toContain("/games/1");
    expect(hrefs).toContain("/games/2");
    expect(hrefs).toContain("/games/3");
  });
});
