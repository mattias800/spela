import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { makeGame } from "@/test-utils/fixtures";
import {
  OnThisDayShelf,
  BestOfYearSection,
  AnniversariesShelf,
  DecadeSpotlight,
} from "../temporal-shelves";
import type {
  OnThisDayResponse,
  BestOfYearResponse,
  AnniversaryItem,
  DecadeResponse,
} from "@/types/api";

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));


// --- On This Day Shelf ---

describe("OnThisDayShelf", () => {
  function renderShelf(props: {
    data?: OnThisDayResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <OnThisDayShelf
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders games with release years", () => {
    const data: OnThisDayResponse = {
      date: "March 10",
      games: [
        makeGame({
          id: "otd1",
          title: "Chrono Trigger",
          releaseDate: "1995-03-11",
        }),
        makeGame({
          id: "otd2",
          title: "Super Metroid",
          releaseDate: "1994-03-19",
        }),
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("on-this-day-shelf")).toBeInTheDocument();
    expect(
      screen.getByText("On This Day in Gaming — March 10"),
    ).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
    expect(screen.getByText("Super Metroid")).toBeInTheDocument();
    const years = screen.getAllByTestId("release-year");
    expect(years[0]).toHaveTextContent("Released 1995");
    expect(years[1]).toHaveTextContent("Released 1994");
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("on-this-day-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({
      data: { date: "March 10", games: [] },
    });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Best of Year Section ---

describe("BestOfYearSection", () => {
  function renderSection(props: {
    year?: number;
    onYearChange?: (year: number) => void;
    data?: BestOfYearResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <BestOfYearSection
          year={props.year ?? 1995}
          onYearChange={props.onYearChange ?? vi.fn()}
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders games in a grid with year selector", () => {
    const data: BestOfYearResponse = {
      year: 1995,
      games: [
        makeGame({ id: "boy1", title: "Donkey Kong Country 2" }),
        makeGame({ id: "boy2", title: "Earthbound" }),
      ],
    };
    renderSection({ data, year: 1995 });

    expect(screen.getByTestId("best-of-year-section")).toBeInTheDocument();
    expect(screen.getByText("Best of 1995")).toBeInTheDocument();
    expect(screen.getByText("Donkey Kong Country 2")).toBeInTheDocument();
    expect(screen.getByText("Earthbound")).toBeInTheDocument();
    expect(screen.getByTestId("year-selector")).toBeInTheDocument();
  });

  it("calls onYearChange when a year button is clicked", () => {
    const onYearChange = vi.fn();
    const data: BestOfYearResponse = {
      year: 1995,
      games: [makeGame({ id: "boy1", title: "Game" })],
    };
    renderSection({ data, year: 1995, onYearChange });

    fireEvent.click(screen.getByText("2000"));
    expect(onYearChange).toHaveBeenCalledWith(2000);
  });

  it("highlights the selected year button", () => {
    const data: BestOfYearResponse = {
      year: 1995,
      games: [makeGame({ id: "boy1", title: "Game" })],
    };
    renderSection({ data, year: 1995 });

    const selectedBtn = screen.getByText("1995");
    expect(selectedBtn).toHaveAttribute("aria-pressed", "true");

    const otherBtn = screen.getByText("2000");
    expect(otherBtn).toHaveAttribute("aria-pressed", "false");
  });

  it("shows skeleton when loading", () => {
    renderSection({ isLoading: true });
    expect(
      screen.getByTestId("best-of-year-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderSection({
      data: { year: 1995, games: [] },
    });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderSection({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Anniversaries Shelf ---

describe("AnniversariesShelf", () => {
  function renderShelf(props: {
    anniversaries?: AnniversaryItem[];
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <AnniversariesShelf
          anniversaries={props.anniversaries}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders anniversaries with year labels", () => {
    const anniversaries: AnniversaryItem[] = [
      {
        game: makeGame({ id: "ann1", title: "Final Fantasy VI" }),
        yearsAgo: 3,
        playedAt: "2023-03-10T12:00:00Z",
      },
      {
        game: makeGame({ id: "ann2", title: "Mega Man X" }),
        yearsAgo: 1,
        playedAt: "2025-03-10T15:00:00Z",
      },
    ];
    renderShelf({ anniversaries });

    expect(screen.getByTestId("anniversaries-shelf")).toBeInTheDocument();
    expect(
      screen.getByText("Your Gaming Anniversaries"),
    ).toBeInTheDocument();
    expect(screen.getByText("Final Fantasy VI")).toBeInTheDocument();
    expect(screen.getByText("Mega Man X")).toBeInTheDocument();
    const labels = screen.getAllByTestId("anniversary-label");
    expect(labels[0]).toHaveTextContent("3 years ago you played this");
    expect(labels[1]).toHaveTextContent("1 year ago you played this");
  });

  it("uses singular for 1 year", () => {
    const anniversaries: AnniversaryItem[] = [
      {
        game: makeGame({ id: "ann1", title: "Solo" }),
        yearsAgo: 1,
        playedAt: "2025-03-10T12:00:00Z",
      },
    ];
    renderShelf({ anniversaries });

    expect(screen.getByTestId("anniversary-label")).toHaveTextContent(
      "1 year ago you played this",
    );
  });

  it("uses plural for multiple years", () => {
    const anniversaries: AnniversaryItem[] = [
      {
        game: makeGame({ id: "ann1", title: "Multi" }),
        yearsAgo: 5,
        playedAt: "2021-03-10T12:00:00Z",
      },
    ];
    renderShelf({ anniversaries });

    expect(screen.getByTestId("anniversary-label")).toHaveTextContent(
      "5 years ago you played this",
    );
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("anniversaries-shelf-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({ anniversaries: [] });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});

// --- Decade Spotlight ---

describe("DecadeSpotlight", () => {
  function renderShelf(props: {
    data?: DecadeResponse;
    isLoading?: boolean;
  }) {
    return render(
      <MemoryRouter>
        <DecadeSpotlight
          data={props.data}
          isLoading={props.isLoading ?? false}
        />
      </MemoryRouter>,
    );
  }

  it("renders decade games with label", () => {
    const data: DecadeResponse = {
      decade: "90s",
      label: "The 90s",
      games: [
        makeGame({ id: "d1", title: "Street Fighter II" }),
        makeGame({ id: "d2", title: "Sonic the Hedgehog" }),
      ],
    };
    renderShelf({ data });

    expect(screen.getByTestId("decade-spotlight")).toBeInTheDocument();
    expect(
      screen.getByText("The 90s"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("The defining games of the 90s"),
    ).toBeInTheDocument();
    expect(screen.getByText("Street Fighter II")).toBeInTheDocument();
    expect(screen.getByText("Sonic the Hedgehog")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    renderShelf({ isLoading: true });
    expect(
      screen.getByTestId("decade-spotlight-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when empty", () => {
    const { container } = renderShelf({
      data: { decade: "90s", label: "The 90s", games: [] },
    });
    expect(container.innerHTML).toBe("");
  });

  it("returns null when data is undefined", () => {
    const { container } = renderShelf({});
    expect(container.innerHTML).toBe("");
  });
});
