import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { TimeToBeatCard } from "../time-to-beat-card";
import type { } from "@/types/api";
import { makeGame } from "@/test-utils/fixtures";


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

  it('displays "Time to Beat" heading', () => {
    const game = makeGame({ timeToBeatNormally: hrs(10) });
    render(<TimeToBeatCard game={game} />);
    expect(
      screen.getByRole("heading", { name: /Time to Beat/i, level: 3 }),
    ).toBeInTheDocument();
  });

  it("does not render progress bars (#1099)", () => {
    const game = makeGame({
      timeToBeatHastily: hrs(10),
      timeToBeatNormally: hrs(25),
      timeToBeatCompletely: hrs(50),
    });
    const { container } = render(<TimeToBeatCard game={game} />);
    // Pre-#1099: each tier had a bg-brand-400 / bg-amber-400 / bg-purple-400
    // bar element. The compact redesign drops bars entirely.
    expect(container.querySelectorAll("progress").length).toBe(0);
    expect(container.querySelector("[class*='bg-brand-400']")).toBeNull();
    expect(container.querySelector("[class*='bg-amber-400']")).toBeNull();
    expect(container.querySelector("[class*='bg-purple-400']")).toBeNull();
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

  it("renders all three tier labels even when some values are missing (#1099)", () => {
    // Pre-#1099 tests asserted that zero-tiers were absent from the DOM
    // (`.filter((t) => t.hours > 0)`). The compact redesign keeps the grid
    // rhythm by rendering placeholder "—" cards for missing tiers.
    const game = makeGame({
      timeToBeatHastily: 0,
      timeToBeatNormally: hrs(15),
      timeToBeatCompletely: hrs(40),
    });
    render(<TimeToBeatCard game={game} />);

    expect(screen.getByText("Main Story")).toBeInTheDocument();
    expect(screen.getByText("Main + Extras")).toBeInTheDocument();
    expect(screen.getByText("Completionist")).toBeInTheDocument();
    // Missing tier shows the em-dash placeholder, not a value.
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.getByText("15 hrs")).toBeInTheDocument();
    expect(screen.getByText("40 hrs")).toBeInTheDocument();
  });

  it("attaches an explanatory tooltip to each tier card (#1099)", () => {
    const game = makeGame({
      timeToBeatHastily: hrs(10),
      timeToBeatNormally: hrs(25),
      timeToBeatCompletely: hrs(50),
    });
    const { container } = render(<TimeToBeatCard game={game} />);
    // Per-tier disambiguation moved from inline icons (#1099 dropped them)
    // to a hover tooltip. Three tiers → three tooltips.
    const tierCards = container.querySelectorAll("[title]");
    expect(tierCards.length).toBe(3);
    expect(tierCards[0].getAttribute("title")).toMatch(/main story/i);
    expect(tierCards[1].getAttribute("title")).toMatch(/side content/i);
    expect(tierCards[2].getAttribute("title")).toMatch(/100% completion/i);
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
