import { render, screen } from "@testing-library/react";
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

const mockUseSharedSession = useSharedSession as ReturnType<typeof vi.fn>;
const mockUseSharedSessionSaves = useSharedSessionSaves as ReturnType<typeof vi.fn>;

const mockSharedSessionDetail = {
  id: "ss-1",
  name: "Friday Night SNES",
  description: "We play every Friday",
  gameId: "g1",
  gameTitle: "Super Mario World",
  gameCoverUrl: "https://example.com/cover.png",
  gameConsoleName: "SNES",
  ownerId: "u1",
  ownerUsername: "alice",
  status: "active",
  memberCount: 2,
  lastActivityAt: "2026-02-13T10:00:00Z",
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

  it("renders shared session description", () => {
    renderPage();

    expect(screen.getByText("We play every Friday")).toBeInTheDocument();
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

  it("shows delete button for shared session owner", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: "Delete" }),
    ).toBeInTheDocument();
  });

  it("shows leave button when not owner", () => {
    mockUseSharedSession.mockReturnValue({
      data: { ...mockSharedSessionDetail, ownerId: "u999" },
      isLoading: false,
    });
    renderPage();

    expect(
      screen.getByRole("button", { name: /Leave/ }),
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
});
