import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { RelaysPage } from "../relays-page";

vi.mock("@/hooks/use-relays", () => ({
  useMyRelays: vi.fn(),
  useRelayInvitations: vi.fn(),
  useAcceptRelayInvitation: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useRejectRelayInvitation: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useRelayRealtime: vi.fn(),
  useCreateRelay: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
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
  useMyRelays,
  useRelayInvitations,
} from "@/hooks/use-relays";

const mockUseMyRelays = useMyRelays as ReturnType<typeof vi.fn>;
const mockUseRelayInvitations = useRelayInvitations as ReturnType<typeof vi.fn>;

const mockRelays = {
  data: [
    {
      id: "relay-1",
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

const mockInvitations = {
  data: [
    {
      id: "inv-1",
      relayId: "relay-2",
      relayName: "RPG Club",
      gameId: "g2",
      gameTitle: "Chrono Trigger",
      gameCoverUrl: "https://example.com/chrono.png",
      gameConsoleName: "SNES",
      inviterUsername: "bob",
      createdAt: "2026-02-13T10:00:00Z",
    },
  ],
  total: 1,
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RelaysPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseMyRelays.mockReturnValue({ data: mockRelays, isLoading: false });
  mockUseRelayInvitations.mockReturnValue({
    data: mockInvitations,
    isLoading: false,
  });
});

describe("RelaysPage", () => {
  it("renders heading and description", () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Relays", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Take turns playing games with friends using shared save states.",
      ),
    ).toBeInTheDocument();
  });

  it("renders My Relays tab by default", () => {
    renderPage();

    expect(screen.getByText("Friday Night SNES")).toBeInTheDocument();
  });

  it("renders Create Relay button", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: /Create Relay/ }),
    ).toBeInTheDocument();
  });

  it("shows empty state when no relays", () => {
    mockUseMyRelays.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 24 },
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("No relays yet")).toBeInTheDocument();
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
      screen.getByTestId("relay-invitation-inv-1"),
    ).toBeInTheDocument();
  });

  it("shows empty state when no invitations", async () => {
    mockUseRelayInvitations.mockReturnValue({
      data: { data: [], total: 0 },
      isLoading: false,
    });
    renderPage();

    await userEvent.click(screen.getByText("Invitations"));

    expect(screen.getByText("No invitations")).toBeInTheDocument();
  });

  it("renders loading skeletons when loading", () => {
    mockUseMyRelays.mockReturnValue({ data: undefined, isLoading: true });
    const { container } = renderPage();

    // Skeleton elements should be present (animation classes)
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
});
