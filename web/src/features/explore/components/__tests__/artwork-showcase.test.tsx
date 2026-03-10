import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ArtworkShowcase } from "../artwork-showcase";
import type { ArtworkItem } from "@/types/api";

function makeArtwork(overrides: Partial<ArtworkItem> = {}): ArtworkItem {
  return {
    url: "/artwork/default.jpg",
    width: 1920,
    height: 1080,
    gameId: "game-1",
    gameTitle: "Test Game",
    consoleName: "SNES",
    consoleAbbreviation: "snes",
    consoleColor: "#805ad5",
    ...overrides,
  };
}

const mockArtworks: ArtworkItem[] = [
  makeArtwork({
    url: "/artwork/mario.jpg",
    gameId: "game-1",
    gameTitle: "Super Mario World",
    consoleName: "SNES",
  }),
  makeArtwork({
    url: "/artwork/zelda.jpg",
    gameId: "game-2",
    gameTitle: "A Link to the Past",
    consoleName: "SNES",
  }),
  makeArtwork({
    url: "/artwork/metroid.jpg",
    gameId: "game-3",
    gameTitle: "Super Metroid",
    consoleName: "SNES",
  }),
];

function renderShowcase(
  props: {
    artworks?: ArtworkItem[] | undefined;
    isLoading?: boolean;
  } = {},
) {
  const artworks = "artworks" in props ? props.artworks : mockArtworks;
  return render(
    <MemoryRouter>
      <ArtworkShowcase
        artworks={artworks}
        isLoading={props.isLoading ?? false}
      />
    </MemoryRouter>,
  );
}

describe("ArtworkShowcase", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders section with heading", () => {
    renderShowcase();
    expect(
      screen.getByRole("heading", { name: "Artwork Showcase", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders artwork cards", () => {
    renderShowcase();
    const cards = screen.getAllByTestId("artwork-card");
    expect(cards).toHaveLength(3);
  });

  it("renders artwork images", () => {
    renderShowcase();
    const images = screen.getAllByRole("img");
    expect(
      images.some(
        (img) => img.getAttribute("src") === "/artwork/mario.jpg",
      ),
    ).toBe(true);
    expect(
      images.some(
        (img) => img.getAttribute("src") === "/artwork/zelda.jpg",
      ),
    ).toBe(true);
  });

  it("shows game titles on cards", () => {
    renderShowcase();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("A Link to the Past")).toBeInTheDocument();
    expect(screen.getByText("Super Metroid")).toBeInTheDocument();
  });

  it("shows console names on cards", () => {
    renderShowcase();
    // All three cards have "SNES" as consoleName
    const snesTexts = screen.getAllByText("SNES");
    expect(snesTexts.length).toBeGreaterThanOrEqual(3);
  });

  it("navigates to game detail on click", () => {
    renderShowcase();
    const cards = screen.getAllByTestId("artwork-card");
    expect(cards[0]).toHaveAttribute("href", "/games/game-1");
    expect(cards[1]).toHaveAttribute("href", "/games/game-2");
    expect(cards[2]).toHaveAttribute("href", "/games/game-3");
  });

  it("renders loading skeleton when loading", () => {
    renderShowcase({ isLoading: true, artworks: undefined });
    expect(
      screen.getByTestId("artwork-showcase-skeleton"),
    ).toBeInTheDocument();
  });

  it("renders nothing when artworks is empty", () => {
    const { container } = renderShowcase({ artworks: [] });
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when artworks is undefined and not loading", () => {
    const { container } = renderShowcase({
      artworks: undefined,
      isLoading: false,
    });
    expect(container.innerHTML).toBe("");
  });

  it("renders browse screenshots link", () => {
    renderShowcase();
    const link = screen.getByTestId("browse-screenshots-link");
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute("href", "/explore/gallery");
  });

  it("renders browse covers link", () => {
    renderShowcase();
    const link = screen.getByTestId("browse-covers-link");
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute("href", "/explore/covers");
  });

  it("limits displayed artworks to 10", () => {
    const manyArtworks = Array.from({ length: 15 }, (_, i) =>
      makeArtwork({
        url: `/artwork/${i}.jpg`,
        gameId: `game-${i}`,
        gameTitle: `Game ${i}`,
      }),
    );
    renderShowcase({ artworks: manyArtworks });
    const cards = screen.getAllByTestId("artwork-card");
    expect(cards).toHaveLength(10);
  });

  it("renders the scrollable list with label", () => {
    renderShowcase();
    expect(
      screen.getByRole("list", { name: "Artwork showcase" }),
    ).toBeInTheDocument();
  });
});
