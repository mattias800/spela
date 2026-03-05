import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { SessionCard } from "../session-card";
import type { GameSession } from "@/types/api";

const baseSession: GameSession = {
  id: "ses-1",
  gameId: "g1",
  name: "Main Playthrough",
  lastPlayedAt: "2026-03-01T10:00:00Z",
  lastPlayedByUsername: "alice",
  totalPlayTime: 3600,
  screenshotUrl: null,
  cheatsEnabled: false,
  memberCount: 1,
  memberAvatars: [],
  createdAt: "2026-02-28T10:00:00Z",
  updatedAt: "2026-03-01T10:00:00Z",
};

const onContinue = vi.fn();
const onRename = vi.fn();
const onDelete = vi.fn();

function renderCard(session: GameSession = baseSession, isDeleting = false) {
  return render(
    <SessionCard
      session={session}
      onContinue={onContinue}
      onRename={onRename}
      onDelete={onDelete}
      isDeleting={isDeleting}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("SessionCard", () => {
  it("renders session name", () => {
    renderCard();
    expect(screen.getByText("Main Playthrough")).toBeInTheDocument();
  });

  it("renders play time", () => {
    renderCard();
    expect(screen.getByText("1h 0m")).toBeInTheDocument();
  });

  it("renders last played time", () => {
    renderCard();
    // formatRelativeTime will produce some relative text
    expect(screen.getByTestId("session-card-ses-1")).toBeInTheDocument();
  });

  it("calls onContinue when Continue clicked", () => {
    renderCard();
    fireEvent.click(screen.getByText("Continue"));
    expect(onContinue).toHaveBeenCalledWith(baseSession);
  });

  it("enters rename mode when rename button clicked", () => {
    renderCard();
    fireEvent.click(screen.getByLabelText("Rename session"));
    expect(screen.getByTestId("session-name-input")).toBeInTheDocument();
    expect(screen.getByTestId("session-name-input")).toHaveValue(
      "Main Playthrough",
    );
  });

  it("calls onRename when name is changed and submitted", () => {
    renderCard();
    fireEvent.click(screen.getByLabelText("Rename session"));

    const input = screen.getByTestId("session-name-input");
    fireEvent.change(input, { target: { value: "New Name" } });
    fireEvent.keyDown(input, { key: "Enter" });

    expect(onRename).toHaveBeenCalledWith(baseSession, "New Name");
  });

  it("cancels rename on Escape", () => {
    renderCard();
    fireEvent.click(screen.getByLabelText("Rename session"));

    const input = screen.getByTestId("session-name-input");
    fireEvent.change(input, { target: { value: "New Name" } });
    fireEvent.keyDown(input, { key: "Escape" });

    expect(onRename).not.toHaveBeenCalled();
    expect(screen.getByText("Main Playthrough")).toBeInTheDocument();
  });

  it("shows delete confirmation when delete clicked", () => {
    renderCard();
    fireEvent.click(screen.getByLabelText("Delete session"));
    expect(screen.getByText("Delete Session")).toBeInTheDocument();
    expect(
      screen.getByText(
        'Delete "Main Playthrough"? All save data for this session will be permanently removed.',
      ),
    ).toBeInTheDocument();
  });

  it("calls onDelete when delete is confirmed", () => {
    renderCard();
    fireEvent.click(screen.getByLabelText("Delete session"));
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(onDelete).toHaveBeenCalledWith(baseSession);
  });

  it("shows cheats badge when cheats enabled", () => {
    renderCard({ ...baseSession, cheatsEnabled: true });
    expect(screen.getByText("Cheats")).toBeInTheDocument();
  });

  it("does not show cheats badge when cheats disabled", () => {
    renderCard();
    expect(screen.queryByText("Cheats")).not.toBeInTheDocument();
  });

  it("shows member count for multiplayer sessions", () => {
    renderCard({ ...baseSession, memberCount: 3 });
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("shows member avatars", () => {
    renderCard({
      ...baseSession,
      memberAvatars: [
        "https://example.com/a1.png",
        "https://example.com/a2.png",
      ],
    });
    const avatarContainer = screen.getByTestId("member-avatars");
    expect(avatarContainer).toBeInTheDocument();
    expect(avatarContainer.querySelectorAll("img")).toHaveLength(2);
  });

  it("truncates member avatars at 3 with overflow count", () => {
    renderCard({
      ...baseSession,
      memberAvatars: [
        "https://example.com/a1.png",
        "https://example.com/a2.png",
        "https://example.com/a3.png",
        "https://example.com/a4.png",
        "https://example.com/a5.png",
      ],
    });
    expect(screen.getByText("+2")).toBeInTheDocument();
  });

  it("shows screenshot thumbnail when available", () => {
    renderCard({
      ...baseSession,
      screenshotUrl: "https://example.com/screenshot.png",
    });
    const img = screen.getByAltText("Main Playthrough");
    expect(img).toHaveAttribute(
      "src",
      "https://example.com/screenshot.png",
    );
  });
});
