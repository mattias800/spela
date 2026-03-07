import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NetplaySessionInvites } from "../netplay-session-invites";
import type { NetplayInvite } from "@/types/api";

const mockInvites: NetplayInvite[] = [
  {
    id: "inv1",
    netplaySessionId: "s1",
    inviterId: "u1",
    inviterUsername: "host",
    inviterAvatarUrl: null,
    inviteeId: "u2",
    inviteeUsername: "alice",
    inviteeAvatarUrl: null,
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: null,
    consoleName: "Super Nintendo",
    hostUsername: "host",
    inputDelay: 3,
    status: "pending",
    createdAt: new Date().toISOString(),
  },
  {
    id: "inv2",
    netplaySessionId: "s1",
    inviterId: "u1",
    inviterUsername: "host",
    inviterAvatarUrl: null,
    inviteeId: "u3",
    inviteeUsername: "bob",
    inviteeAvatarUrl: null,
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: null,
    consoleName: "Super Nintendo",
    hostUsername: "host",
    inputDelay: 3,
    status: "accepted",
    createdAt: new Date().toISOString(),
  },
  {
    id: "inv3",
    netplaySessionId: "s1",
    inviterId: "u1",
    inviterUsername: "host",
    inviterAvatarUrl: null,
    inviteeId: "u4",
    inviteeUsername: "charlie",
    inviteeAvatarUrl: null,
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: null,
    consoleName: "Super Nintendo",
    hostUsername: "host",
    inputDelay: 3,
    status: "declined",
    createdAt: new Date().toISOString(),
  },
];

let mockData: NetplayInvite[] | undefined;
let mockIsLoading = false;

vi.mock("@/hooks/use-netplay", () => ({
  useSessionNetplayInvites: vi.fn(() => ({
    data: mockData,
    isLoading: mockIsLoading,
  })),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockData = undefined;
  mockIsLoading = false;
});

describe("NetplaySessionInvites", () => {
  it("renders nothing when there are no invites", () => {
    mockData = [];
    const Wrapper = createWrapper();
    const { container } = render(
      <Wrapper>
        <NetplaySessionInvites sessionId="s1" />
      </Wrapper>,
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders the section header with count", () => {
    mockData = mockInvites;
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplaySessionInvites sessionId="s1" />
      </Wrapper>,
    );

    expect(screen.getByText("Invited Players")).toBeInTheDocument();
    expect(screen.getByText("(3)")).toBeInTheDocument();
  });

  it("renders invite rows with usernames", () => {
    mockData = mockInvites;
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplaySessionInvites sessionId="s1" />
      </Wrapper>,
    );

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
    expect(screen.getByText("charlie")).toBeInTheDocument();
  });

  it("renders status badges for each invite", () => {
    mockData = mockInvites;
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplaySessionInvites sessionId="s1" />
      </Wrapper>,
    );

    expect(screen.getByText("Pending")).toBeInTheDocument();
    expect(screen.getByText("Accepted")).toBeInTheDocument();
    expect(screen.getByText("Declined")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    mockIsLoading = true;
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplaySessionInvites sessionId="s1" />
      </Wrapper>,
    );

    expect(screen.getByText("Invited Players")).toBeInTheDocument();
  });
});
