import { render, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { HeroCarousel } from "../hero-carousel";
import type { FeaturedGame } from "@/types/api";

function makeFeaturedGame(overrides: Partial<FeaturedGame> = {}): FeaturedGame {
  return {
    gameId: "1",
    title: "Test Game",
    heroUrl: "/hero/test.jpg",
    logoUrl: "/logo/test.png",
    consoleAbbreviation: "snes",
    consoleColor: "#805ad5",
    igdbCriticsRating: 92.5,
    genre: "RPG",
    isFavorite: false,
    isPlayLater: false,
    ...overrides,
  };
}

const mockGames: FeaturedGame[] = [
  makeFeaturedGame({ gameId: "1", title: "Game One" }),
  makeFeaturedGame({ gameId: "2", title: "Game Two", logoUrl: null }),
  makeFeaturedGame({ gameId: "3", title: "Game Three" }),
];

function renderCarousel(
  props: { games?: FeaturedGame[] | undefined; isLoading?: boolean } = {},
) {
  const games = "games" in props ? props.games : mockGames;
  return render(
    <MemoryRouter>
      <HeroCarousel
        games={games}
        isLoading={props.isLoading ?? false}
      />
    </MemoryRouter>,
  );
}

describe("HeroCarousel", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    // Mock matchMedia for prefers-reduced-motion
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders featured games", () => {
    renderCarousel();
    expect(screen.getByTestId("hero-carousel")).toBeInTheDocument();
    expect(screen.getByTestId("hero-slide-1")).toBeInTheDocument();
    expect(screen.getByTestId("hero-slide-2")).toBeInTheDocument();
    expect(screen.getByTestId("hero-slide-3")).toBeInTheDocument();
  });

  it("renders loading skeleton when loading", () => {
    renderCarousel({ isLoading: true, games: undefined });
    expect(screen.getByTestId("hero-carousel-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("hero-carousel")).not.toBeInTheDocument();
  });

  it("renders nothing when no games", () => {
    const { container } = renderCarousel({ games: [] });
    expect(screen.queryByTestId("hero-carousel")).not.toBeInTheDocument();
    expect(screen.queryByTestId("hero-carousel-skeleton")).not.toBeInTheDocument();
    // Check there's no output at all
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when games is undefined and not loading", () => {
    const { container } = renderCarousel({ games: undefined, isLoading: false });
    expect(container.innerHTML).toBe("");
  });

  it("shows logo image when logoUrl is available", () => {
    renderCarousel({
      games: [makeFeaturedGame({ gameId: "1", logoUrl: "/logo/test.png" })],
    });
    expect(screen.getByAltText("Test Game")).toBeInTheDocument();
  });

  it("falls back to text title when logoUrl is null", () => {
    renderCarousel({
      games: [makeFeaturedGame({ gameId: "2", title: "No Logo Game", logoUrl: null })],
    });
    expect(screen.getByTestId("hero-title-text-2")).toBeInTheDocument();
    expect(screen.getByText("No Logo Game")).toBeInTheDocument();
  });

  it("renders console badge", () => {
    renderCarousel({
      games: [makeFeaturedGame({ consoleAbbreviation: "snes" })],
    });
    expect(screen.getByText("SNES")).toBeInTheDocument();
  });

  it("renders rating", () => {
    renderCarousel({
      games: [makeFeaturedGame({ igdbCriticsRating: 92.5 })],
    });
    expect(screen.getByText("92.5")).toBeInTheDocument();
  });

  it("does not render genre chip (removed from carousel)", () => {
    renderCarousel({
      games: [makeFeaturedGame({ genre: "RPG" })],
    });
    expect(screen.queryByText("RPG")).not.toBeInTheDocument();
  });

  it("renders View Game button linking to game detail", () => {
    renderCarousel({
      games: [makeFeaturedGame({ gameId: "42" })],
    });
    const link = screen.getByTestId("hero-view-game-42");
    expect(link).toHaveAttribute("href", "/games/42");
  });

  it("renders navigation dots for multiple games", () => {
    renderCarousel();
    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(3);
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
    expect(tabs[1]).toHaveAttribute("aria-selected", "false");
  });

  it("does not render navigation dots for single game", () => {
    renderCarousel({
      games: [makeFeaturedGame()],
    });
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
  });

  it("renders arrow buttons for multiple games", () => {
    renderCarousel();
    expect(screen.getByLabelText("Previous slide")).toBeInTheDocument();
    expect(screen.getByLabelText("Next slide")).toBeInTheDocument();
  });

  it("does not render arrow buttons for single game", () => {
    renderCarousel({ games: [makeFeaturedGame()] });
    expect(screen.queryByLabelText("Previous slide")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Next slide")).not.toBeInTheDocument();
  });

  it("auto-rotates after 8 seconds", () => {
    renderCarousel();
    const tabs = screen.getAllByRole("tab");
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");

    act(() => {
      vi.advanceTimersByTime(8000);
    });

    const tabsAfter = screen.getAllByRole("tab");
    expect(tabsAfter[0]).toHaveAttribute("aria-selected", "false");
    expect(tabsAfter[1]).toHaveAttribute("aria-selected", "true");
  });

  it("pauses auto-rotation on hover", async () => {
    renderCarousel();
    const carousel = screen.getByTestId("hero-carousel");

    // Hover to pause
    await userEvent.hover(carousel);

    act(() => {
      vi.advanceTimersByTime(16000);
    });

    // Should still be on first slide
    const tabs = screen.getAllByRole("tab");
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");

    // Unhover to resume
    await userEvent.unhover(carousel);

    act(() => {
      vi.advanceTimersByTime(8000);
    });

    const tabsAfter = screen.getAllByRole("tab");
    expect(tabsAfter[1]).toHaveAttribute("aria-selected", "true");
  });

  it("clicking navigation dot changes slide", async () => {
    renderCarousel();
    const tabs = screen.getAllByRole("tab");

    await userEvent.click(tabs[2]);

    const tabsAfter = screen.getAllByRole("tab");
    expect(tabsAfter[2]).toHaveAttribute("aria-selected", "true");
  });

  it("clicking next arrow advances to next slide", async () => {
    renderCarousel();
    const nextBtn = screen.getByLabelText("Next slide");

    await userEvent.click(nextBtn);

    const tabs = screen.getAllByRole("tab");
    expect(tabs[1]).toHaveAttribute("aria-selected", "true");
  });

  it("clicking prev arrow goes to previous slide (wraps)", async () => {
    renderCarousel();
    const prevBtn = screen.getByLabelText("Previous slide");

    await userEvent.click(prevBtn);

    const tabs = screen.getAllByRole("tab");
    expect(tabs[2]).toHaveAttribute("aria-selected", "true");
  });

  it("has accessible region label", () => {
    renderCarousel();
    const region = screen.getByRole("region", {
      name: "Featured games carousel",
    });
    expect(region).toBeInTheDocument();
  });

  it("has accessible labels on navigation dots", () => {
    renderCarousel();
    expect(screen.getByLabelText("Go to slide 1")).toBeInTheDocument();
    expect(screen.getByLabelText("Go to slide 2")).toBeInTheDocument();
    expect(screen.getByLabelText("Go to slide 3")).toBeInTheDocument();
  });
});
