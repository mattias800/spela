import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ForYouSection } from "../for-you-section";
import type { ForYouRow } from "@/types/api";
import { makeGame } from "@/test-utils/fixtures";

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));


function renderComponent(props: {
  rows?: ForYouRow[];
  isLoading?: boolean;
}) {
  return render(
    <MemoryRouter>
      <ForYouSection
        rows={props.rows}
        isLoading={props.isLoading ?? false}
      />
    </MemoryRouter>,
  );
}

describe("ForYouSection", () => {
  it("renders 'Because you played' row with source game reference", () => {
    const rows: ForYouRow[] = [
      {
        type: "because_you_played",
        title: "Because you played Chrono Trigger",
        genre: "",
        sourceGame: makeGame({
          id: "source-1",
          title: "Chrono Trigger",
          coverUrl: "/covers/chrono.jpg",
        }),
        games: [
          makeGame({ id: "rec-1", title: "Final Fantasy VI" }),
          makeGame({ id: "rec-2", title: "Secret of Mana" }),
        ],
      },
    ];

    renderComponent({ rows });

    expect(screen.getByTestId("for-you-section")).toBeInTheDocument();
    expect(screen.getByTestId("for-you-row-because_you_played")).toBeInTheDocument();
    expect(screen.getByText("Because you played Chrono Trigger")).toBeInTheDocument();
    expect(screen.getByTestId("source-game-cover")).toBeInTheDocument();
    expect(screen.getByTestId("source-game-cover")).toHaveAttribute("src", "/covers/chrono.jpg");
    expect(screen.getByText("Final Fantasy VI")).toBeInTheDocument();
    expect(screen.getByText("Secret of Mana")).toBeInTheDocument();
  });

  it("renders 'More Genre' row", () => {
    const rows: ForYouRow[] = [
      {
        type: "more_genre",
        title: "More RPGs for you",
        genre: "",
        sourceGame: makeGame(),
        games: [
          makeGame({ id: "g1", title: "Dragon Quest V" }),
          makeGame({ id: "g2", title: "Earthbound" }),
        ],
      },
    ];

    renderComponent({ rows });

    expect(screen.getByTestId("for-you-row-more_genre")).toBeInTheDocument();
    expect(screen.getByText("More RPGs for you")).toBeInTheDocument();
    expect(screen.getByText("Dragon Quest V")).toBeInTheDocument();
    expect(screen.getByText("Earthbound")).toBeInTheDocument();
  });

  it("renders 'Unfinished business' row", () => {
    const rows: ForYouRow[] = [
      {
        type: "unfinished",
        title: "Unfinished business",
        genre: "",
        sourceGame: makeGame(),
        games: [
          makeGame({ id: "u1", title: "Mega Man X" }),
        ],
      },
    ];

    renderComponent({ rows });

    expect(screen.getByTestId("for-you-row-unfinished")).toBeInTheDocument();
    expect(screen.getByText("Unfinished business")).toBeInTheDocument();
    expect(screen.getByText("Mega Man X")).toBeInTheDocument();
  });

  it("renders 'Expand horizons' row with genre callout", () => {
    const rows: ForYouRow[] = [
      {
        type: "expand_horizons",
        title: "Expand your horizons",
        genre: "Puzzle",
        sourceGame: makeGame(),
        games: [
          makeGame({ id: "e1", title: "Tetris Attack" }),
          makeGame({ id: "e2", title: "Panel de Pon" }),
        ],
      },
    ];

    renderComponent({ rows });

    expect(screen.getByTestId("for-you-row-expand_horizons")).toBeInTheDocument();
    expect(screen.getByText("Expand your horizons")).toBeInTheDocument();
    expect(screen.getByText("Puzzle")).toBeInTheDocument();
    expect(screen.getByText("Tetris Attack")).toBeInTheDocument();
    expect(screen.getByText("Panel de Pon")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderComponent({ isLoading: true });

    expect(screen.getByTestId("for-you-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("for-you-section")).not.toBeInTheDocument();
  });

  it("returns null when no rows", () => {
    const { container } = renderComponent({ rows: undefined });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when rows is empty array", () => {
    const { container } = renderComponent({ rows: [] });
    expect(container.innerHTML).toBe("");
  });
});
