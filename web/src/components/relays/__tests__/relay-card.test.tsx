import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { RelayCard } from "../relay-card";
import type { Relay } from "@/types/api";

function makeRelay(overrides: Partial<Relay> = {}): Relay {
  return {
    id: "relay-1",
    name: "Friday Night SNES",
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: "https://example.com/cover.png",
    gameConsoleName: "SNES",
    ownerId: "u1",
    ownerUsername: "alice",
    status: "active",
    memberCount: 3,
    lastActivityAt: "2026-02-13T10:00:00Z",
    createdAt: "2026-02-01T10:00:00Z",
    updatedAt: "2026-02-13T10:00:00Z",
    ...overrides,
  };
}

describe("RelayCard", () => {
  it("renders relay name and game info", () => {
    const relay = makeRelay();
    render(
      <MemoryRouter>
        <RelayCard relay={relay} />
      </MemoryRouter>,
    );

    expect(screen.getByText("Friday Night SNES")).toBeInTheDocument();
    expect(
      screen.getByText(/Super Mario World.*SNES/),
    ).toBeInTheDocument();
  });

  it("renders active status badge", () => {
    const relay = makeRelay({ status: "active" });
    render(
      <MemoryRouter>
        <RelayCard relay={relay} />
      </MemoryRouter>,
    );

    expect(screen.getByText("active")).toBeInTheDocument();
  });

  it("renders completed status badge", () => {
    const relay = makeRelay({ status: "completed" });
    render(
      <MemoryRouter>
        <RelayCard relay={relay} />
      </MemoryRouter>,
    );

    expect(screen.getByText("completed")).toBeInTheDocument();
  });

  it("links to relay detail page", () => {
    const relay = makeRelay({ id: "relay-42" });
    render(
      <MemoryRouter>
        <RelayCard relay={relay} />
      </MemoryRouter>,
    );

    const link = screen.getByTestId("relay-card-relay-42");
    expect(link).toHaveAttribute("href", "/relays/relay-42");
  });

  it("renders cover image when available", () => {
    const relay = makeRelay({
      gameCoverUrl: "https://example.com/cover.png",
    });
    render(
      <MemoryRouter>
        <RelayCard relay={relay} />
      </MemoryRouter>,
    );

    const img = screen.getByAltText("Super Mario World");
    expect(img).toHaveAttribute("src", "https://example.com/cover.png");
  });

  it("renders member avatars when provided", () => {
    const relay = makeRelay();
    const members = [
      { username: "alice" },
      { username: "bob" },
    ];
    render(
      <MemoryRouter>
        <RelayCard relay={relay} members={members} />
      </MemoryRouter>,
    );

    expect(screen.getByTitle("alice")).toBeInTheDocument();
    expect(screen.getByTitle("bob")).toBeInTheDocument();
  });
});
