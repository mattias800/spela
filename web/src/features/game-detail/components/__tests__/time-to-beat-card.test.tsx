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
      timeToBeatNormally: 25,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByTestId("time-to-beat-card")).toBeInTheDocument();
  });

  it('displays "How Long to Beat" heading', () => {
    const game = makeGame({ timeToBeatNormally: 10 });
    render(<TimeToBeatCard game={game} />);
    expect(
      screen.getByRole("heading", { name: /How Long to Beat/i, level: 3 }),
    ).toBeInTheDocument();
  });

  it("shows correct labels and formatted hours", () => {
    const game = makeGame({
      timeToBeatHastily: 10,
      timeToBeatNormally: 25,
      timeToBeatCompletely: 50,
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
      timeToBeatNormally: 15,
      timeToBeatCompletely: 40,
    });
    render(<TimeToBeatCard game={game} />);

    expect(screen.queryByText("Main Story")).not.toBeInTheDocument();
    expect(screen.getByText("Main + Extras")).toBeInTheDocument();
    expect(screen.getByText("Completionist")).toBeInTheDocument();
  });

  it("formats fractional hours correctly", () => {
    const game = makeGame({
      timeToBeatHastily: 5.5,
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("5.5 hrs")).toBeInTheDocument();
  });

  it('formats sub-hour values as "<1 hr"', () => {
    const game = makeGame({
      timeToBeatHastily: 0.5,
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("<1 hr")).toBeInTheDocument();
  });

  it('formats exactly 1 hour as "1 hr"', () => {
    const game = makeGame({
      timeToBeatHastily: 1,
      timeToBeatNormally: 0,
      timeToBeatCompletely: 0,
    });
    render(<TimeToBeatCard game={game} />);
    expect(screen.getByText("1 hr")).toBeInTheDocument();
  });
});
