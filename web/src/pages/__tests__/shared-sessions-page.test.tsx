import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { SharedSessionsPage } from "../shared-sessions-page";

vi.mock("@/hooks/use-shared-sessions", () => ({
  useMySharedSessions: vi.fn(),
  useSharedSessionInvitations: vi.fn(),
  useAcceptSharedSessionInvitation: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useRejectSharedSessionInvitation: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useSharedSessionRealtime: vi.fn(),
  useCreateSharedSession: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
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

import {
  useMySharedSessions,
  useSharedSessionInvitations,
} from "@/hooks/use-shared-sessions";

const mockUseMySharedSessions = useMySharedSessions as ReturnType<typeof vi.fn>;
const mockUseSharedSessionInvitations = useSharedSessionInvitations as ReturnType<typeof vi.fn>;

const mockSharedSessions = {
  data: [
    {
      id: "ss-1",
      name: "Friday Night SNES",
      gameId: "g1",
      gameTitle: "Super Mario World",
      gameCoverUrl: "https://example.com/cover.png",
      gameConsoleName: "SNES",
      ownerId: "u1",
      ownerUsername: "alice",
      status: "active",
      memberCount: 3,
      lastActivityAt: "2026-02-13T10:00:00Z",
      createdAt: "2026-02-01T10:00:00Z",
      updatedAt: "2026-02-13T10:00:00Z",
    },
  ],
  total: 1,
  page: 1,
  pageSize: 24,
};

const mockInvitations = [
  {
    id: "inv-1",
    sharedSessionId: "ss-2",
    sharedSessionName: "RPG Club",
    gameId: "g2",
    gameTitle: "Chrono Trigger",
    gameCoverUrl: "https://example.com/chrono.png",
    consoleName: "SNES",
    inviterId: "u1",
    inviterUsername: "bob",
    inviterAvatarUrl: "",
    inviteeId: "u2",
    inviteeUsername: "alice",
    status: "pending",
    createdAt: "2026-02-13T10:00:00Z",
  },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SharedSessionsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseMySharedSessions.mockReturnValue({ data: mockSharedSessions, isLoading: false });
  mockUseSharedSessionInvitations.mockReturnValue({
    data: mockInvitations,
    isLoading: false,
  });
});

describe("SharedSessionsPage", () => {
  it("renders heading and description", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Shared Sessions", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Take turns playing games with friends using shared save states.",
      ),
    ).toBeInTheDocument();
  });

  it("renders My Shared Sessions tab by default", () => {
    renderPage();

    expect(screen.getByText("Friday Night SNES")).toBeInTheDocument();
  });

  it("renders Create Shared Session button", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: /Create Shared Session/ }),
    ).toBeInTheDocument();
  });

  it("shows empty state when no shared sessions", () => {
    mockUseMySharedSessions.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 24 },
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("No shared sessions yet")).toBeInTheDocument();
  });

  it("shows invitation count badge on Invitations tab", () => {
    renderPage();

    const invitationsTab = screen.getByText("Invitations");
    expect(invitationsTab.parentElement).toHaveTextContent("1");
  });

  it("switches to Invitations tab and shows invitations", async () => {
    renderPage();

    await userEvent.click(screen.getByText("Invitations"));

    expect(screen.getByText("RPG Club")).toBeInTheDocument();
    expect(
      screen.getByTestId("shared-session-invitation-inv-1"),
    ).toBeInTheDocument();
  });

  it("shows empty state when no invitations", async () => {
    mockUseSharedSessionInvitations.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();

    await userEvent.click(screen.getByText("Invitations"));

    expect(screen.getByText("No invitations")).toBeInTheDocument();
  });

  it("renders loading skeletons when loading", () => {
    mockUseMySharedSessions.mockReturnValue({ data: undefined, isLoading: true });
    const { container } = renderPage();

    // Skeleton elements should be present (animation classes)
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
});
