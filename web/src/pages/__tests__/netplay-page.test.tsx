import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { NetplayPage } from "../netplay-page";

vi.mock("@/hooks/use-netplay", () => ({
  useNetplaySessions: vi.fn(),
  useJoinNetplaySession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
  useCreateNetplaySession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
  useNetplayInvitesRealtime: vi.fn(),
  useNetplayInvites: vi.fn(() => ({
    data: { data: [], total: 0 },
    isLoading: false,
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

vi.mock("@/hooks/use-games", () => ({
  useGames: vi.fn(() => ({ data: { data: [] } })),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

import { useNetplaySessions } from "@/hooks/use-netplay";

const mockUseNetplaySessions = useNetplaySessions as ReturnType<typeof vi.fn>;

const mockSessions = {
  data: [
    {
      id: "s1",
      hostId: "u1",
      hostUsername: "alice",
      hostAvatarUrl: null,
      clientId: "u2",
      clientUsername: "bob",
      clientAvatarUrl: null,
      gameId: "g1",
      gameTitle: "Super Mario World",
      gameCoverUrl: "https://example.com/cover.png",
      consoleName: "SNES",
      status: "in_progress",
      endReason: null,
      inputDelay: 3,
      inviteCode: "ABC123",
      createdAt: "2026-02-13T10:00:00Z",
      startedAt: "2026-02-13T10:05:00Z",
      endedAt: null,
    },
    {
      id: "s2",
      hostId: "u3",
      hostUsername: "charlie",
      hostAvatarUrl: null,
      clientId: null,
      clientUsername: null,
      clientAvatarUrl: null,
      gameId: "g2",
      gameTitle: "Mega Man 2",
      gameCoverUrl: null,
      consoleName: "NES",
      status: "waiting",
      endReason: null,
      inputDelay: 3,
      inviteCode: "XYZ789",
      createdAt: "2026-02-13T11:00:00Z",
      startedAt: null,
      endedAt: null,
    },
  ],
  total: 2,
  page: 1,
  pageSize: 20,
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NetplayPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseNetplaySessions.mockReturnValue({
    data: mockSessions,
    isLoading: false,
  });
});

describe("NetplayPage", () => {
  it("renders heading and description", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Real-time multiplayer sessions."),
    ).toBeInTheDocument();
  });

  it("renders Create Session button", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: /Create Session/ }),
    ).toBeInTheDocument();
  });

  it("renders session rows", () => {
    renderPage();

    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Mega Man 2")).toBeInTheDocument();
  });

  it("shows invite code for waiting sessions", () => {
    renderPage();

    expect(screen.getByText("XYZ789")).toBeInTheDocument();
  });

  it("shows status badges", () => {
    renderPage();

    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(screen.getByText("Waiting for player")).toBeInTheDocument();
  });

  it("shows empty state when no sessions", () => {
    mockUseNetplaySessions.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 20 },
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("No netplay sessions yet")).toBeInTheDocument();
  });

  it("renders loading skeletons when loading", () => {
    mockUseNetplaySessions.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderPage();

    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders invite code input", () => {
    renderPage();

    expect(
      screen.getByPlaceholderText("e.g. ABC123"),
    ).toBeInTheDocument();
  });

  it("renders View Session button for invite code lookup", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: "View Session" }),
    ).toBeInTheDocument();
  });

  it("converts invite code input to uppercase", async () => {
    renderPage();

    const input = screen.getByPlaceholderText("e.g. ABC123");
    await userEvent.type(input, "abc");

    expect(input).toHaveValue("ABC");
  });
});
