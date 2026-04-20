import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { DeveloperStatsCard } from "@/features/explore/components/developer-stats-card";
import type { EntityUserStats } from "@/types/api";
import { makeGame } from "@/test-utils/fixtures";


function renderCard(userStats: EntityUserStats, totalGames = 10) {
  return render(
    <MemoryRouter>
      <DeveloperStatsCard userStats={userStats} totalGames={totalGames} />
    </MemoryRouter>,
  );
}

describe("DeveloperStatsCard", () => {
  const baseStats: EntityUserStats = {
    totalPlayTime: 7200,
    gamesPlayed: 3,
    favoriteCount: 2,
    mostPlayedGame: makeGame({
      id: "42",
      title: "Mega Man X",
      coverUrl: "/covers/mmx.jpg",
      totalPlayTime: 3600,
    }),
  };

  it("renders the section heading", () => {
    renderCard(baseStats);
    expect(
      screen.getByRole("heading", { name: "Your Stats" }),
    ).toBeInTheDocument();
  });

  it("renders total play time", () => {
    renderCard(baseStats);
    const card = screen.getByTestId("developer-user-stats");
    expect(card).toHaveTextContent("2h 0m");
  });

  it("renders games played fraction", () => {
    renderCard(baseStats, 10);
    const card = screen.getByTestId("developer-user-stats");
    expect(card).toHaveTextContent("3/10");
  });

  it("renders favorite count", () => {
    renderCard(baseStats);
    const card = screen.getByTestId("developer-user-stats");
    expect(card).toHaveTextContent("2");
  });

  it("renders most played game name", () => {
    renderCard(baseStats);
    // Name appears in both the stat summary and the cover link
    expect(screen.getAllByText("Mega Man X").length).toBeGreaterThanOrEqual(1);
  });

  it("renders most played game cover when available", () => {
    renderCard(baseStats);
    const mpg = screen.getByTestId("developer-most-played-game");
    const img = mpg.querySelector("img");
    expect(img).toHaveAttribute("src", "/covers/mmx.jpg");
  });

  it("renders without most played game when null", () => {
    renderCard({
      ...baseStats,
      mostPlayedGame: null,
    });
    expect(
      screen.queryByTestId("developer-most-played-game"),
    ).not.toBeInTheDocument();
  });
});
