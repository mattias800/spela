import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { DeveloperSpotlight } from "../developer-spotlight";
import type { DeveloperSpotlightResponse, Game } from "@/types/api";

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "1",
    title: "Test Game",
    consoleId: "snes",
    consoleName: "SNES",
    fileName: "test.sfc",
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

const mockSpotlight: DeveloperSpotlightResponse = {
  name: "Capcom",
  gameCount: 10,
  avgRating: 85.5,
  consoles: ["SNES", "GBA"],
  topGames: [
    makeGame({ id: "1", title: "Mega Man X" }),
    makeGame({ id: "2", title: "Street Fighter II" }),
    makeGame({ id: "3", title: "Breath of Fire" }),
  ],
  heroUrl: "/hero/capcom.jpg",
};

function renderSpotlight(
  props: {
    spotlight?: DeveloperSpotlightResponse | undefined;
    isLoading?: boolean;
  } = {},
) {
  return render(
    <MemoryRouter>
      <DeveloperSpotlight
        spotlight={"spotlight" in props ? props.spotlight : mockSpotlight}
        isLoading={props.isLoading ?? false}
        onToggleFavorite={vi.fn()}
        onTogglePlayLater={vi.fn()}
      />
    </MemoryRouter>,
  );
}

describe("DeveloperSpotlight", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the spotlight section", () => {
    renderSpotlight();
    expect(screen.getByTestId("developer-spotlight")).toBeInTheDocument();
  });

  it("renders developer name as heading", () => {
    renderSpotlight();
    expect(
      screen.getByRole("heading", { name: "Capcom", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders Developer Spotlight label", () => {
    renderSpotlight();
    expect(screen.getByText("Developer Spotlight")).toBeInTheDocument();
  });

  it("renders stats subtitle", () => {
    renderSpotlight();
    expect(screen.getByText(/10 games/)).toBeInTheDocument();
    expect(screen.getByText(/Avg rating: 85.5/)).toBeInTheDocument();
    expect(screen.getByText(/SNES, GBA/)).toBeInTheDocument();
  });

  it("renders top games", () => {
    renderSpotlight();
    expect(screen.getByText("Mega Man X")).toBeInTheDocument();
    expect(screen.getByText("Street Fighter II")).toBeInTheDocument();
    expect(screen.getByText("Breath of Fire")).toBeInTheDocument();
  });

  it("renders See all games link", () => {
    renderSpotlight();
    const link = screen.getByText("See all games");
    expect(link.closest("a")).toHaveAttribute(
      "href",
      "/explore/developers/Capcom",
    );
  });

  it("renders hero background image", () => {
    renderSpotlight();
    const img = screen
      .getByTestId("developer-spotlight")
      .querySelector("img[src='/hero/capcom.jpg']");
    expect(img).toBeInTheDocument();
  });

  it("renders game list items", () => {
    renderSpotlight();
    const listItems = screen.getAllByRole("listitem");
    expect(listItems).toHaveLength(3);
  });

  it("renders nothing when spotlight is undefined and not loading", () => {
    const { container } = renderSpotlight({
      spotlight: undefined,
      isLoading: false,
    });
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when loading", () => {
    renderSpotlight({ isLoading: true, spotlight: undefined });
    expect(
      screen.getByTestId("developer-spotlight-skeleton"),
    ).toBeInTheDocument();
  });

  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderSpotlight({
      isLoading: true,
      spotlight: undefined,
    });
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
});
