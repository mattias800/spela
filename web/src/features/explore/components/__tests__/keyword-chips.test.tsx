import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { KeywordChips } from "../keyword-chips";
import type { Keyword } from "@/types/api";

function makeKeyword(overrides: Partial<Keyword> = {}): Keyword {
  return {
    id: "100",
    name: "Time Travel",
    gameCount: 15,
    ...overrides,
  };
}

const mockKeywords: Keyword[] = [
  makeKeyword({ id: "100", name: "Time Travel", gameCount: 15 }),
  makeKeyword({ id: "101", name: "Post-Apocalyptic", gameCount: 12 }),
  makeKeyword({ id: "102", name: "Steampunk", gameCount: 8 }),
];

function renderChips(
  props: { keywords?: Keyword[] | undefined; isLoading?: boolean } = {},
) {
  const keywords = "keywords" in props ? props.keywords : mockKeywords;
  return render(
    <MemoryRouter>
      <KeywordChips keywords={keywords} isLoading={props.isLoading ?? false} />
    </MemoryRouter>,
  );
}

describe("KeywordChips", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders section with heading", () => {
    renderChips();
    expect(
      screen.getByRole("heading", { name: "Popular Keywords", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders keyword chips", () => {
    renderChips();
    expect(screen.getByText("Time Travel")).toBeInTheDocument();
    expect(screen.getByText("Post-Apocalyptic")).toBeInTheDocument();
    expect(screen.getByText("Steampunk")).toBeInTheDocument();
  });

  it("shows game counts on chips", () => {
    renderChips();
    expect(screen.getByText("15")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
  });

  it("renders chips as list items", () => {
    renderChips();
    const listItems = screen.getAllByRole("listitem");
    expect(listItems).toHaveLength(3);
  });

  it("renders the scrollable list with label", () => {
    renderChips();
    expect(
      screen.getByRole("list", { name: "Popular Keywords" }),
    ).toBeInTheDocument();
  });

  it("links keyword chips to keyword detail pages", () => {
    renderChips();
    const listItems = screen.getAllByRole("listitem");
    const hrefs = listItems.map((l) => l.getAttribute("href"));
    expect(hrefs).toContain("/explore/keywords/100");
    expect(hrefs).toContain("/explore/keywords/101");
    expect(hrefs).toContain("/explore/keywords/102");
  });

  it("renders nothing when keywords array is empty", () => {
    const { container } = renderChips({ keywords: [] });
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when keywords is undefined and not loading", () => {
    const { container } = renderChips({
      keywords: undefined,
      isLoading: false,
    });
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when loading", () => {
    renderChips({ isLoading: true, keywords: undefined });
    expect(
      screen.getByTestId("keyword-chips-skeleton"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Popular Keywords", level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderChips({
      isLoading: true,
      keywords: undefined,
    });
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
});
