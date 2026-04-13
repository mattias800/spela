import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ToastProvider } from "@/components/ui";
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
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ToastProvider>{ui}</ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("GameHero", () => {
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

  it("disables play button when canPlayInBrowser is false", () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} canPlayInBrowser={false} />,
    );

    expect(screen.getByTestId("play-in-browser-btn")).toBeDisabled();
  });

  it("disables play button when biosMissing is true", () => {
    const game = makeGame();
    renderWithQuery(
      <GameHero game={game} {...defaultProps} biosMissing={true} />,
    );

    expect(screen.getByTestId("play-in-browser-btn")).toBeDisabled();
    expect(screen.getByTestId("play-in-browser-btn")).toHaveAttribute(
      "title",
      "Missing required BIOS files",
    );
  });

  describe("non-playable games", () => {
    it("shows Download ROM button instead of Play button when game is not playable", () => {
      const game = makeGame({ playable: false });
      renderWithQuery(<GameHero game={game} {...defaultProps} />);

      expect(screen.getByTestId("download-rom-btn")).toBeInTheDocument();
      expect(screen.getByText("Download ROM")).toBeInTheDocument();
      expect(screen.queryByTestId("play-in-browser-btn")).not.toBeInTheDocument();
    });

    it("calls onDownloadRom when Download ROM button is clicked", async () => {
      const onDownloadRom = vi.fn();
      const game = makeGame({ playable: false });
      renderWithQuery(
        <GameHero game={game} {...defaultProps} onDownloadRom={onDownloadRom} />,
      );

      await userEvent.click(screen.getByTestId("download-rom-btn"));
      expect(onDownloadRom).toHaveBeenCalledOnce();
    });

    it("shows External Emulator badge when game is not playable", () => {
      const game = makeGame({ playable: false });
      renderWithQuery(<GameHero game={game} {...defaultProps} />);

      expect(screen.getByTestId("external-emulator-badge")).toBeInTheDocument();
      expect(screen.getByText("External Emulator")).toBeInTheDocument();
    });

    it("does not show External Emulator badge when game is playable", () => {
      const game = makeGame({ playable: true });
      renderWithQuery(<GameHero game={game} {...defaultProps} />);

      expect(screen.queryByTestId("external-emulator-badge")).not.toBeInTheDocument();
    });
  });
});
