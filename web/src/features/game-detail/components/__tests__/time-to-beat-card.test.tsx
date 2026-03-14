import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { TimeToBeatCard } from "../time-to-beat-card";
import type { Game } from "@/types/api";

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "1",
    title: "Test Game",
    consoleId: "snes",
    consoleName: "SNES",
    fileName: "test.smc",
    fileSize: 1024,
    discCount: 1,
    screenshotUrls: [],
    scrapeAttempts: 0,
    coverAspectRatio: 0.75,
    playable: true,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

// Helper: convert hours to seconds (IGDB API returns seconds)
const hrs = (h: number) => h * 3600;

describe("TimeToBeatCard", () => {
  it("renders nothing when all time values are 0", () => {
    const game = makeGame({
      timeToBeatHastily: 0,
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    const { container } = render(<TimeToBeatCard game={game} />);
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when all time values are undefined", () => {
    const game = makeGame({
      timeToBeatHastily: undefined,
      timeToBeatNormally: undefined,
      timeToBeatCompletely: undefined,
    });
    const { container } = render(<TimeToBeatCard game={game} />);
    expect(container.innerHTML).toBe("");
  });

  it("renders card when at least one value is non-zero", () => {
    const game = makeGame({
      timeToBeatHastily: 0,
      timeToBeatNormally: hrs(25),
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByTestId("time-to-beat-card")).toBeInTheDocument();
  });

  it('displays "How Long to Beat" heading', () => {
    const game = makeGame({ timeToBeatNormally: hrs(10) });
    render(<TimeToBeatCard game={game} />);
    expect(
      screen.getByRole("heading", { name: /How Long to Beat/i, level: 3 }),
    ).toBeInTheDocument();
  });

  it("converts seconds to hours and shows correct labels", () => {
    const game = makeGame({
      timeToBeatHastily: hrs(10),
      timeToBeatNormally: hrs(25),
      timeToBeatCompletely: hrs(50),
    });
    render(<TimeToBeatCard game={game} />);

    expect(screen.getByText("Main Story")).toBeInTheDocument();
    expect(screen.getByText("10 hrs")).toBeInTheDocument();

    expect(screen.getByText("Main + Extras")).toBeInTheDocument();
    expect(screen.getByText("25 hrs")).toBeInTheDocument();

    expect(screen.getByText("Completionist")).toBeInTheDocument();
    expect(screen.getByText("50 hrs")).toBeInTheDocument();
  });

  it("only shows tiers with non-zero values", () => {
    const game = makeGame({
      timeToBeatHastily: 0,
      timeToBeatNormally: hrs(15),
      timeToBeatCompletely: hrs(40),
    });
    render(<TimeToBeatCard game={game} />);

    expect(screen.queryByText("Main Story")).not.toBeInTheDocument();
    expect(screen.getByText("Main + Extras")).toBeInTheDocument();
    expect(screen.getByText("Completionist")).toBeInTheDocument();
  });

  it("formats fractional hours correctly", () => {
    // 5.5 hours = 19800 seconds
    const game = makeGame({
      timeToBeatHastily: 19800,
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("5.5 hrs")).toBeInTheDocument();
  });

  it('formats sub-hour values as "<1 hr"', () => {
    // 30 minutes = 1800 seconds
    const game = makeGame({
      timeToBeatHastily: 1800,
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("<1 hr")).toBeInTheDocument();
  });

  it('formats exactly 1 hour as "1 hr"', () => {
    const game = makeGame({
      timeToBeatHastily: hrs(1),
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("1 hr")).toBeInTheDocument();
  });

  it("handles the bug case: 7200 seconds = 2 hrs, not 7200 hrs", () => {
    const game = makeGame({
      timeToBeatHastily: 0,
      timeToBeatNormally: 7200,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("2 hrs")).toBeInTheDocument();
    expect(screen.queryByText("7200 hrs")).not.toBeInTheDocument();
  });
});
