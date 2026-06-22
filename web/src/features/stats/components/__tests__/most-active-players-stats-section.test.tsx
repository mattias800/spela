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

import { useMostActivePlayers } from "@/hooks/use-stats";
import { useFederationAggregatedStats } from "@/hooks/use-federation-stats";
import { MostActivePlayersStatsSection } from "../most-active-players-stats-section";

const mockLocal = useMostActivePlayers as ReturnType<typeof vi.fn>;
const mockMesh = useFederationAggregatedStats as ReturnType<typeof vi.fn>;

function renderSection() {
  return render(
    <MemoryRouter>
      <MostActivePlayersStatsSection />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockLocal.mockReset();
  mockMesh.mockReset();
  mockMesh.mockReturnValue({ data: [], isLoading: false });
});

describe("MostActivePlayersStatsSection", () => {
  it("shows local players on 'this server'", () => {
    mockLocal.mockReturnValue({
      data: {
        players: [
          {
            userId: "u1",
            username: "alice",
            avatarUrl: "",
            totalPlayTime: 3600,
            gamesPlayed: 4,
            lastPlayed: "2026-06-22T10:00:00Z",
          },
        ],
      },
      isLoading: false,
    });
    renderSection();
    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.queryByTestId("mesh-stat-row")).not.toBeInTheDocument();
  });

  it("switches to mesh rows (no player count column) when toggled to 'across servers'", () => {
    mockLocal.mockReturnValue({ data: { players: [] }, isLoading: false });
    mockMesh.mockReturnValue({
      data: [
        {
          key: "bob",
          label: "bob",
          metric: "player_play",
          totalPlayTimeSeconds: 7200,
          totalPlayers: 0,
          sources: [],
        },
      ],
      isLoading: false,
    });
    renderSection();

    fireEvent.click(screen.getByText("Across servers"));

    const rows = screen.getAllByTestId("mesh-stat-row");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent("bob");
    // player_play rows don't render a "players" count
    expect(rows[0]).not.toHaveTextContent("players");
  });

  it("shows the local empty state on 'this server' when no players", () => {
    mockLocal.mockReturnValue({ data: { players: [] }, isLoading: false });
    renderSection();
    expect(screen.getByText("No player activity yet")).toBeInTheDocument();
  });
});
