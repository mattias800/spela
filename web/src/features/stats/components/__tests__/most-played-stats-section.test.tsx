import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";

vi.mock("@/hooks/use-stats", () => ({
  useMostPlayedGames: vi.fn(),
  useMostActivePlayers: vi.fn(),
}));
vi.mock("@/hooks/use-federation-stats", () => ({
  useFederationAggregatedStats: vi.fn(),
}));

import { useMostPlayedGames } from "@/hooks/use-stats";
import { useFederationAggregatedStats } from "@/hooks/use-federation-stats";
import { MostPlayedStatsSection } from "../most-played-stats-section";

const mockLocal = useMostPlayedGames as ReturnType<typeof vi.fn>;
const mockMesh = useFederationAggregatedStats as ReturnType<typeof vi.fn>;

function renderSection() {
  return render(
    <MemoryRouter>
      <MostPlayedStatsSection />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockLocal.mockReset();
  mockMesh.mockReset();
  mockMesh.mockReturnValue({ data: [], isLoading: false });
});

describe("MostPlayedStatsSection", () => {
  it("shows local games on 'this server' and links to the game", () => {
    mockLocal.mockReturnValue({
      data: {
        games: [
          {
            game: { id: "1", title: "Chrono Trigger", coverUrl: "", consoleName: "SNES" },
            totalPlayTime: 3600,
            totalPlayers: 5,
          },
        ],
      },
      isLoading: false,
    });
    renderSection();
    const link = screen.getByRole("link", { name: /Chrono Trigger/ });
    expect(link).toHaveAttribute("href", "/games/1");
    expect(screen.getByText("SNES")).toBeInTheDocument();
    expect(screen.queryByTestId("mesh-stat-row")).not.toBeInTheDocument();
  });

  it("switches to mesh rows when toggled to 'across servers'", () => {
    mockLocal.mockReturnValue({ data: { games: [] }, isLoading: false });
    mockMesh.mockReturnValue({
      data: [
        {
          key: "igdb:1022",
          label: "Chrono Trigger",
          metric: "game_play",
          totalPlayTimeSeconds: 7200,
          totalPlayers: 9,
          sources: [],
        },
      ],
      isLoading: false,
    });
    renderSection();

    fireEvent.click(screen.getByText("Across servers"));

    const rows = screen.getAllByTestId("mesh-stat-row");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent("Chrono Trigger");
    expect(rows[0]).toHaveTextContent("9 players");
  });

  it("shows an empty state when nobody across the mesh has played", () => {
    mockLocal.mockReturnValue({ data: { games: [] }, isLoading: false });
    mockMesh.mockReturnValue({ data: [], isLoading: false });
    renderSection();
    fireEvent.click(screen.getByText("Across servers"));
    expect(
      screen.getByText("Nothing across connected servers yet"),
    ).toBeInTheDocument();
  });

  it("shows the local empty state on 'this server' when no games", () => {
    mockLocal.mockReturnValue({ data: { games: [] }, isLoading: false });
    renderSection();
    expect(screen.getByText("No games played yet")).toBeInTheDocument();
  });
});
