import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { RelayDetailPage } from "../relay-detail-page";

vi.mock("@/hooks/use-relays", () => ({
  useRelay: vi.fn(),
  useRelaySaves: vi.fn(),
  useDeleteRelay: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useLeaveRelay: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useRelayRealtime: vi.fn(),
  useRemoveRelayMember: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useInviteToRelay: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useDeleteRelaySave: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
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

import { useRelay, useRelaySaves } from "@/hooks/use-relays";

const mockUseRelay = useRelay as ReturnType<typeof vi.fn>;
const mockUseRelaySaves = useRelaySaves as ReturnType<typeof vi.fn>;

const mockRelayDetail = {
  id: "relay-1",
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
    relayId: "relay-1",
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
      <MemoryRouter initialEntries={["/relays/relay-1"]}>
        <Routes>
          <Route path="/relays/:id" element={<RelayDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseRelay.mockReturnValue({
    data: mockRelayDetail,
    isLoading: false,
  });
  mockUseRelaySaves.mockReturnValue({
    data: mockSaves,
    isLoading: false,
  });
});

describe("RelayDetailPage", () => {
  it("renders relay name", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Friday Night SNES" }),
    ).toBeInTheDocument();
  });

  it("renders relay description", () => {
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
    expect(screen.getByTestId("relay-member-u1")).toBeInTheDocument();
    expect(screen.getByTestId("relay-member-u2")).toBeInTheDocument();
  });

  it("renders saves section", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Relay Saves" }),
    ).toBeInTheDocument();
    expect(screen.getByText("World 3 Save")).toBeInTheDocument();
  });

  it("shows delete button for relay owner", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: /Delete Relay/ }),
    ).toBeInTheDocument();
  });

  it("shows leave button when not owner", () => {
    mockUseRelay.mockReturnValue({
      data: { ...mockRelayDetail, ownerId: "u999" },
      isLoading: false,
    });
    renderPage();

    expect(
      screen.getByRole("button", { name: /Leave Relay/ }),
    ).toBeInTheDocument();
  });

  it("shows not found when relay is null", () => {
    mockUseRelay.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Relay not found")).toBeInTheDocument();
  });

  it("renders play button", () => {
    renderPage();

    expect(screen.getByTestId("relay-play-btn")).toBeInTheDocument();
  });

  it("shows owner badge on owner member", () => {
    renderPage();

    expect(screen.getByText("Owner")).toBeInTheDocument();
  });

  it("renders empty saves state when no saves", () => {
    mockUseRelaySaves.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("No saves yet")).toBeInTheDocument();
  });
});
