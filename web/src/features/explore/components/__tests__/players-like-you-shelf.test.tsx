import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { PlayersLikeYouShelf } from "../players-like-you-shelf";
import type { Game } from "@/types/api";

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

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

function renderComponent(props: {
  games?: Game[];
  isLoading?: boolean;
  similarUsersCount?: number;
}) {
  return render(
    <MemoryRouter>
      <PlayersLikeYouShelf
        games={props.games}
        isLoading={props.isLoading ?? false}
        similarUsersCount={props.similarUsersCount ?? 0}
      />
    </MemoryRouter>,
  );
}

describe("PlayersLikeYouShelf", () => {
  it("renders shelf with games", () => {
    const games = [
      makeGame({ id: "p1", title: "Super Metroid" }),
      makeGame({ id: "p2", title: "Castlevania" }),
    ];

    renderComponent({ games, similarUsersCount: 5 });

    expect(screen.getByTestId("players-like-you-shelf")).toBeInTheDocument();
    expect(screen.getByText("Players like you also enjoyed")).toBeInTheDocument();
    expect(screen.getByText("Super Metroid")).toBeInTheDocument();
    expect(screen.getByText("Castlevania")).toBeInTheDocument();
  });

  it("shows similar users count", () => {
    const games = [makeGame({ id: "p1", title: "Super Metroid" })];

    renderComponent({ games, similarUsersCount: 12 });

    expect(screen.getByText("Based on 12 players with similar taste")).toBeInTheDocument();
  });

  it("shows singular form for 1 player", () => {
    const games = [makeGame({ id: "p1", title: "Super Metroid" })];

    renderComponent({ games, similarUsersCount: 1 });

    expect(screen.getByText("Based on 1 player with similar taste")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderComponent({ isLoading: true });

    expect(screen.getByTestId("players-like-you-shelf-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("players-like-you-shelf")).not.toBeInTheDocument();
  });

  it("returns null when no games", () => {
    const { container } = renderComponent({ games: undefined });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when games is empty array", () => {
    const { container } = renderComponent({ games: [] });
    expect(container.innerHTML).toBe("");
  });
});
