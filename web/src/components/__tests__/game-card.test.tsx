import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { GameCard } from "../game-card";
import { makeGame } from "@/test-utils/fixtures";

class MockIntersectionObserver {
  readonly root = null;
  readonly rootMargin = "";
  readonly thresholds = [];

  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderCard(game = makeGame()) {
  return render(
    <MemoryRouter initialEntries={["/library"]}>
      <GameCard game={game} showConsoleBadge />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe("GameCard", () => {
  beforeAll(() => {
    vi.stubGlobal("IntersectionObserver", MockIntersectionObserver);
  });

  afterAll(() => {
    vi.unstubAllGlobals();
  });

  it("does not render platform pills for a single-platform game", () => {
    renderCard();

    expect(
      screen.queryByTestId("game-platform-pills-game-1"),
    ).not.toBeInTheDocument();
  });

  it("renders alternate platform pills and navigates without following the card link", async () => {
    const user = userEvent.setup();
    const game = makeGame({
      id: "game-nes",
      title: "Mega Adventure",
      consoleId: "nes",
      consoleName: "Nintendo Entertainment System",
      platforms: [
        {
          gameId: "game-nes",
          consoleId: "nes",
          consoleName: "Nintendo Entertainment System",
          isPreferred: true,
        },
        {
          gameId: "game-snes",
          consoleId: "snes",
          consoleName: "SNES",
          isPreferred: false,
        },
      ],
    });
    renderCard(game);

    expect(
      screen.getByTestId("game-platform-pills-game-nes"),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("Current platform Nintendo Entertainment System"),
    ).toHaveTextContent("NES");

    await user.click(
      screen.getByRole("link", { name: "Open Mega Adventure on SNES" }),
    );

    expect(screen.getByTestId("location")).toHaveTextContent(
      "/games/game-snes",
    );
  });

  it("links the card cover to the represented platform even when another platform is preferred", async () => {
    const user = userEvent.setup();
    const game = makeGame({
      id: "game-nes",
      title: "Mega Adventure",
      consoleId: "nes",
      consoleName: "Nintendo Entertainment System",
      platforms: [
        {
          gameId: "game-nes",
          consoleId: "nes",
          consoleName: "Nintendo Entertainment System",
          isPreferred: false,
        },
        {
          gameId: "game-snes",
          consoleId: "snes",
          consoleName: "SNES",
          isPreferred: true,
        },
      ],
    });
    renderCard(game);

    await user.click(screen.getByRole("link", { name: "Open Mega Adventure" }));

    expect(screen.getByTestId("location")).toHaveTextContent(
      "/games/game-nes",
    );
  });
});
