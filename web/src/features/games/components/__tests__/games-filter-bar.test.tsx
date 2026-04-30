import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { GamesFilterBar } from "../games-filter-bar";
import type { Console } from "@/types/api";

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
  makeConsole("snes", "Super Nintendo"),
  makeConsole("nes", "Nintendo"),
];

function renderBar(overrides: Partial<React.ComponentProps<typeof GamesFilterBar>> = {}) {
  const defaultProps: React.ComponentProps<typeof GamesFilterBar> = {
    filters: {},
    onFiltersChange: vi.fn(),
    searchValue: "",
    onSearchChange: vi.fn(),
    viewMode: "grid" as const,
    onViewModeChange: vi.fn(),
    consoles: testConsoles,
    ...overrides,
  };
  return render(<GamesFilterBar {...defaultProps} />);
}

describe("GamesFilterBar", () => {
  it("renders console select by default", () => {
    renderBar();
    // The "All Consoles" option should be present in the select
    expect(screen.getByDisplayValue("All Consoles")).toBeInTheDocument();
  });

  it("hides console select when hideConsoleFilter is true", () => {
    renderBar({ hideConsoleFilter: true });
    expect(screen.queryByDisplayValue("All Consoles")).not.toBeInTheDocument();
  });

  it("shows console select when hideConsoleFilter is false", () => {
    renderBar({ hideConsoleFilter: false });
    expect(screen.getByDisplayValue("All Consoles")).toBeInTheDocument();
  });

  it("renders search input", () => {
    renderBar();
    expect(screen.getByPlaceholderText("Search games...")).toBeInTheDocument();
  });

  it("renders hide betas toggle when showHideBetas is true", () => {
    renderBar({ showHideBetas: true });
    expect(screen.getByTestId("hide-betas-toggle")).toBeInTheDocument();
  });

  it("does not render hide betas toggle by default", () => {
    renderBar();
    expect(screen.queryByTestId("hide-betas-toggle")).not.toBeInTheDocument();
  });
});
