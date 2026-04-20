import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import {
  TrendingShelf,
  CommunityTopShelf,
  CultClassicsShelf,
  RecentlyReviewedShelf,
  ActiveNowShelf,
} from "../social-shelves";
import type {
  Game,
  TrendingGame,
  CommunityTopGame,
  CultClassicGame,
  RecentReviewItem,
  ActiveNowItem,
} from "@/types/api";

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
    coverUrl: "",
    description: "",
    developer: "",
    genre: "",
    igdbCriticsRating: 0,
    isPreRelease: false,
    lastPlayedAt: null,
    players: 0,
    publisher: "",
    releaseDate: "",
    ...overrides,
  };
}

// --- Trending Shelf ---

describe("TrendingShelf", () => {
  function renderTrending(props: {
    games?: TrendingGame[];
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <TrendingShelf
          games={props.games}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders trending games with player counts", () => {
    const games: TrendingGame[] = [
      { game: makeGame({ id: "t1", title: "Super Metroid" }), playersThisWeek: 5 },
      { game: makeGame({ id: "t2", title: "Castlevania" }), playersThisWeek: 3 },
    ];
    renderTrending({ games });

    expect(screen.getByTestId("trending-shelf")).toBeInTheDocument();
    expect(screen.getByText("Trending on Your Server")).toBeInTheDocument();
    expect(screen.getByText("Super Metroid")).toBeInTheDocument();
    expect(screen.getByText("Castlevania")).toBeInTheDocument();
    const counts = screen.getAllByTestId("players-count");
    expect(counts[0]).toHaveTextContent("5 players this week");
    expect(counts[1]).toHaveTextContent("3 players this week");
  });

  it("uses singular form for 1 player", () => {
    const games: TrendingGame[] = [
      { game: makeGame({ id: "t1", title: "Solo" }), playersThisWeek: 1 },
    ];
    renderTrending({ games });

    expect(screen.getByTestId("players-count")).toHaveTextContent("1 player this week");
  });

  it("shows skeleton when loading", () => {
    renderTrending({ isLoading: true });
    expect(screen.getByTestId("trending-shelf-skeleton")).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderTrending({ games: [] });
    expect(container.innerHTML).toBe("");
  });
});

// --- Community Top Shelf ---

describe("CommunityTopShelf", () => {
  function renderCommunityTop(props: {
    games?: CommunityTopGame[];
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <CommunityTopShelf
          games={props.games}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders community favorites with ratings", () => {
    const games: CommunityTopGame[] = [
      { game: makeGame({ id: "c1", title: "A Link to the Past" }), avgRating: 4.8, ratingCount: 12 },
    ];
    renderCommunityTop({ games });

    expect(screen.getByTestId("community-top-shelf")).toBeInTheDocument();
    expect(screen.getByText("Community Favorites")).toBeInTheDocument();
    expect(screen.getByText("A Link to the Past")).toBeInTheDocument();
    expect(screen.getByTestId("community-rating")).toHaveTextContent("4.8/5");
    expect(screen.getByText("(12 ratings)")).toBeInTheDocument();
  });

  it("uses singular for 1 rating", () => {
    const games: CommunityTopGame[] = [
      { game: makeGame({ id: "c1", title: "Game" }), avgRating: 5.0, ratingCount: 1 },
    ];
    renderCommunityTop({ games });
    expect(screen.getByText("(1 rating)")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderCommunityTop({ isLoading: true });
    expect(screen.getByTestId("community-top-shelf-skeleton")).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderCommunityTop({ games: [] });
    expect(container.innerHTML).toBe("");
  });
});

// --- Cult Classics Shelf ---

describe("CultClassicsShelf", () => {
  function renderCultClassics(props: {
    games?: CultClassicGame[];
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <CultClassicsShelf
          games={props.games}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders cult classics with community vs IGDB ratings", () => {
    const games: CultClassicGame[] = [
      {
        game: makeGame({ id: "cu1", title: "Hidden Treasure" }),
        communityRating: 4.5,
        igdbCriticsRating: 60,
        ratingCount: 3,
      },
    ];
    renderCultClassics({ games });

    expect(screen.getByTestId("cult-classics-shelf")).toBeInTheDocument();
    expect(screen.getByText("Cult Classics")).toBeInTheDocument();
    expect(screen.getByText("Hidden Treasure")).toBeInTheDocument();
    expect(screen.getByTestId("cult-community-rating")).toHaveTextContent("4.5/5");
    expect(screen.getByText("vs IGDB 60/100")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderCultClassics({ isLoading: true });
    expect(screen.getByTestId("cult-classics-shelf-skeleton")).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderCultClassics({ games: [] });
    expect(container.innerHTML).toBe("");
  });
});

// --- Recently Reviewed Shelf ---

describe("RecentlyReviewedShelf", () => {
  function renderRecentlyReviewed(props: {
    reviews?: RecentReviewItem[];
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <RecentlyReviewedShelf
          reviews={props.reviews}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders reviews with text and reviewer name", () => {
    const reviews: RecentReviewItem[] = [
      {
        game: makeGame({ id: "r1", title: "Final Fantasy" }),
        rating: 5,
        review: "Best RPG ever made!",
        reviewerName: "playerone",
        reviewedAt: "2025-03-01T10:00:00Z",
      },
    ];
    renderRecentlyReviewed({ reviews });

    expect(screen.getByTestId("recently-reviewed-shelf")).toBeInTheDocument();
    expect(screen.getByText("Recently Reviewed")).toBeInTheDocument();
    expect(screen.getAllByText("Final Fantasy").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId("review-text")).toHaveTextContent("Best RPG ever made!");
    expect(screen.getByText("— playerone")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderRecentlyReviewed({ isLoading: true });
    expect(screen.getByTestId("recently-reviewed-shelf-skeleton")).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderRecentlyReviewed({ reviews: [] });
    expect(container.innerHTML).toBe("");
  });
});

// --- Active Now Shelf ---

describe("ActiveNowShelf", () => {
  function renderActiveNow(props: {
    games?: ActiveNowItem[];
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <ActiveNowShelf
          games={props.games}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders active games with session and challenge counts", () => {
    const games: ActiveNowItem[] = [
      {
        game: makeGame({ id: "a1", title: "Mario Kart" }),
        activeSessions: 2,
        activeChallenges: 1,
      },
    ];
    renderActiveNow({ games });

    expect(screen.getByTestId("active-now-shelf")).toBeInTheDocument();
    expect(screen.getByText("Active Right Now")).toBeInTheDocument();
    expect(screen.getByText("Mario Kart")).toBeInTheDocument();
    expect(screen.getByText("2 sessions")).toBeInTheDocument();
    expect(screen.getByText("1 challenge")).toBeInTheDocument();
  });

  it("uses singular for 1 session", () => {
    const games: ActiveNowItem[] = [
      {
        game: makeGame({ id: "a1", title: "Tetris" }),
        activeSessions: 1,
        activeChallenges: 0,
      },
    ];
    renderActiveNow({ games });
    expect(screen.getByText("1 session")).toBeInTheDocument();
    // No challenge badge should be rendered
    expect(screen.queryByText(/\d+ challenge/)).not.toBeInTheDocument();
  });

  it("uses plural for multiple challenges", () => {
    const games: ActiveNowItem[] = [
      {
        game: makeGame({ id: "a1", title: "Street Fighter" }),
        activeSessions: 0,
        activeChallenges: 3,
      },
    ];
    renderActiveNow({ games });
    // No session badge should be rendered
    expect(screen.queryByText(/\d+ session/)).not.toBeInTheDocument();
    expect(screen.getByText("3 challenges")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderActiveNow({ isLoading: true });
    expect(screen.getByTestId("active-now-shelf-skeleton")).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderActiveNow({ games: [] });
    expect(container.innerHTML).toBe("");
  });
});
