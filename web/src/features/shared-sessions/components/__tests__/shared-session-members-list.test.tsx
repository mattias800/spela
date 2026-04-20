import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { SharedSessionMembersList } from "../shared-session-members-list";
import type { SharedSessionMember } from "@/types/api";

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "u1", username: "alice", role: "user" },
  })),
}));

vi.mock("@/hooks/use-shared-sessions", () => ({
  useRemoveSharedSessionMember: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
  useInviteToSharedSession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
}));

vi.mock("@/hooks/use-social", () => ({
  useSearchUsers: vi.fn(() => ({
    data: { data: [], total: 0, page: 1, pageSize: 10 },
    isLoading: false,
  })),
  useRecentPartners: vi.fn(() => ({
    data: [],
    isLoading: false,
  })),
}));

vi.mock("@/hooks/use-debounced-value", () => ({
  useDebouncedValue: vi.fn((value: string) => value),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

const mockMembers: SharedSessionMember[] = [
  {
    userId: "u1",
    username: "alice",
    role: "owner",
    id: "m1",
    avatarUrl: "",
    joinedAt: "2026-02-01T10:00:00Z",
  },
  {
    userId: "u2",
    username: "bob",
    role: "member",
    id: "m2",
    avatarUrl: "",
    joinedAt: "2026-02-02T10:00:00Z",
  },
  {
    userId: "u3",
    username: "charlie",
    role: "member",
    id: "m3",
    avatarUrl: "",
    joinedAt: "2026-02-03T10:00:00Z",
  },
];

describe("SharedSessionMembersList", () => {
  it("renders all members", () => {
    render(
      <SharedSessionMembersList
        sharedSessionId="ss-1"
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
      <SharedSessionMembersList
        sharedSessionId="ss-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByText("(3)")).toBeInTheDocument();
  });

  it("renders owner badge for owner member", () => {
    render(
      <SharedSessionMembersList
        sharedSessionId="ss-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByText("Owner")).toBeInTheDocument();
  });

  it("shows invite button when user is owner", () => {
    render(
      <SharedSessionMembersList
        sharedSessionId="ss-1"
        members={mockMembers}
        isOwner={true}
      />,
    );

    expect(screen.getByText("Invite")).toBeInTheDocument();
  });

  it("hides invite button when user is not owner", () => {
    render(
      <SharedSessionMembersList
        sharedSessionId="ss-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.queryByText("Invite")).not.toBeInTheDocument();
  });

  it("renders section heading", () => {
    render(
      <SharedSessionMembersList
        sharedSessionId="ss-1"
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
      <SharedSessionMembersList
        sharedSessionId="ss-1"
        members={mockMembers}
        isOwner={false}
      />,
    );

    expect(screen.getByTestId("shared-session-member-u1")).toBeInTheDocument();
    expect(screen.getByTestId("shared-session-member-u2")).toBeInTheDocument();
    expect(screen.getByTestId("shared-session-member-u3")).toBeInTheDocument();
  });
});
