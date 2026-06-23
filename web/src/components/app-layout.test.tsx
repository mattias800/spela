import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";

// AppLayout fans out to many hooks; stub them all so the test stays focused on
// whether the "Connected Servers" nav item is gated on connected-server content.
vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { username: "Tester" },
    logout: vi.fn(),
    isAdmin: false,
  })),
}));
vi.mock("@/hooks/use-game-scraped-listener", () => ({
  useGameScrapedListener: vi.fn(),
}));
vi.mock("@/hooks/use-shared-sessions", () => ({
  usePendingInvitationCount: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/hooks/use-netplay", () => ({
  usePendingNetplayInviteCount: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/hooks/use-notifications", () => ({
  useNotifications: vi.fn(),
}));
vi.mock("@/hooks/use-bios", () => ({
  useBiosStatus: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/hooks/use-admin", () => ({
  useIgdbStatus: vi.fn(() => ({ data: { configured: true } })),
}));
vi.mock("@/hooks/use-health", () => ({
  useHealth: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/hooks/use-connected-servers", () => ({
  useConnectedServerConsoles: vi.fn(),
}));
vi.mock("@/features/search/components/search-palette", () => ({
  SearchPalette: () => null,
}));

import { useConnectedServerConsoles } from "@/hooks/use-connected-servers";
import { AppLayout } from "./app-layout";

const mockConnected = useConnectedServerConsoles as ReturnType<typeof vi.fn>;

function renderLayout() {
  return render(
    <MemoryRouter>
      <AppLayout />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("AppLayout — Connected Servers nav item", () => {
  // The Sidebar renders its content twice (desktop aside + mobile drawer), so a
  // present label appears more than once — assert on the count, not a single node.
  it("hides the item when there are no connected-server consoles", () => {
    mockConnected.mockReturnValue({ data: [] });
    renderLayout();
    expect(screen.queryAllByText("Connected Servers")).toHaveLength(0);
  });

  it("hides the item while the connected-server query is still loading", () => {
    mockConnected.mockReturnValue({ data: undefined });
    renderLayout();
    expect(screen.queryAllByText("Connected Servers")).toHaveLength(0);
  });

  it("shows the item once a connected server shares games", () => {
    mockConnected.mockReturnValue({ data: [{ console: "SNES", count: 3 }] });
    renderLayout();
    expect(screen.getAllByText("Connected Servers").length).toBeGreaterThan(0);
  });
});
