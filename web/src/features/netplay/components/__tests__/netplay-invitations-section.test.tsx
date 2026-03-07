import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { NetplayInvitationsSection } from "../netplay-invitations-section";
import type { NetplayInvite } from "@/types/api";

const mockPendingInvite: NetplayInvite = {
  id: "inv1",
  netplaySessionId: "s1",
  inviterId: "u1",
  inviterUsername: "alice",
  inviterAvatarUrl: null,
  inviteeId: "u2",
  inviteeUsername: "bob",
  inviteeAvatarUrl: null,
  gameId: "g1",
  gameTitle: "Super Mario World",
  gameCoverUrl: null,
  consoleName: "Super Nintendo",
  hostUsername: "alice",
  inputDelay: 3,
  status: "pending",
  createdAt: new Date().toISOString(),
};

const mockDeclinedInvite: NetplayInvite = {
  ...mockPendingInvite,
  id: "inv2",
  status: "declined",
};

let mockInvitesData: { data: NetplayInvite[]; total: number } | undefined;
let mockIsLoading = false;

vi.mock("@/hooks/use-netplay", () => ({
  useNetplayInvites: vi.fn(() => ({
    data: mockInvitesData,
    isLoading: mockIsLoading,
  })),
  useAcceptNetplayInvite: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
  useDeclineNetplayInvite: vi.fn(() => ({
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

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockInvitesData = undefined;
  mockIsLoading = false;
});

describe("NetplayInvitationsSection", () => {
  it("renders nothing when there are no pending invites", () => {
    mockInvitesData = { data: [], total: 0 };
    const Wrapper = createWrapper();
    const { container } = render(
      <Wrapper>
        <NetplayInvitationsSection />
      </Wrapper>,
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when all invites are non-pending", () => {
    mockInvitesData = { data: [mockDeclinedInvite], total: 1 };
    const Wrapper = createWrapper();
    const { container } = render(
      <Wrapper>
        <NetplayInvitationsSection />
      </Wrapper>,
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders the section header with count when there are pending invites", () => {
    mockInvitesData = { data: [mockPendingInvite], total: 1 };
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInvitationsSection />
      </Wrapper>,
    );

    expect(screen.getByText("Invitations")).toBeInTheDocument();
    expect(screen.getByText("(1)")).toBeInTheDocument();
  });

  it("renders invite cards for pending invites", () => {
    mockInvitesData = { data: [mockPendingInvite], total: 1 };
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInvitationsSection />
      </Wrapper>,
    );

    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Accept")).toBeInTheDocument();
    expect(screen.getByText("Decline")).toBeInTheDocument();
  });

  it("shows skeleton when loading", () => {
    mockIsLoading = true;
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInvitationsSection />
      </Wrapper>,
    );

    expect(screen.getByText("Invitations")).toBeInTheDocument();
  });
});
