import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { CompletionistMapSection } from "@/features/explore/components/completionist-map";
import type { CompletionistMapResponse } from "@/types/api";

const mockData: CompletionistMapResponse = {
  consoles: [
    { id: "snes", name: "Super Nintendo", totalGames: 100, playedGames: 50, percentage: 50 },
    { id: "nes", name: "Nintendo", totalGames: 80, playedGames: 80, percentage: 100 },
    { id: "gba", name: "Game Boy Advance", totalGames: 60, playedGames: 10, percentage: 17 },
  ],
  totalGames: 240,
  totalPlayed: 140,
  overallPct: 58,
};

describe("CompletionistMapSection", () => {
  it("renders overall progress", () => {
    render(<CompletionistMapSection data={mockData} isLoading={false} />);
    expect(screen.getByText("Overall Progress")).toBeInTheDocument();
    expect(screen.getByText("58%")).toBeInTheDocument();
    expect(screen.getByText("/ 240 games played")).toBeInTheDocument();
  });

  it("renders per-console progress", () => {
    render(<CompletionistMapSection data={mockData} isLoading={false} />);
    expect(screen.getByText("Super Nintendo")).toBeInTheDocument();
    expect(screen.getByText("Nintendo")).toBeInTheDocument();
    expect(screen.getByText("Game Boy Advance")).toBeInTheDocument();
    expect(screen.getByText("50/100")).toBeInTheDocument();
    expect(screen.getByText("80/80")).toBeInTheDocument();
    expect(screen.getByText("10/60")).toBeInTheDocument();
  });

  it("renders skeleton when loading", () => {
    render(<CompletionistMapSection data={undefined} isLoading={true} />);
    expect(
      screen.getByTestId("completionist-map-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when data has no consoles", () => {
    const empty: CompletionistMapResponse = {
      consoles: [],
      totalGames: 0,
      totalPlayed: 0,
      overallPct: 0,
    };
    const { container } = render(
      <CompletionistMapSection data={empty} isLoading={false} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders test id for each console row", () => {
    render(<CompletionistMapSection data={mockData} isLoading={false} />);
    expect(screen.getByTestId("console-progress-snes")).toBeInTheDocument();
    expect(screen.getByTestId("console-progress-nes")).toBeInTheDocument();
    expect(screen.getByTestId("console-progress-gba")).toBeInTheDocument();
  });

  it("shows percentage per console", () => {
    render(<CompletionistMapSection data={mockData} isLoading={false} />);
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByText("100%")).toBeInTheDocument();
    expect(screen.getByText("17%")).toBeInTheDocument();
  });
});
