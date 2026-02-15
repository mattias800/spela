import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { InvitationCard } from "../invitation-card";
import type { RelayInvitation } from "@/types/api";

function makeInvitation(
  overrides: Partial<RelayInvitation> = {},
): RelayInvitation {
  return {
    id: "inv-1",
    relayId: "relay-1",
    relayName: "Friday Night SNES",
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: "https://example.com/cover.png",
    gameConsoleName: "SNES",
    inviterUsername: "alice",
    createdAt: "2026-02-13T10:00:00Z",
    ...overrides,
  };
}

describe("InvitationCard", () => {
  it("renders relay name and game info", () => {
    render(
      <InvitationCard
        invitation={makeInvitation()}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        isAccepting={false}
        isRejecting={false}
      />,
    );

    expect(screen.getByText("Friday Night SNES")).toBeInTheDocument();
    expect(
      screen.getByText(/Super Mario World.*SNES/),
    ).toBeInTheDocument();
  });

  it("renders inviter username", () => {
    render(
      <InvitationCard
        invitation={makeInvitation({ inviterUsername: "bob" })}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        isAccepting={false}
        isRejecting={false}
      />,
    );

    expect(screen.getByText("bob")).toBeInTheDocument();
  });

  it("calls onAccept when accept button is clicked", async () => {
    const onAccept = vi.fn();
    render(
      <InvitationCard
        invitation={makeInvitation()}
        onAccept={onAccept}
        onReject={vi.fn()}
        isAccepting={false}
        isRejecting={false}
      />,
    );

    await userEvent.click(screen.getByText("Accept"));
    expect(onAccept).toHaveBeenCalledOnce();
  });

  it("calls onReject when decline button is clicked", async () => {
    const onReject = vi.fn();
    render(
      <InvitationCard
        invitation={makeInvitation()}
        onAccept={vi.fn()}
        onReject={onReject}
        isAccepting={false}
        isRejecting={false}
      />,
    );

    await userEvent.click(screen.getByText("Decline"));
    expect(onReject).toHaveBeenCalledOnce();
  });

  it("disables decline button while accepting", () => {
    render(
      <InvitationCard
        invitation={makeInvitation()}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        isAccepting={true}
        isRejecting={false}
      />,
    );

    expect(screen.getByText("Decline").closest("button")).toBeDisabled();
  });

  it("disables accept button while rejecting", () => {
    render(
      <InvitationCard
        invitation={makeInvitation()}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        isAccepting={false}
        isRejecting={true}
      />,
    );

    expect(screen.getByText("Accept").closest("button")).toBeDisabled();
  });

  it("renders game cover image", () => {
    render(
      <InvitationCard
        invitation={makeInvitation()}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        isAccepting={false}
        isRejecting={false}
      />,
    );

    const img = screen.getByAltText("Super Mario World");
    expect(img).toBeInTheDocument();
  });
});
