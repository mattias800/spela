import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ThemeGrid } from "../theme-grid";
import type { Theme } from "@/types/api";

function makeTheme(overrides: Partial<Theme> = {}): Theme {
  return {
    id: "1",
    name: "Science Fiction",
    gameCount: 42,
    ...overrides,
  };
}

const mockThemes: Theme[] = [
  makeTheme({ id: "1", name: "Science Fiction", gameCount: 42 }),
  makeTheme({ id: "2", name: "Horror", gameCount: 28 }),
  makeTheme({ id: "3", name: "Fantasy", gameCount: 35 }),
];

function renderGrid(
  props: { themes?: Theme[] | undefined; isLoading?: boolean } = {},
) {
  const themes = "themes" in props ? props.themes : mockThemes;
  return render(
    <MemoryRouter>
      <ThemeGrid themes={themes} isLoading={props.isLoading ?? false} />
    </MemoryRouter>,
  );
}

describe("ThemeGrid", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders section with heading", () => {
    renderGrid();
    expect(
      screen.getByRole("heading", { name: "Browse by Theme", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders theme cards", () => {
    renderGrid();
    expect(screen.getByText("Science Fiction")).toBeInTheDocument();
    expect(screen.getByText("Horror")).toBeInTheDocument();
    expect(screen.getByText("Fantasy")).toBeInTheDocument();
  });

  it("shows game counts", () => {
    renderGrid();
    expect(screen.getByText("42 games")).toBeInTheDocument();
    expect(screen.getByText("28 games")).toBeInTheDocument();
    expect(screen.getByText("35 games")).toBeInTheDocument();
  });

  it("renders singular game count", () => {
    renderGrid({ themes: [makeTheme({ id: "10", name: "Test", gameCount: 1 })] });
    expect(screen.getByText("1 game")).toBeInTheDocument();
  });

  it("renders cards as list items", () => {
    renderGrid();
    const listItems = screen.getAllByRole("listitem");
    expect(listItems).toHaveLength(3);
  });

  it("renders the scrollable list with label", () => {
    renderGrid();
    expect(
      screen.getByRole("list", { name: "Browse by Theme" }),
    ).toBeInTheDocument();
  });

  it("links theme cards to theme detail pages", () => {
    renderGrid();
    const listItems = screen.getAllByRole("listitem");
    const hrefs = listItems.map((l) => l.getAttribute("href"));
    expect(hrefs).toContain("/explore/themes/1");
    expect(hrefs).toContain("/explore/themes/2");
    expect(hrefs).toContain("/explore/themes/3");
  });

  it("renders nothing when themes array is empty", () => {
    const { container } = renderGrid({ themes: [] });
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when themes is undefined and not loading", () => {
    const { container } = renderGrid({ themes: undefined, isLoading: false });
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when loading", () => {
    renderGrid({ isLoading: true, themes: undefined });
    expect(screen.getByTestId("theme-grid-skeleton")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Browse by Theme", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderGrid({ isLoading: true, themes: undefined });
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
});
