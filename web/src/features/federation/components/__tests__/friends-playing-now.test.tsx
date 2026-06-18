import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";

vi.mock("@/hooks/use-federation-presence", () => ({
  useFederationPresence: vi.fn(),
}));

import { useFederationPresence } from "@/hooks/use-federation-presence";
import { FriendsPlayingNow } from "../friends-playing-now";

const mockPresence = useFederationPresence as ReturnType<typeof vi.fn>;

function entry(over: Partial<Record<string, unknown>> = {}) {
  return {
    originFingerprint: "",
    hops: 1,
    username: "bob",
    gameKey: "igdb:99",
    gameTitle: "Chrono Trigger",
    serverName: "Server B",
    ...over,
  };
}

function renderWidget() {
  return render(
    <MemoryRouter>
      <FriendsPlayingNow />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockPresence.mockReset();
});

describe("FriendsPlayingNow", () => {
  it("shows the skeleton (no rows) while loading", () => {
    mockPresence.mockReturnValue({ data: undefined, isLoading: true });
    renderWidget();
    expect(
      screen.getByTestId("friends-playing-now-skeleton"),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("friends-playing-now-row")).not.toBeInTheDocument();
  });

  it("shows the empty state when no one is playing on a connected server", () => {
    mockPresence.mockReturnValue({ data: [], isLoading: false });
    renderWidget();
    expect(screen.getByText("No one playing right now")).toBeInTheDocument();
  });

  it("renders a row per remote player, linking to the remote game", () => {
    mockPresence.mockReturnValue({
      data: [
        entry(),
        entry({ username: "carol", gameKey: "igdb:7", gameTitle: "Zelda", serverName: "RetroPals" }),
      ],
      isLoading: false,
    });
    renderWidget();

    const rows = screen.getAllByTestId("friends-playing-now-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveAttribute("href", "/remote-games/igdb%3A99");
    expect(rows[0]).toHaveTextContent("bob");
    expect(rows[0]).toHaveTextContent("Chrono Trigger");
    expect(rows[0]).toHaveTextContent("Server B");
    expect(rows[1]).toHaveTextContent("RetroPals");
  });

  it("filters out local players (hop 0) — this section is across connected servers", () => {
    mockPresence.mockReturnValue({
      data: [
        entry({ username: "localguy", hops: 0, serverName: "" }),
        entry({ username: "remotebob", hops: 1 }),
      ],
      isLoading: false,
    });
    renderWidget();

    const rows = screen.getAllByTestId("friends-playing-now-row");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent("remotebob");
    expect(screen.queryByText("localguy")).not.toBeInTheDocument();
  });

  it("falls back to a generic label when the server name is blank", () => {
    mockPresence.mockReturnValue({
      data: [entry({ serverName: "" })],
      isLoading: false,
    });
    renderWidget();
    expect(screen.getByTestId("friends-playing-now-row")).toHaveTextContent(
      "a connected server",
    );
  });
});
