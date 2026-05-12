import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ConsoleQuickJump } from "@/features/explore/components/console-quick-jump";
import type { ConsoleHighlight } from "@/types/api";
import { makeGame } from "@/test-utils/fixtures";


function makeConsoleHighlight(
  overrides: Partial<ConsoleHighlight> = {},
): ConsoleHighlight {
  return {
    id: "snes",
    name: "Super Nintendo",
    colorTheme: "#805ad5",
    iconUrl: "/icons/snes.png",
    logoUrl: "/logos/snes.png",
    logoAspectRatio: null,
    gameCount: 42,
    topGame: makeGame({ id: "top1", title: "Top SNES Game" }),
    ...overrides,
  };
}

const mockConsoles: ConsoleHighlight[] = [
  makeConsoleHighlight({
    id: "snes",
    name: "Super Nintendo",
    gameCount: 42,
    colorTheme: "#805ad5",
  }),
  makeConsoleHighlight({
    id: "nes",
    name: "NES",
    gameCount: 30,
    colorTheme: "#e53e3e",
  }),
  makeConsoleHighlight({
    id: "gba",
    name: "Game Boy Advance",
    gameCount: 25,
    colorTheme: "#3182ce",
  }),
];

function renderComponent(
  consoles: ConsoleHighlight[] | undefined = mockConsoles,
  isLoading = false,
) {
  return render(
    <MemoryRouter>
      <ConsoleQuickJump consoles={consoles} isLoading={isLoading} />
    </MemoryRouter>,
  );
}

describe("ConsoleQuickJump", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the section with test id", () => {
    renderComponent();
    expect(screen.getByTestId("console-quick-jump")).toBeInTheDocument();
  });

  it("renders the section heading", () => {
    renderComponent();
    expect(
      screen.getByRole("heading", { name: "Browse by Console", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders console cards", () => {
    renderComponent();
    expect(screen.getByTestId("console-card-snes")).toBeInTheDocument();
    expect(screen.getByTestId("console-card-nes")).toBeInTheDocument();
    expect(screen.getByTestId("console-card-gba")).toBeInTheDocument();
  });

  it("renders console names", () => {
    renderComponent();
    expect(screen.getByText("Super Nintendo")).toBeInTheDocument();
    expect(screen.getByText("NES")).toBeInTheDocument();
    expect(screen.getByText("Game Boy Advance")).toBeInTheDocument();
  });

  it("shows game counts", () => {
    renderComponent();
    expect(screen.getByText("42 games")).toBeInTheDocument();
    expect(screen.getByText("30 games")).toBeInTheDocument();
    expect(screen.getByText("25 games")).toBeInTheDocument();
  });

  it("each card links to console detail page", () => {
    renderComponent();
    const snesCard = screen.getByTestId("console-card-snes");
    expect(snesCard.tagName).toBe("A");
    expect(snesCard).toHaveAttribute("href", "/consoles/snes");

    const nesCard = screen.getByTestId("console-card-nes");
    expect(nesCard).toHaveAttribute("href", "/consoles/nes");

    const gbaCard = screen.getByTestId("console-card-gba");
    expect(gbaCard).toHaveAttribute("href", "/consoles/gba");
  });

  it("renders loading skeleton when loading", () => {
    renderComponent(undefined, true);
    expect(
      screen.getByTestId("console-quick-jump-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when consoles array is empty", () => {
    const { container } = renderComponent([]);
    expect(
      screen.queryByTestId("console-quick-jump"),
    ).not.toBeInTheDocument();
    expect(container.innerHTML).toBe("");
  });

  it("returns null when consoles is undefined and not loading", () => {
    const { container } = render(
      <MemoryRouter>
        <ConsoleQuickJump consoles={undefined} isLoading={false} />
      </MemoryRouter>,
    );
    expect(
      screen.queryByTestId("console-quick-jump"),
    ).not.toBeInTheDocument();
    expect(container.innerHTML).toBe("");
  });

  it("renders singular game count correctly", () => {
    renderComponent([
      makeConsoleHighlight({ id: "vb", name: "Virtual Boy", gameCount: 1 }),
    ]);
    expect(screen.getByText("1 game")).toBeInTheDocument();
  });

  it("renders list items with correct role", () => {
    renderComponent();
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(3);
  });

  // #1175 — the "See all consoles" affordance is the section's only
  // exit ramp to the full Consoles directory; without it users assume
  // the carousel is the complete inventory.
  it("renders See-all-consoles link pointing at /consoles", () => {
    renderComponent();
    const link = screen.getByTestId("console-quick-jump-see-all");
    expect(link).toBeInTheDocument();
    expect(link.tagName).toBe("A");
    expect(link).toHaveAttribute("href", "/consoles");
  });

  // The link must also render in the loading skeleton so it doesn't
  // pop in when data arrives — the section header layout is the same
  // in both states.
  it("renders See-all-consoles link in the loading skeleton too", () => {
    renderComponent(undefined, true);
    const link = screen.getByTestId("console-quick-jump-see-all");
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute("href", "/consoles");
  });
});
