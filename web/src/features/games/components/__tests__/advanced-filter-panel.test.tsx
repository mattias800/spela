import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import {
  AdvancedFilterPanel,
  savedSearchToFilters,
} from "../advanced-filter-panel";
import type { GameFilters, Console, Theme, Keyword, SavedSearch } from "@/types/api";

const makeConsole = (abbr: string, name: string): Console => ({
  id: abbr,
  name,
  abbreviation: abbr,
  extensions: [],
  defaultCore: "",
  coverAspectRatio: 0.75,
  colorTheme: "",
  generation: 4,
  iconUrl: "",
  logoUrl: "",
  gameCount: 10,
  saveStateSupport: true,
  saveStatePolicy: "small",
  browserPlayable: false,
  playable: true,
  code: abbr,
  emulatorJsCore: "",
    webEmulator: "",
  logoPngUrl: "",
  maker: { code: "", name: "" },
  mediaType: { code: "", name: "", category: { code: "", name: "" } },
  releaseYear: null,
  unitsSold: null,
  summary: null,
  createdAt: "",
  updatedAt: "",
});

const testConsoles: Console[] = [
  makeConsole("SNES", "Super Nintendo"),
  makeConsole("NES", "Nintendo"),
  makeConsole("GBA", "Game Boy Advance"),
];

const testThemes: Theme[] = [
  { id: "17", name: "Fantasy", gameCount: 25 },
  { id: "18", name: "Sci-fi", gameCount: 15 },
];

const testKeywords: Keyword[] = [
  { id: "100", name: "Retro", gameCount: 50 },
  { id: "200", name: "Classic", gameCount: 30 },
];

const testSavedSearches: SavedSearch[] = [
  {
    id: "1",
    name: "SNES RPGs",
    filters: { consoles: "SNES", genres: "RPG" },
    createdAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "2",
    name: "High-rated Action",
    filters: { genres: "Action", ratingMin: 80 },
    createdAt: "2026-01-02T00:00:00Z",
  },
];

function renderPanel(overrides: Partial<React.ComponentProps<typeof AdvancedFilterPanel>> = {}) {
  const defaultProps: React.ComponentProps<typeof AdvancedFilterPanel> = {
    filters: {},
    onFiltersChange: vi.fn(),
    consoles: testConsoles,
    themes: testThemes,
    keywords: testKeywords,
    savedSearches: testSavedSearches,
    onSaveSearch: vi.fn(),
    onDeleteSearch: vi.fn(),
    onApplySearch: vi.fn(),
    totalResults: 42,
    isOpen: true,
    onToggle: vi.fn(),
    ...overrides,
  };

  return { ...render(<AdvancedFilterPanel {...defaultProps} />), props: defaultProps };
}

describe("AdvancedFilterPanel", () => {
  it("renders toggle button", () => {
    renderPanel({ isOpen: false });
    expect(screen.getByTestId("advanced-filter-toggle")).toBeInTheDocument();
    expect(screen.getByText("Filters")).toBeInTheDocument();
  });

  it("shows active filter count badge", () => {
    renderPanel({
      isOpen: false,
      filters: { consoles: ["SNES"], genres: ["RPG"] },
    });
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows panel when open", () => {
    renderPanel({ isOpen: true });
    expect(screen.getByText("Advanced Filters")).toBeInTheDocument();
    expect(screen.getByText("42 games match")).toBeInTheDocument();
  });

  it("does not show panel when closed", () => {
    renderPanel({ isOpen: false });
    expect(screen.queryByText("Advanced Filters")).not.toBeInTheDocument();
  });

  it("calls onToggle when toggle button is clicked", async () => {
    const user = userEvent.setup();
    const { props } = renderPanel({ isOpen: false });
    await user.click(screen.getByTestId("advanced-filter-toggle"));
    expect(props.onToggle).toHaveBeenCalled();
  });

  it("renders console chips", () => {
    renderPanel();
    const consoleFilter = screen.getByTestId("console-filter");
    expect(within(consoleFilter).getByText("Super Nintendo")).toBeInTheDocument();
    expect(within(consoleFilter).getByText("Nintendo")).toBeInTheDocument();
    expect(within(consoleFilter).getByText("Game Boy Advance")).toBeInTheDocument();
  });

  it("hides console chips when hideConsoleFilter is true", () => {
    renderPanel({ hideConsoleFilter: true });
    expect(screen.queryByTestId("console-filter")).not.toBeInTheDocument();
  });

  it("shows console chips when hideConsoleFilter is false", () => {
    renderPanel({ hideConsoleFilter: false });
    expect(screen.getByTestId("console-filter")).toBeInTheDocument();
  });

  it("toggles console selection", async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    renderPanel({ onFiltersChange });
    const consoleFilter = screen.getByTestId("console-filter");
    await user.click(within(consoleFilter).getByText("Super Nintendo"));
    expect(onFiltersChange).toHaveBeenCalled();
    const updater = onFiltersChange.mock.calls[0][0];
    const result = updater({});
    expect(result.consoles).toEqual(["SNES"]);
    expect(result.page).toBe(1);
  });

  it("deselects a selected console", async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    renderPanel({
      onFiltersChange,
      filters: { consoles: ["SNES", "NES"] },
    });
    const consoleFilter = screen.getByTestId("console-filter");
    await user.click(within(consoleFilter).getByText("Super Nintendo"));
    const updater = onFiltersChange.mock.calls[0][0];
    const result = updater({ consoles: ["SNES", "NES"] });
    expect(result.consoles).toEqual(["NES"]);
  });

  it("renders genre chips", () => {
    renderPanel();
    const genreFilter = screen.getByTestId("genre-filter");
    expect(within(genreFilter).getByText("Action")).toBeInTheDocument();
    expect(within(genreFilter).getByText("RPG")).toBeInTheDocument();
  });

  it("renders theme chips", () => {
    renderPanel();
    const themeFilter = screen.getByTestId("theme-filter");
    expect(within(themeFilter).getByText("Fantasy (25)")).toBeInTheDocument();
    expect(within(themeFilter).getByText("Sci-fi (15)")).toBeInTheDocument();
  });

  it("renders keyword chips", () => {
    renderPanel();
    const keywordFilter = screen.getByTestId("keyword-filter");
    expect(within(keywordFilter).getByText("Retro (50)")).toBeInTheDocument();
    expect(within(keywordFilter).getByText("Classic (30)")).toBeInTheDocument();
  });

  it("renders year range inputs", () => {
    renderPanel();
    const yearFilter = screen.getByTestId("year-filter");
    expect(within(yearFilter).getByTestId("year-filter-min")).toBeInTheDocument();
    expect(within(yearFilter).getByTestId("year-filter-max")).toBeInTheDocument();
  });

  it("renders rating range inputs", () => {
    renderPanel();
    const ratingFilter = screen.getByTestId("rating-filter");
    expect(within(ratingFilter).getByTestId("rating-filter-min")).toBeInTheDocument();
    expect(within(ratingFilter).getByTestId("rating-filter-max")).toBeInTheDocument();
  });

  it("renders play status chips", () => {
    renderPanel();
    const statusFilter = screen.getByTestId("play-status-filter");
    expect(within(statusFilter).getByText("Any")).toBeInTheDocument();
    expect(within(statusFilter).getByText("Unplayed")).toBeInTheDocument();
    expect(within(statusFilter).getByText("Played")).toBeInTheDocument();
    expect(within(statusFilter).getByText("Favorited")).toBeInTheDocument();
    expect(within(statusFilter).getByText("Play Later")).toBeInTheDocument();
  });

  it("toggles play status", async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    renderPanel({ onFiltersChange });
    await user.click(screen.getByText("Unplayed"));
    const updater = onFiltersChange.mock.calls[0][0];
    const result = updater({});
    expect(result.playStatus).toBe("unplayed");
  });

  it("renders developer and publisher inputs", () => {
    renderPanel();
    expect(screen.getByTestId("developer-filter")).toBeInTheDocument();
    expect(screen.getByTestId("publisher-filter")).toBeInTheDocument();
  });

  it("clears all filters", async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    renderPanel({
      onFiltersChange,
      filters: { consoles: ["SNES"], genres: ["RPG"], developer: "Nintendo" },
    });
    await user.click(screen.getByTestId("clear-filters"));
    const updater = onFiltersChange.mock.calls[0][0];
    const result = updater({
      consoles: ["SNES"],
      genres: ["RPG"],
      developer: "Nintendo",
      sortBy: "title",
      sortOrder: "asc",
      pageSize: 48,
      search: "mario",
    });
    // Should preserve search, sort, and pageSize but clear filter fields
    expect(result.consoles).toBeUndefined();
    expect(result.genres).toBeUndefined();
    expect(result.developer).toBeUndefined();
    expect(result.search).toBe("mario");
    expect(result.sortBy).toBe("title");
  });

  // --- Saved Searches ---

  it("renders saved searches list", () => {
    renderPanel();
    expect(screen.getByText("SNES RPGs")).toBeInTheDocument();
    expect(screen.getByText("High-rated Action")).toBeInTheDocument();
  });

  it("applies a saved search", async () => {
    const user = userEvent.setup();
    const onApplySearch = vi.fn();
    renderPanel({ onApplySearch });
    const items = screen.getAllByTestId("saved-search-apply");
    await user.click(items[0]);
    expect(onApplySearch).toHaveBeenCalledWith({ consoles: "SNES", genres: "RPG" });
  });

  it("shows save button when filters are active", () => {
    renderPanel({ filters: { consoles: ["SNES"] } });
    expect(screen.getByTestId("save-search-button")).toBeInTheDocument();
  });

  it("does not show save button when no filters active", () => {
    renderPanel({ filters: {} });
    expect(screen.queryByTestId("save-search-button")).not.toBeInTheDocument();
  });

  it("saves a search", async () => {
    const user = userEvent.setup();
    const onSaveSearch = vi.fn();
    renderPanel({
      onSaveSearch,
      filters: { consoles: ["SNES"], genres: ["RPG"] },
    });
    await user.click(screen.getByTestId("save-search-button"));
    const nameInput = screen.getByTestId("save-search-name");
    await user.type(nameInput, "My Filter");
    await user.click(screen.getByText("Save"));
    expect(onSaveSearch).toHaveBeenCalledWith("My Filter", {
      consoles: "SNES",
      genres: "RPG",
    });
  });

  it("shows empty state when no saved searches", () => {
    renderPanel({ savedSearches: [] });
    expect(screen.getByText(/No saved searches yet/)).toBeInTheDocument();
  });
});

describe("savedSearchToFilters", () => {
  it("converts a saved search record to GameFilters", () => {
    const base: GameFilters = { sortBy: "title", sortOrder: "asc", pageSize: 48 };
    const result = savedSearchToFilters(
      { consoles: "SNES,NES", genres: "RPG", ratingMin: 80, developer: "Square" },
      base,
    );
    expect(result.consoles).toEqual(["SNES", "NES"]);
    expect(result.genres).toEqual(["RPG"]);
    expect(result.ratingMin).toBe(80);
    expect(result.developer).toBe("Square");
    expect(result.sortBy).toBe("title");
    expect(result.page).toBe(1);
  });

  it("preserves search from record if present", () => {
    const result = savedSearchToFilters({ search: "mario" }, { sortBy: "title" });
    expect(result.search).toBe("mario");
  });
});
