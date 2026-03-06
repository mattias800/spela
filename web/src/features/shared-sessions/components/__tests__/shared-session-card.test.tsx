import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SharedSessionCard } from "../shared-session-card";
import type { SharedSession } from "@/types/api";

function makeSharedSession(overrides: Partial<SharedSession> = {}): SharedSession {
  return {
    id: "ss-1",
    name: "Friday Night SNES",
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: "https://example.com/cover.png",
    gameConsoleName: "SNES",
    ownerId: "u1",
    ownerUsername: "alice",
    status: "active",
    activeUserId: null,
    turnTakenAt: null,
    memberCount: 3,
    lastActivityAt: "2026-02-13T10:00:00Z",
    createdAt: "2026-02-01T10:00:00Z",
    updatedAt: "2026-02-13T10:00:00Z",
    ...overrides,
  };
}

describe("SharedSessionCard", () => {
  it("renders shared session name and game info", () => {
    const sharedSession = makeSharedSession();
    render(
      <MemoryRouter>
        <SharedSessionCard sharedSession={sharedSession} />
      </MemoryRouter>,
    );

    expect(screen.getByText("Friday Night SNES")).toBeInTheDocument();
    expect(
      screen.getByText(/Super Mario World.*SNES/),
    ).toBeInTheDocument();
  });

  it("renders active status badge", () => {
    const sharedSession = makeSharedSession({ status: "active" });
    render(
      <MemoryRouter>
        <SharedSessionCard sharedSession={sharedSession} />
      </MemoryRouter>,
    );

    expect(screen.getByText("active")).toBeInTheDocument();
  });

  it("renders completed status badge", () => {
    const sharedSession = makeSharedSession({ status: "completed" });
    render(
      <MemoryRouter>
        <SharedSessionCard sharedSession={sharedSession} />
      </MemoryRouter>,
    );

    expect(screen.getByText("completed")).toBeInTheDocument();
  });

  it("links to shared session detail page", () => {
    const sharedSession = makeSharedSession({ id: "ss-42" });
    render(
      <MemoryRouter>
        <SharedSessionCard sharedSession={sharedSession} />
      </MemoryRouter>,
    );

    const link = screen.getByTestId("shared-session-card-ss-42");
    expect(link).toHaveAttribute("href", "/shared-sessions/ss-42");
  });

  it("renders cover image when available", () => {
    const sharedSession = makeSharedSession({
      gameCoverUrl: "https://example.com/cover.png",
    });
    render(
      <MemoryRouter>
        <SharedSessionCard sharedSession={sharedSession} />
      </MemoryRouter>,
    );

    const img = screen.getByAltText("Super Mario World");
    expect(img).toHaveAttribute("src", "https://example.com/cover.png");
  });

  it("renders member avatars when provided", () => {
    const sharedSession = makeSharedSession();
    const members = [
      { username: "alice" },
      { username: "bob" },
    ];
    render(
      <MemoryRouter>
        <SharedSessionCard sharedSession={sharedSession} members={members} />
      </MemoryRouter>,
    );

    expect(screen.getByTitle("alice")).toBeInTheDocument();
    expect(screen.getByTitle("bob")).toBeInTheDocument();
  });
});
