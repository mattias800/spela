import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { GameHero } from "../game-hero";
import type { Game } from "@/types/api";

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "game-1",
    title: "Test Game",
    consoleId: "nes",
    consoleName: "NES",
    fileName: "test.nes",
    fileSize: 1024,
    screenshotUrls: [],
    scrapeAttempts: 1,
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

const defaultProps = {
  canPlayInBrowser: true,
  isAdmin: false,
  isFavorite: false,
  isInPlayLater: false,
  isScraping: false,
  onPlay: vi.fn(),
  onScrape: vi.fn(),
  onToggleFavorite: vi.fn(),
  onTogglePlayLater: vi.fn(),
};

describe("GameHero", () => {
  it("does not render bare '0' when game.players is 0", () => {
    const game = makeGame({ players: 0 });
    render(<GameHero game={game} {...defaultProps} />);

    // Should render a MetaItem with "Players: 0", not a bare "0" text node
    expect(screen.getByText("Players:")).toBeInTheDocument();
    expect(screen.getByText("0")).toBeInTheDocument();

    // Check that "0" appears within a MetaItem context
    const playersLabel = screen.getByText("Players:");
    const metaItem = playersLabel.closest("div");
    expect(metaItem).toHaveTextContent("Players: 0");
  });

  it("does not render Players MetaItem when players is undefined", () => {
    const game = makeGame({ players: undefined });
    render(<GameHero game={game} {...defaultProps} />);

    expect(screen.queryByText("Players:")).not.toBeInTheDocument();
  });

  it("renders overflow menu button", () => {
    const game = makeGame();
    render(<GameHero game={game} {...defaultProps} />);

    expect(screen.getByTestId("overflow-menu-btn")).toBeInTheDocument();
  });

  it("calls onPlay when play button is clicked", async () => {
    const onPlay = vi.fn();
    const game = makeGame();
    render(<GameHero game={game} {...defaultProps} onPlay={onPlay} />);

    await userEvent.click(screen.getByTestId("play-in-browser-btn"));
    expect(onPlay).toHaveBeenCalledOnce();
  });

  it("renders game title", () => {
    const game = makeGame({ title: "Super Mario Bros" });
    render(<GameHero game={game} {...defaultProps} />);

    expect(
      screen.getByRole("heading", { name: /Super Mario Bros/ }),
    ).toBeInTheDocument();
  });

  it("renders console badge", () => {
    const game = makeGame({ consoleName: "SNES" });
    render(<GameHero game={game} {...defaultProps} />);

    expect(screen.getByText("SNES")).toBeInTheDocument();
  });
});
