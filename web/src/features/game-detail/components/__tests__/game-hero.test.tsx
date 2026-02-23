import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
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
    discCount: 1,
    screenshotUrls: [],
    scrapeAttempts: 1,
    coverAspectRatio: 0.75,
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
  onAddToCollection: vi.fn(),
};

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

describe("GameHero", () => {
  it("does not render bare '0' when game.players is 0", () => {
    const game = makeGame({ players: 0 });
    renderWithQuery(<GameHero game={game} {...defaultProps} />);

    // Should render a MetaItem with "Players" label and "0" value, not a bare "0" text node
    expect(screen.getAllByText("Players").length).toBeGreaterThan(0);
    expect(screen.getAllByText("0").length).toBeGreaterThan(0);

    // Check that "0" appears within a MetaItem context
    const playersLabel = screen.getAllByText("Players")[0];
    const metaItem = playersLabel.closest("div")?.parentElement;
    expect(metaItem).toHaveTextContent("Players");
    expect(metaItem).toHaveTextContent("0");
  });

  it("does not render Players MetaItem when players is undefined", () => {
    const game = makeGame({ players: undefined });
    renderWithQuery(<GameHero game={game} {...defaultProps} />);

    expect(screen.queryByText("Players")).not.toBeInTheDocument();
  });

  it("renders actions menu button", () => {
    const game = makeGame();
    renderWithQuery(<GameHero game={game} {...defaultProps} />);

    expect(screen.getByTestId("actions-menu-btn")).toBeInTheDocument();
  });

  it("calls onPlay when play button is clicked", async () => {
    const onPlay = vi.fn();
    const game = makeGame();
    renderWithQuery(<GameHero game={game} {...defaultProps} onPlay={onPlay} />);

    await userEvent.click(screen.getByTestId("play-in-browser-btn"));
    expect(onPlay).toHaveBeenCalledOnce();
  });

  it("renders game title", () => {
    const game = makeGame({ title: "Super Mario Bros" });
    renderWithQuery(<GameHero game={game} {...defaultProps} />);

    expect(
      screen.getByRole("heading", { name: /Super Mario Bros/ }),
    ).toBeInTheDocument();
  });

  it("renders console badge", () => {
    const game = makeGame({ consoleName: "SNES" });
    renderWithQuery(<GameHero game={game} {...defaultProps} />);

    expect(screen.getByText("SNES")).toBeInTheDocument();
  });

  it("renders resume-btn instead of play-in-browser-btn when hasSaves is true", () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} hasSaves={true} />,
    );

    expect(screen.getByTestId("resume-btn")).toBeInTheDocument();
    expect(screen.queryByTestId("play-in-browser-btn")).not.toBeInTheDocument();
  });

  it("calls onPlay when resume-btn is clicked", async () => {
    const onPlay = vi.fn();
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} hasSaves={true} onPlay={onPlay} />,
    );

    await userEvent.click(screen.getByTestId("resume-btn"));
    expect(onPlay).toHaveBeenCalledOnce();
  });

  it("shows admin-only 'Scrape Metadata' in actions menu for admin users", async () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} isAdmin={true} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(screen.getByText("Scrape Metadata")).toBeInTheDocument();
  });

  it("hides 'Scrape Metadata' in actions menu for non-admin users", async () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} isAdmin={false} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(screen.queryByText("Scrape Metadata")).not.toBeInTheDocument();
  });

  it("shows 'Unfavorite' in actions menu when game is favorited", async () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} isFavorite={true} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(screen.getByText("Unfavorite")).toBeInTheDocument();
    expect(screen.queryByText("Favorite")).not.toBeInTheDocument();
  });

  it("shows 'In Queue' in actions menu when game is in play later", async () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} isInPlayLater={true} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(screen.getByText("In Queue")).toBeInTheDocument();
    expect(screen.queryByText("Play Later")).not.toBeInTheDocument();
  });

  it("calls onToggleFavorite when Favorite is clicked in actions menu", async () => {
    const onToggleFavorite = vi.fn();
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} onToggleFavorite={onToggleFavorite} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    await userEvent.click(screen.getByText("Favorite"));
    expect(onToggleFavorite).toHaveBeenCalledOnce();
  });

  it("calls onAddToCollection when clicked in actions menu", async () => {
    const onAddToCollection = vi.fn();
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} onAddToCollection={onAddToCollection} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    await userEvent.click(screen.getByText("Add to Collection"));
    expect(onAddToCollection).toHaveBeenCalledOnce();
  });

  it("hides 'Add to Collection' in actions menu when callback not provided", async () => {
    const game = makeGame();
    const { onAddToCollection: _, ...propsWithoutAddToCollection } = defaultProps;
    renderWithQuery(
      <GameHero game={game} {...propsWithoutAddToCollection} />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(screen.queryByText("Add to Collection")).not.toBeInTheDocument();
  });

  it("shows 'New Game' in split button menu when hasSaves with onPlayFresh", async () => {
    const onPlayFresh = vi.fn();
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} hasSaves={true} onPlayFresh={onPlayFresh} />,
    );

    await userEvent.click(screen.getByTestId("split-menu-btn"));
    expect(screen.getByText("New Game")).toBeInTheDocument();
  });

  it("calls onPlayFresh when 'New Game' is clicked in split button menu", async () => {
    const onPlayFresh = vi.fn();
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} hasSaves={true} onPlayFresh={onPlayFresh} />,
    );

    await userEvent.click(screen.getByTestId("split-menu-btn"));
    await userEvent.click(screen.getByText("New Game"));
    expect(onPlayFresh).toHaveBeenCalledOnce();
  });

  it("disables play button when canPlayInBrowser is false", () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} canPlayInBrowser={false} />,
    );

    expect(screen.getByTestId("play-in-browser-btn")).toBeDisabled();
  });
});
