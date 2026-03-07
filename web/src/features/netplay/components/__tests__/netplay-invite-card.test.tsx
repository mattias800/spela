import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { NetplayInviteCard } from "../netplay-invite-card";
import type { NetplayInvite } from "@/types/api";

const mockAcceptMutate = vi.fn();
const mockDeclineMutate = vi.fn();

vi.mock("@/hooks/use-netplay", () => ({
  useAcceptNetplayInvite: vi.fn(() => ({
    mutate: mockAcceptMutate,
    isPending: false,
  })),
  useDeclineNetplayInvite: vi.fn(() => ({
    mutate: mockDeclineMutate,
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

const mockInvite: NetplayInvite = {
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
  gameCoverUrl: "https://example.com/smw.png",
  consoleName: "Super Nintendo",
  hostUsername: "alice",
  inputDelay: 3,
  status: "pending",
  createdAt: new Date().toISOString(),
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
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
});

describe("NetplayInviteCard", () => {
  it("renders the game title", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
  });

  it("renders the console badge", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    expect(screen.getByText("Super Nintendo")).toBeInTheDocument();
  });

  it("renders the inviter username", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    expect(screen.getByText("alice")).toBeInTheDocument();
  });

  it("renders accept and decline buttons", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    expect(screen.getByText("Accept")).toBeInTheDocument();
    expect(screen.getByText("Decline")).toBeInTheDocument();
  });

  it("calls accept mutation when Accept is clicked", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    await userEvent.click(screen.getByText("Accept"));
    expect(mockAcceptMutate).toHaveBeenCalledWith(
      "inv1",
      expect.any(Object),
    );
  });

  it("calls decline mutation when Decline is clicked", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    await userEvent.click(screen.getByText("Decline"));
    expect(mockDeclineMutate).toHaveBeenCalledWith(
      "inv1",
      expect.any(Object),
    );
  });

  it("renders the cover art image", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={mockInvite} />
      </Wrapper>,
    );

    const img = screen.getByAltText("Super Mario World");
    expect(img).toHaveAttribute("src", "https://example.com/smw.png");
  });

  it("renders placeholder when no cover art", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteCard invite={{ ...mockInvite, gameCoverUrl: null }} />
      </Wrapper>,
    );

    expect(screen.queryByAltText("Super Mario World")).not.toBeInTheDocument();
  });
});
