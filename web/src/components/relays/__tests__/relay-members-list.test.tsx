import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { RelayMembersList } from "../relay-members-list";
import type { RelayMember } from "@/types/api";

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "u1", username: "alice", role: "user" },
  })),
}));

vi.mock("@/hooks/use-relays", () => ({
  useRemoveRelayMember: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
  useInviteToRelay: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

const mockMembers: RelayMember[] = [
  {
    userId: "u1",
    username: "alice",
    role: "owner",
    joinedAt: "2026-02-01T10:00:00Z",
    isOnline: true,
  },
  {
    userId: "u2",
    username: "bob",
    role: "member",
    joinedAt: "2026-02-02T10:00:00Z",
    lastPlayedAt: "2026-02-13T10:00:00Z",
    isOnline: false,
  },
  {
    userId: "u3",
    username: "charlie",
    role: "member",
    joinedAt: "2026-02-03T10:00:00Z",
    isOnline: true,
  },
];

describe("RelayMembersList", () => {
  it("renders all members", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
    expect(screen.getByText("charlie")).toBeInTheDocument();
  });

  it("shows member count", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByText("(3)")).toBeInTheDocument();
  });

  it("renders owner badge for owner member", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByText("Owner")).toBeInTheDocument();
  });

  it("shows invite button when user is owner", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={true}
      />,
    );

    expect(screen.getByText("Invite")).toBeInTheDocument();
  });

  it("hides invite button when user is not owner", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.queryByText("Invite")).not.toBeInTheDocument();
  });

  it("renders section heading", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(
      screen.getByRole("heading", { name: "Members" }),
    ).toBeInTheDocument();
  });

  it("renders member test IDs", () => {
    render(
      <RelayMembersList
        relayId="relay-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByTestId("relay-member-u1")).toBeInTheDocument();
    expect(screen.getByTestId("relay-member-u2")).toBeInTheDocument();
    expect(screen.getByTestId("relay-member-u3")).toBeInTheDocument();
  });
});
