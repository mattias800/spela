import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ReleaseTimeline } from "@/features/explore/components/release-timeline";
import type { TimelineEntry } from "@/types/api";

const mockTimeline: TimelineEntry[] = [
  {
    year: 1992,
    games: [
      { id: "g1", title: "Mega Man X", coverUrl: "/covers/mmx.jpg", igdbCriticsRating: 95 },
      { id: "g2", title: "Street Fighter II", coverUrl: "/covers/sf2.jpg", igdbCriticsRating: 90 },
    ],
  },
  {
    year: 1990,
    games: [
      { id: "g3", title: "Final Fight", coverUrl: "/covers/ff.jpg", igdbCriticsRating: 80 },
    ],
  },
  {
    year: 1995,
    games: [
      { id: "g4", title: "Mega Man X3", coverUrl: "/covers/mmx3.jpg", igdbCriticsRating: 0 },
    ],
  },
];

function renderTimeline(timeline: TimelineEntry[] = mockTimeline) {
  return render(
    <MemoryRouter>
      <ReleaseTimeline timeline={timeline} />
    </MemoryRouter>,
  );
}

describe("ReleaseTimeline", () => {
  it("renders the section with test id", () => {
    renderTimeline();
    expect(screen.getByTestId("release-timeline")).toBeInTheDocument();
  });

  it("renders the heading", () => {
    renderTimeline();
    expect(
      screen.getByRole("heading", { name: "Release Timeline" }),
    ).toBeInTheDocument();
  });

  it("renders year columns sorted ascending", () => {
    renderTimeline();
    expect(screen.getByTestId("timeline-year-1990")).toBeInTheDocument();
    expect(screen.getByTestId("timeline-year-1992")).toBeInTheDocument();
    expect(screen.getByTestId("timeline-year-1995")).toBeInTheDocument();

    // Check order: 1990 should come before 1992
    const strip = screen.getByTestId("timeline-strip");
    const years = strip.querySelectorAll("[data-testid^='timeline-year-']");
    expect(years[0]).toHaveAttribute("data-testid", "timeline-year-1990");
    expect(years[1]).toHaveAttribute("data-testid", "timeline-year-1992");
    expect(years[2]).toHaveAttribute("data-testid", "timeline-year-1995");
  });

  it("renders year labels", () => {
    renderTimeline();
    expect(screen.getByText("1990")).toBeInTheDocument();
    expect(screen.getByText("1992")).toBeInTheDocument();
    expect(screen.getByText("1995")).toBeInTheDocument();
  });

  it("renders game cover images", () => {
    renderTimeline();
    expect(screen.getByTestId("timeline-game-g1")).toBeInTheDocument();
    expect(screen.getByTestId("timeline-game-g2")).toBeInTheDocument();
    expect(screen.getByTestId("timeline-game-g3")).toBeInTheDocument();
    expect(screen.getByTestId("timeline-game-g4")).toBeInTheDocument();
  });

  it("shows tooltip on hover with title and rating", () => {
    renderTimeline();
    const cover = screen.getByTestId("timeline-game-g1");
    // Tooltip should not be visible initially
    expect(
      screen.queryByTestId("timeline-tooltip-g1"),
    ).not.toBeInTheDocument();

    // Hover over the cover's parent (the relative div)
    fireEvent.mouseEnter(cover.closest("[class*='relative']")!);
    const tooltip = screen.getByTestId("timeline-tooltip-g1");
    expect(tooltip).toHaveTextContent("Mega Man X");
    expect(tooltip).toHaveTextContent("95.0");
  });

  it("hides tooltip on mouse leave", () => {
    renderTimeline();
    const cover = screen.getByTestId("timeline-game-g1");
    const parent = cover.closest("[class*='relative']")!;

    fireEvent.mouseEnter(parent);
    expect(screen.getByTestId("timeline-tooltip-g1")).toBeInTheDocument();

    fireEvent.mouseLeave(parent);
    expect(
      screen.queryByTestId("timeline-tooltip-g1"),
    ).not.toBeInTheDocument();
  });

  it("does not show rating in tooltip when rating is 0", () => {
    renderTimeline();
    const cover = screen.getByTestId("timeline-game-g4");
    const parent = cover.closest("[class*='relative']")!;

    fireEvent.mouseEnter(parent);
    const tooltip = screen.getByTestId("timeline-tooltip-g4");
    expect(tooltip).toHaveTextContent("Mega Man X3");
    // Should not contain a rating value
    expect(tooltip.querySelectorAll("p")).toHaveLength(1);
  });

  it("renders nothing when timeline is empty", () => {
    const { container } = renderTimeline([]);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when timeline is undefined", () => {
    const { container } = render(
      <MemoryRouter>
        <ReleaseTimeline timeline={undefined as unknown as TimelineEntry[]} />
      </MemoryRouter>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders game covers as links to game detail pages", () => {
    renderTimeline();
    const cover = screen.getByTestId("timeline-game-g1");
    const link = cover.closest("a");
    expect(link).toHaveAttribute("href", "/games/g1");
  });
});
