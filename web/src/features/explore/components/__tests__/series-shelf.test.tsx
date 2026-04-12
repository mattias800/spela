import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SeriesShelf } from "../series-shelf";
import type { FeaturedSeries } from "@/types/api";

function makeSeries(overrides: Partial<FeaturedSeries> = {}): FeaturedSeries {
  return {
    id: "1",
    name: "Super Mario",
    libraryGames: 5,
    totalGames: 12,
    consoleCount: 3,
    heroUrl: "/hero/mario.jpg",
    ...overrides,
  };
}

const mockSeries: FeaturedSeries[] = [
  makeSeries({ id: "1", name: "Super Mario", libraryGames: 5, totalGames: 12, consoleCount: 3 }),
  makeSeries({ id: "2", name: "The Legend of Zelda", libraryGames: 4, totalGames: 10, consoleCount: 2 }),
  makeSeries({ id: "3", name: "Metroid", libraryGames: 3, totalGames: 8, consoleCount: 2 }),
];

function renderShelf(
  props: { series?: FeaturedSeries[] | undefined; isLoading?: boolean } = {},
) {
  const series = "series" in props ? props.series : mockSeries;
  return render(
    <MemoryRouter>
      <SeriesShelf series={series} isLoading={props.isLoading ?? false} />
    </MemoryRouter>,
  );
}

describe("SeriesShelf", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders section with heading", () => {
    renderShelf();
    expect(
      screen.getByRole("heading", { name: "Browse by Series", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders series cards", () => {
    renderShelf();
    expect(screen.getByText("Super Mario")).toBeInTheDocument();
    expect(screen.getByText("The Legend of Zelda")).toBeInTheDocument();
    expect(screen.getByText("Metroid")).toBeInTheDocument();
  });

  it("shows game counts", () => {
    renderShelf();
    expect(screen.getByText(/5\/12 games/)).toBeInTheDocument();
    expect(screen.getByText(/4\/10 games/)).toBeInTheDocument();
    expect(screen.getByText(/3\/8 games/)).toBeInTheDocument();
  });

  it("shows console count", () => {
    renderShelf();
    expect(screen.getByText(/across 3 consoles/)).toBeInTheDocument();
    expect(screen.getAllByText(/across 2 consoles/)).toHaveLength(2);
  });

  it("renders singular game count", () => {
    renderShelf({
      series: [makeSeries({ id: "10", name: "Test", libraryGames: 1, totalGames: 1, consoleCount: 1 })],
    });
    expect(screen.getByText(/1\/1 game/)).toBeInTheDocument();
    expect(screen.getByText(/across 1 console/)).toBeInTheDocument();
  });

  it("renders cards as list items", () => {
    renderShelf();
    const listItems = screen.getAllByRole("listitem");
    expect(listItems).toHaveLength(3);
  });

  it("renders the scrollable list with label", () => {
    renderShelf();
    expect(
      screen.getByRole("list", { name: "Browse by Series" }),
    ).toBeInTheDocument();
  });

  it("links series cards to series detail pages", () => {
    renderShelf();
    const links = screen.getAllByRole("listitem");
    // The Link wraps the listitem content
    expect(links[0].closest("a")).toHaveAttribute(
      "href",
      "/explore/series/1",
    );
    expect(links[1].closest("a")).toHaveAttribute(
      "href",
      "/explore/series/2",
    );
    expect(links[2].closest("a")).toHaveAttribute(
      "href",
      "/explore/series/3",
    );
  });

  it("renders nothing when series array is empty", () => {
    const { container } = renderShelf({ series: [] });
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when series is undefined and not loading", () => {
    const { container } = renderShelf({ series: undefined, isLoading: false });
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when loading", () => {
    renderShelf({ isLoading: true, series: undefined });
    expect(screen.getByTestId("series-shelf-skeleton")).toBeInTheDocument();
  });

  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderShelf({ isLoading: true, series: undefined });
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
});
