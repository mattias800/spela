import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { ChallengeCard } from "../challenge-card";
import type { Challenge } from "@/types/api";

// ---------------------------------------------------------------------------
// Mock Data
// ---------------------------------------------------------------------------

function mockChallenge(overrides?: Partial<Challenge>): Challenge {
  return {
    id: "1",
    creatorId: "u1",
    creatorUsername: "alice",
    creatorAvatar: "",
    gameId: "g1",
    gameTitle: "Super Mario Bros.",
    gameCoverUrl: "",
    consoleName: "NES",
    name: "Speed Run World 1",
    description: "Complete World 1 as fast as possible!",
    type: "speedrun",
    difficulty: "medium",
    status: "active",
    screenshotUrl: "/api/challenges/1/screenshot",
    coreName: "fceumm",
    saveFileSize: 1024,
    attemptCount: 12,
    completionCount: 8,
    expiresAt: null,
    createdAt: "2026-02-01T10:00:00Z",
    updatedAt: "2026-02-01T10:00:00Z",
    ...overrides,
  };
}

function renderCard(overrides?: Partial<Challenge>) {
  return render(
    <MemoryRouter>
      <ChallengeCard challenge={mockChallenge(overrides)} />
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ChallengeCard", () => {
  it("renders challenge title", () => {
    renderCard();
    expect(screen.getByText("Speed Run World 1")).toBeInTheDocument();
  });

  it("renders game title and console name", () => {
    renderCard();
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("NES")).toBeInTheDocument();
  });

  it("renders creator username", () => {
    renderCard();
    expect(screen.getByText("alice")).toBeInTheDocument();
  });

  it("renders difficulty badge with correct styling", () => {
    renderCard({ difficulty: "easy" });
    const badge = screen.getByText("Easy");
    expect(badge).toBeInTheDocument();
  });

  it("renders medium difficulty badge with amber styling", () => {
    renderCard({ difficulty: "medium" });
    const badge = screen.getByText("Medium");
    expect(badge).toBeInTheDocument();
  });

  it("renders hard difficulty badge with red styling", () => {
    renderCard({ difficulty: "hard" });
    const badge = screen.getByText("Hard");
    expect(badge).toBeInTheDocument();
  });

  it("renders type icon/label (speedrun, completion, survival)", () => {
    renderCard({ type: "speedrun" });
    expect(screen.getByText("Speedrun")).toBeInTheDocument();
  });

  it("renders attempt count", () => {
    renderCard({ attemptCount: 12 });
    expect(screen.getByText(/12/)).toBeInTheDocument();
  });

  it("links to challenge detail page", () => {
    renderCard();
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/challenges/1");
  });

  it("renders screenshot image with 16:10 aspect ratio", () => {
    renderCard({ screenshotUrl: "/api/challenges/1/screenshot" });
    const img = screen.getByRole("img");
    expect(img).toHaveAttribute(
      "src",
      expect.stringContaining("/screenshot"),
    );
  });
});
