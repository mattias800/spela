import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/hooks/use-federation-achievements", () => ({
  useFederationAchievements: vi.fn(),
}));

import { useFederationAchievements } from "@/hooks/use-federation-achievements";
import { TopAchieversSection } from "../top-achievers-section";

const mockAchievements = useFederationAchievements as ReturnType<typeof vi.fn>;

function entry(over: Partial<Record<string, unknown>> = {}) {
  return {
    originFingerprint: "",
    hops: 1,
    username: "alice",
    count: 120,
    serverName: "Server B",
    ...over,
  };
}

beforeEach(() => {
  mockAchievements.mockReset();
});

describe("TopAchieversSection", () => {
  it("shows the skeleton (no rows) while loading", () => {
    mockAchievements.mockReturnValue({ data: undefined, isLoading: true });
    render(<TopAchieversSection />);
    expect(screen.queryByTestId("top-achievers-row")).not.toBeInTheDocument();
  });

  it("shows all players (local + remote) on the default 'across' scope", () => {
    mockAchievements.mockReturnValue({
      data: [
        entry({ username: "alice", count: 120, serverName: "Server B", hops: 1 }),
        entry({ username: "you", count: 80, serverName: "", hops: 0 }),
      ],
      isLoading: false,
    });
    render(<TopAchieversSection />);
    const rows = screen.getAllByTestId("top-achievers-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("alice");
    expect(rows[0]).toHaveTextContent("120 achievements");
    expect(rows[0]).toHaveTextContent("Server B");
    expect(rows[1]).toHaveTextContent("you");
  });

  it("filters to local players (hop 0) on the 'This server' scope", () => {
    mockAchievements.mockReturnValue({
      data: [
        entry({ username: "remotebob", hops: 1, serverName: "Server B" }),
        entry({ username: "localyou", hops: 0, serverName: "", count: 80 }),
      ],
      isLoading: false,
    });
    render(<TopAchieversSection />);
    fireEvent.click(screen.getByText("This server"));

    const rows = screen.getAllByTestId("top-achievers-row");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent("localyou");
    expect(screen.queryByText("remotebob")).not.toBeInTheDocument();
  });

  it("shows an empty state when no one is on the leaderboard", () => {
    mockAchievements.mockReturnValue({ data: [], isLoading: false });
    render(<TopAchieversSection />);
    expect(
      screen.getByText("Nothing across connected servers yet"),
    ).toBeInTheDocument();
  });

  it("uses singular 'achievement' for a count of 1", () => {
    mockAchievements.mockReturnValue({
      data: [entry({ count: 1 })],
      isLoading: false,
    });
    render(<TopAchieversSection />);
    expect(screen.getByTestId("top-achievers-row")).toHaveTextContent(
      "1 achievement",
    );
  });
});
