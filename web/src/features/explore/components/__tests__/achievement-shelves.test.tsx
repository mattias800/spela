import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { makeGame } from "@/test-utils/fixtures";
import {
  EasyToCompleteShelf,
  HardestGamesShelf,
  AlmostDoneShelf,
  FreshChallengesShelf,
  ActiveChallengesShelf,
} from "../achievement-shelves";
import type {
  EasyToCompleteResponse,
  HardestGamesResponse,
  AlmostDoneResponse,
  FreshChallengesResponse,
  ActiveChallengesResponse,
} from "@/types/api";

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));


// --- Easy to Complete Shelf ---

describe("EasyToCompleteShelf", () => {
  function renderShelf(props: {
    data?: EasyToCompleteResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <EasyToCompleteShelf
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders data correctly", () => {
    const data: EasyToCompleteResponse = {
      games: [
        {
          game: makeGame({ id: "e1", title: "Easy Game 1" }),
          totalAchievements: 20,
          avgCompletion: 85,
          playersAttempted: 50,
          playersCompleted: 42,
        },
        {
          game: makeGame({ id: "e2", title: "Easy Game 2" }),
          totalAchievements: 15,
          avgCompletion: 92,
          playersAttempted: 30,
          playersCompleted: 28,
        },
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("easy-to-complete-shelf")).toBeInTheDocument();
    expect(screen.getByText("Easy to 100%")).toBeInTheDocument();
    expect(screen.getByText("Easy Game 1")).toBeInTheDocument();
    expect(screen.getByText("Easy Game 2")).toBeInTheDocument();
    const completions = screen.getAllByTestId("avg-completion");
    expect(completions[0]).toHaveTextContent("85% avg completion");
    expect(completions[1]).toHaveTextContent("92% avg completion");
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("easy-to-complete-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({ data: { games: [] } });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Hardest Games Shelf ---

describe("HardestGamesShelf", () => {
  function renderShelf(props: {
    data?: HardestGamesResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <HardestGamesShelf
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders data correctly", () => {
    const data: HardestGamesResponse = {
      games: [
        {
          game: makeGame({ id: "h1", title: "Hard Game 1" }),
          totalAchievements: 100,
          avgCompletion: 3,
          playersAttempted: 200,
          playersCompleted: 5,
        },
        {
          game: makeGame({ id: "h2", title: "Hard Game 2" }),
          totalAchievements: 80,
          avgCompletion: 7,
          playersAttempted: 150,
          playersCompleted: 10,
        },
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("hardest-games-shelf")).toBeInTheDocument();
    expect(screen.getByText("Mount Everest")).toBeInTheDocument();
    expect(screen.getByText("Hard Game 1")).toBeInTheDocument();
    expect(screen.getByText("Hard Game 2")).toBeInTheDocument();
    const completions = screen.getAllByTestId("hardest-completion");
    expect(completions[0]).toHaveTextContent("3% avg · 5/200 completed");
    expect(completions[1]).toHaveTextContent("7% avg · 10/150 completed");
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("hardest-games-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({ data: { games: [] } });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Almost Done Shelf ---

describe("AlmostDoneShelf", () => {
  function renderShelf(props: {
    data?: AlmostDoneResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <AlmostDoneShelf
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders data correctly", () => {
    const data: AlmostDoneResponse = {
      games: [
        {
          game: makeGame({ id: "a1", title: "Almost Done Game" }),
          unlockedCount: 18,
          totalCount: 20,
          completionPercent: 90,
        },
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("almost-done-shelf")).toBeInTheDocument();
    expect(screen.getByText("Almost Done")).toBeInTheDocument();
    expect(screen.getByText("Almost Done Game")).toBeInTheDocument();
    expect(screen.getByTestId("progress-bar")).toBeInTheDocument();
    expect(screen.getByTestId("achievement-progress")).toHaveTextContent(
      "18/20 achievements (90%)",
    );
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("almost-done-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({ data: { games: [] } });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Fresh Challenges Shelf ---

describe("FreshChallengesShelf", () => {
  function renderShelf(props: {
    data?: FreshChallengesResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <FreshChallengesShelf
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders data correctly", () => {
    const data: FreshChallengesResponse = {
      games: [
        {
          game: makeGame({ id: "f1", title: "Fresh Game" }),
          totalAchievements: 30,
          totalPoints: 500,
        },
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("fresh-challenges-shelf")).toBeInTheDocument();
    expect(screen.getByText("Fresh Achievement Challenges")).toBeInTheDocument();
    expect(screen.getByText("Fresh Game")).toBeInTheDocument();
    expect(screen.getByTestId("fresh-challenge-info")).toHaveTextContent(
      "30 achievements",
    );
    expect(screen.getByTestId("fresh-challenge-info")).toHaveTextContent(
      "500 points",
    );
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("fresh-challenges-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({ data: { games: [] } });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Active Challenges Shelf ---

describe("ActiveChallengesShelf", () => {
  function renderShelf(props: {
    data?: ActiveChallengesResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <ActiveChallengesShelf
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders challenge cards with names and metadata", () => {
    const data: ActiveChallengesResponse = {
      challenges: [
        {
          id: "ch1",
          creatorUsername: "player1",
          gameId: "g1",
          gameTitle: "Super Mario World",
          name: "Speed Run Challenge",
          type: "speedrun",
          difficulty: "hard",
          consoleName: "",
          description: "",
          gameCoverUrl: "",
          expiresAt: null,
          attemptCount: 15,
          completionCount: 3,
          createdAt: "2025-01-01T00:00:00Z",
        },
        {
          id: "ch2",
          creatorUsername: "player2",
          gameId: "g2",
          gameTitle: "Mega Man X",
          name: "No Death Run",
          type: "survival",
          difficulty: "medium",
          consoleName: "",
          description: "",
          gameCoverUrl: "",
          expiresAt: null,
          attemptCount: 8,
          completionCount: 1,
          createdAt: "2025-01-02T00:00:00Z",
        },
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("active-challenges-shelf")).toBeInTheDocument();
    expect(screen.getByText("Active Challenges")).toBeInTheDocument();
    const names = screen.getAllByTestId("challenge-name");
    expect(names[0]).toHaveTextContent("Speed Run Challenge");
    expect(names[1]).toHaveTextContent("No Death Run");
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Mega Man X")).toBeInTheDocument();
    expect(screen.getByText("speedrun")).toBeInTheDocument();
    expect(screen.getByText("hard")).toBeInTheDocument();
    expect(screen.getByText("survival")).toBeInTheDocument();
    expect(screen.getByText("medium")).toBeInTheDocument();
    expect(screen.getByText("15 attempts")).toBeInTheDocument();
    expect(screen.getByText("3 completed")).toBeInTheDocument();
    expect(screen.getByText("8 attempts")).toBeInTheDocument();
    expect(screen.getByText("1 completed")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("active-challenges-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({ data: { challenges: [] } });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});
