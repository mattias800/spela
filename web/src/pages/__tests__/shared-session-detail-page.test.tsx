import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { SharedSessionDetailPage } from "../shared-session-detail-page";

vi.mock("@/hooks/use-shared-sessions", () => ({
  useSharedSession: vi.fn(),
  useSharedSessionSaves: vi.fn(),
  useDeleteSharedSession: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useLeaveSharedSession: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useSharedSessionRealtime: vi.fn(),
  useRemoveSharedSessionMember: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useInviteToSharedSession: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useDeleteSharedSessionSave: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-sessions", () => ({
  useCloneSession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
    variables: null,
  })),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(() => ({
    data: [
      {
        id: "c1",
        name: "SNES",
        emulatorJsCore: "snes9x",
      },
    ],
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

import { useSharedSession, useSharedSessionSaves } from "@/hooks/use-shared-sessions";
import { useCloneSession } from "@/hooks/use-sessions";

const mockUseSharedSession = useSharedSession as ReturnType<typeof vi.fn>;
const mockUseSharedSessionSaves = useSharedSessionSaves as ReturnType<typeof vi.fn>;
const mockUseCloneSession = useCloneSession as ReturnType<typeof vi.fn>;

const mockSharedSessionDetail = {
  id: "ss-1",
  name: "Friday Night SNES",
  gameId: "g1",
  gameTitle: "Super Mario World",
  gameCoverUrl: "https://example.com/cover.png",
  consoleName: "SNES",
  ownerId: "u1",
  ownerUsername: "alice",
  status: "active",
  memberCount: 2,
  // Shared sessions carry a pointer to the backing personal session
  // where save-state bytes live; Clone to my library copies from this
  // backing session. Fixture includes it so the clone menu is visible
  // by default in tests.
  sessionId: "s42",
  createdAt: "2026-02-01T10:00:00Z",
  updatedAt: "2026-02-13T10:00:00Z",
  members: [
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
      isOnline: false,
    },
  ],
};

const mockSaves = [
  {
    id: "1",
    sharedSessionId: "ss-1",
    gameId: "100",
    userId: "1",
    username: "alice",
    name: "World 3 Save",
    fileSize: 32768,
    isAuto: false,
    createdAt: "2026-02-13T10:00:00Z",
    updatedAt: "2026-02-13T10:00:00Z",
  },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/shared-sessions/ss-1"]}>
        <Routes>
          <Route path="/shared-sessions/:id" element={<SharedSessionDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSharedSession.mockReturnValue({
    data: mockSharedSessionDetail,
    isLoading: false,
  });
  mockUseSharedSessionSaves.mockReturnValue({
    data: mockSaves,
    isLoading: false,
  });
});

describe("SharedSessionDetailPage", () => {
  it("renders shared session name", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Friday Night SNES" }),
    ).toBeInTheDocument();
  });

  it("renders game title", () => {
    renderPage();

    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
  });

  it("renders status badge", () => {
    renderPage();

    expect(screen.getByText("active")).toBeInTheDocument();
  });

  it("renders members section", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Members" }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("shared-session-member-u1")).toBeInTheDocument();
    expect(screen.getByTestId("shared-session-member-u2")).toBeInTheDocument();
  });

  it("renders saves section", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Shared Session Saves" }),
    ).toBeInTheDocument();
    expect(screen.getByText("World 3 Save")).toBeInTheDocument();
  });

  it("exposes Delete shared session from the `…` menu for owners", async () => {
    renderPage();
    const heroActions = screen.getByTestId("shared-session-hero-actions");
    await userEvent.click(
      heroActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    expect(
      screen.getByRole("menuitem", { name: /delete shared session/i }),
    ).toBeInTheDocument();
  });

  it("exposes Leave shared session from the `…` menu for non-owners", async () => {
    mockUseSharedSession.mockReturnValue({
      data: { ...mockSharedSessionDetail, ownerId: "u999" },
      isLoading: false,
    });
    renderPage();
    const heroActions = screen.getByTestId("shared-session-hero-actions");
    await userEvent.click(
      heroActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    expect(
      screen.getByRole("menuitem", { name: /leave shared session/i }),
    ).toBeInTheDocument();
  });

  it("shows not found when shared session is null", () => {
    mockUseSharedSession.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Shared session not found")).toBeInTheDocument();
  });

  it("renders play button", () => {
    renderPage();

    expect(screen.getByTestId("shared-session-play-btn")).toBeInTheDocument();
  });

  it("shows owner badge on owner member", () => {
    renderPage();

    expect(screen.getByText("Owner")).toBeInTheDocument();
  });

  it("renders empty saves state when no saves", () => {
    mockUseSharedSessionSaves.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("No saves yet")).toBeInTheDocument();
  });

  it("opens the clone dialog from the hero `…` menu (US-1)", async () => {
    renderPage();
    const heroActions = screen.getByTestId("shared-session-hero-actions");
    await userEvent.click(
      heroActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /clone to my library/i }),
    );
    expect(screen.getByTestId("clone-session-dialog")).toBeInTheDocument();
    expect(screen.getByTestId("clone-session-name-input")).toHaveValue(
      "Friday Night SNES (Copy)",
    );
  });

  it("calls cloneSession on the backing session ID when confirmed", async () => {
    const cloneMutate = vi.fn();
    mockUseCloneSession.mockReturnValue({
      mutate: cloneMutate,
      isPending: false,
      variables: null,
    });
    renderPage();
    const heroActions = screen.getByTestId("shared-session-hero-actions");
    await userEvent.click(
      heroActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /clone to my library/i }),
    );
    await userEvent.click(screen.getByTestId("clone-session-confirm"));
    // The endpoint takes the *backing session*'s ID, not the shared
    // session wrapper's ID — the shared session is UI shell.
    expect(cloneMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "s42",
        name: "Friday Night SNES (Copy)",
      }),
      expect.any(Object),
    );
  });

  it("omits the Clone menu entry when no backing session exists yet", async () => {
    mockUseSharedSession.mockReturnValue({
      data: { ...mockSharedSessionDetail, sessionId: null },
      isLoading: false,
    });
    renderPage();
    const heroActions = screen.getByTestId("shared-session-hero-actions");
    await userEvent.click(
      heroActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    expect(
      screen.queryByRole("menuitem", { name: /clone to my library/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps Clone behind the `…` menu — no standalone CTA (#553)", () => {
    renderPage();
    expect(
      screen.queryByRole("button", { name: /clone to my library/i }),
    ).not.toBeInTheDocument();
    // Menu item only appears after menu open — asserted by the tests
    // above. This test locks in the initial-render contract.
    expect(
      screen.queryByRole("menuitem", { name: /clone to my library/i }),
    ).not.toBeInTheDocument();
  });
});
