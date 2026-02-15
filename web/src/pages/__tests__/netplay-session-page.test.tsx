import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { NetplaySessionPage } from "../netplay-session-page";

vi.mock("@/hooks/use-netplay", () => ({
  useNetplaySession: vi.fn(),
  useDeleteNetplaySession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "u1", username: "alice", role: "user" },
  })),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

import { useNetplaySession } from "@/hooks/use-netplay";

const mockUseNetplaySession = useNetplaySession as ReturnType<typeof vi.fn>;

const mockWaitingSession = {
  id: "s1",
  hostId: "u1",
  hostUsername: "alice",
  hostAvatarUrl: null,
  clientId: null,
  clientUsername: null,
  clientAvatarUrl: null,
  gameId: "g1",
  gameTitle: "Super Mario World",
  gameCoverUrl: "https://example.com/cover.png",
  consoleName: "SNES",
  status: "waiting",
  endReason: null,
  inputDelay: 3,
  inviteCode: "ABC123",
  createdAt: "2026-02-13T10:00:00Z",
  startedAt: null,
  endedAt: null,
};

const mockInProgressSession = {
  ...mockWaitingSession,
  status: "in_progress",
  clientId: "u2",
  clientUsername: "bob",
  clientAvatarUrl: null,
  startedAt: "2026-02-13T10:05:00Z",
};

const mockEndedSession = {
  ...mockInProgressSession,
  status: "ended",
  endReason: "completed",
  endedAt: "2026-02-13T11:00:00Z",
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/netplay/s1"]}>
        <Routes>
          <Route path="/netplay/:id" element={<NetplaySessionPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseNetplaySession.mockReturnValue({
    data: mockWaitingSession,
    isLoading: false,
  });
});

describe("NetplaySessionPage", () => {
  it("renders session name", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Super Mario World" }),
    ).toBeInTheDocument();
  });

  it("renders game title in meta info", () => {
    renderPage();

    // Game title appears in heading and meta info
    const gameTexts = screen.getAllByText("Super Mario World");
    expect(gameTexts.length).toBeGreaterThanOrEqual(1);
  });

  it("renders console badge", () => {
    renderPage();

    expect(screen.getByText("SNES")).toBeInTheDocument();
  });

  it("renders status badge", () => {
    renderPage();

    // "Waiting for player" appears in badge and empty player slot
    const waitingTexts = screen.getAllByText(/Waiting for player/);
    expect(waitingTexts.length).toBeGreaterThanOrEqual(1);
  });

  it("shows invite code for waiting sessions", () => {
    renderPage();

    expect(screen.getByText("ABC123")).toBeInTheDocument();
  });

  it("shows open in app banner for non-ended sessions", () => {
    renderPage();

    expect(
      screen.getByText(
        "Open this session in the Spela player app to join and play.",
      ),
    ).toBeInTheDocument();
  });

  it("shows cancel button when user is host and session is waiting", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: /Cancel Session/ }),
    ).toBeInTheDocument();
  });

  it("hides cancel button when user is not host", () => {
    mockUseNetplaySession.mockReturnValue({
      data: { ...mockWaitingSession, hostId: "u999" },
      isLoading: false,
    });
    renderPage();

    expect(
      screen.queryByRole("button", { name: /Cancel Session/ }),
    ).not.toBeInTheDocument();
  });

  it("shows player names for in-progress session", () => {
    mockUseNetplaySession.mockReturnValue({
      data: mockInProgressSession,
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("In Progress")).toBeInTheDocument();
    // Player names appear in multiple places (player slots, meta info, status text)
    const aliceTexts = screen.getAllByText("alice");
    expect(aliceTexts.length).toBeGreaterThanOrEqual(1);
    const bobTexts = screen.getAllByText("bob");
    expect(bobTexts.length).toBeGreaterThanOrEqual(1);
  });

  it("shows Create New Session button for ended sessions", () => {
    mockUseNetplaySession.mockReturnValue({
      data: mockEndedSession,
      isLoading: false,
    });
    renderPage();

    expect(
      screen.getByRole("button", { name: /Create New Session/ }),
    ).toBeInTheDocument();
  });

  it("shows end reason for ended sessions", () => {
    mockUseNetplaySession.mockReturnValue({
      data: mockEndedSession,
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("does not show open in app banner for ended sessions", () => {
    mockUseNetplaySession.mockReturnValue({
      data: mockEndedSession,
      isLoading: false,
    });
    renderPage();

    expect(
      screen.queryByText(
        "Open this session in the Spela player app to join and play.",
      ),
    ).not.toBeInTheDocument();
  });

  it("shows not found when session is null", () => {
    mockUseNetplaySession.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Session not found")).toBeInTheDocument();
  });

  it("renders players section", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Players" }),
    ).toBeInTheDocument();
  });

  it("shows empty slot when waiting for second player", () => {
    renderPage();

    expect(
      screen.getByTestId("netplay-player-slot-empty"),
    ).toBeInTheDocument();
  });

  it("shows input delay in meta info", () => {
    renderPage();

    expect(screen.getByText("Input Delay:")).toBeInTheDocument();
    expect(screen.getByText("3 frames")).toBeInTheDocument();
  });

  it("shows host username in meta info", () => {
    renderPage();

    expect(screen.getByText("Host:")).toBeInTheDocument();
  });
});
