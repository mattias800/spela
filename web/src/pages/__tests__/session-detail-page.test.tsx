import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { SessionDetailPage } from "../session-detail-page";

vi.mock("@/hooks/use-sessions", () => ({
  useSession: vi.fn(),
  useSessionSaves: vi.fn(),
  useSessionCheats: vi.fn(),
  useUpdateSessionCheats: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useDeleteSessionSave: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
  useDeleteSession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
    variables: null,
  })),
  useRenameSession: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCloneSession: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
    variables: null,
  })),
}));

vi.mock("@/hooks/use-games", () => ({
  useGame: vi.fn(() => ({
    data: { id: "g1", title: "Super Mario World" },
    isLoading: false,
  })),
}));

vi.mock("@/hooks/use-cheats", () => ({
  useGameCheats: vi.fn(() => ({
    data: [
      { index: 0, description: "Infinite Lives", code: "7E0DBE:09" },
      { index: 1, description: "Infinite Coins", code: "7E0DC0:63" },
      { index: 2, description: "Moon Jump", code: "7E13E0:20" },
    ],
    isLoading: false,
  })),
}));

import {
  useSession,
  useSessionSaves,
  useSessionCheats,
  useUpdateSessionCheats,
  useCloneSession,
} from "@/hooks/use-sessions";

const mockUseSession = useSession as ReturnType<typeof vi.fn>;
const mockUseSessionSaves = useSessionSaves as ReturnType<typeof vi.fn>;
const mockUseSessionCheats = useSessionCheats as ReturnType<typeof vi.fn>;
const mockUseUpdateSessionCheats = useUpdateSessionCheats as ReturnType<
  typeof vi.fn
>;
const mockUseCloneSession = useCloneSession as ReturnType<typeof vi.fn>;

const mockSession = {
  id: "s1",
  gameId: "g1",
  name: "My Session",
  lastPlayedAt: "2026-02-01T10:00:00Z",
  lastPlayedByUsername: "admin",
  totalPlayTime: 3600,
  screenshotUrl: null,
  cheatsEnabled: false,
  isSharedSession: false,
  memberCount: 1,
  memberUsernames: [],
  memberAvatars: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
};

// Save IDs are stringified uints on the wire (see server response.go
// SessionSaveResponse.ID). The useCloneSession hook parses them back to
// numbers for the ?saveId= query, so the fixtures must use numeric-
// strings to exercise that path correctly.
const mockSaves = [
  {
    id: "101",
    sessionId: "s1",
    name: "Checkpoint 1",
    fileSize: 32768,
    screenshotUrl: null,
    isAuto: false,
    isCurrent: true,
    coreName: "snes9x",
    notes: null,
    slot: 0,
    createdAt: "2026-02-01T09:00:00Z",
  },
  {
    id: "102",
    sessionId: "s1",
    name: "Auto Save",
    fileSize: 32768,
    screenshotUrl: null,
    isAuto: true,
    isCurrent: false,
    coreName: "snes9x",
    notes: "Automatic save",
    slot: null,
    createdAt: "2026-02-01T10:00:00Z",
  },
];

const mockCheats = {
  cheatsEnabled: false,
  enabledIndices: [],
};

function renderPage(sessionId = "s1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/sessions/${sessionId}`]}>
        <Routes>
          <Route
            path="sessions/:sessionId"
            element={<SessionDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSession.mockReturnValue({
    data: mockSession,
    isLoading: false,
  });
  mockUseSessionSaves.mockReturnValue({
    data: mockSaves,
    isLoading: false,
  });
  mockUseSessionCheats.mockReturnValue({
    data: mockCheats,
    isLoading: false,
  });
  mockUseUpdateSessionCheats.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
});

describe("SessionDetailPage", () => {
  it("renders session name and game title", () => {
    renderPage();
    expect(screen.getByText("My Session")).toBeInTheDocument();
    const gameLinks = screen.getAllByText("Super Mario World");
    expect(gameLinks.length).toBeGreaterThanOrEqual(1);
  });

  it("renders save states list", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Save States/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("Checkpoint 1")).toBeInTheDocument();
    expect(screen.getByText("Auto Save")).toBeInTheDocument();
  });

  it("displays auto badge on auto saves", () => {
    renderPage();
    expect(screen.getByText("Auto")).toBeInTheDocument();
  });

  it("displays current badge on current save", () => {
    renderPage();
    expect(screen.getByText("Current")).toBeInTheDocument();
  });

  it("renders cheats section with toggle", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Cheats/i }),
    ).toBeInTheDocument();
    const toggle = screen.getByLabelText("Enable cheats");
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-checked", "false");
  });

  it("cheats toggle shows enabled state", () => {
    mockUseSessionCheats.mockReturnValue({
      data: { cheatsEnabled: true, enabledIndices: [0, 2] },
      isLoading: false,
    });
    renderPage();
    const toggle = screen.getByLabelText("Enable cheats");
    expect(toggle).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("2 enabled")).toBeInTheDocument();
  });

  it("calls updateCheats when toggle is clicked", async () => {
    const mutateFn = vi.fn();
    mockUseUpdateSessionCheats.mockReturnValue({
      mutate: mutateFn,
      isPending: false,
    });
    renderPage();
    await userEvent.click(screen.getByLabelText("Enable cheats"));
    expect(mutateFn).toHaveBeenCalledWith({
      sessionId: "s1",
      cheatsEnabled: true,
      enabledIndices: [],
    });
  });

  it("shows delete session confirmation from the header `…` menu", async () => {
    renderPage();
    // The header actions are behind the `…` menu; open it, then click
    // the Delete session item.
    const headerMenu = screen.getByTestId("session-header-actions");
    await userEvent.click(
      headerMenu.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /delete session/i }),
    );
    expect(screen.getByText(/Delete "My Session"/)).toBeInTheDocument();
  });

  it("shows delete save confirmation from the per-save `…` menu", async () => {
    renderPage();
    const saveActions = screen.getByTestId("save-actions-101");
    await userEvent.click(
      saveActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /delete save/i }),
    );
    expect(
      screen.getByText(/Are you sure you want to delete this save state/i),
    ).toBeInTheDocument();
  });

  it("opens the clone dialog from the header `…` menu (US-2)", async () => {
    renderPage();
    const headerMenu = screen.getByTestId("session-header-actions");
    await userEvent.click(
      headerMenu.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /clone session/i }),
    );
    expect(screen.getByTestId("clone-session-dialog")).toBeInTheDocument();
    expect(screen.getByTestId("clone-session-name-input")).toHaveValue(
      "My Session (Copy)",
    );
  });

  it("opens the clone dialog from a specific save's `…` menu (US-3)", async () => {
    renderPage();
    const saveActions = screen.getByTestId("save-actions-102");
    await userEvent.click(
      saveActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /clone from this save/i }),
    );
    expect(screen.getByTestId("clone-session-dialog")).toBeInTheDocument();
    expect(
      screen.getByText(/Cloning from save "Auto Save"/),
    ).toBeInTheDocument();
  });

  it("calls clone mutation with saveId when confirming a clone-from-save", async () => {
    const cloneMutate = vi.fn();
    mockUseCloneSession.mockReturnValue({
      mutate: cloneMutate,
      isPending: false,
      variables: null,
    });
    renderPage();
    const saveActions = screen.getByTestId("save-actions-102");
    await userEvent.click(
      saveActions.querySelector("[data-testid='actions-menu-btn']")!,
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: /clone from this save/i }),
    );
    await userEvent.click(screen.getByTestId("clone-session-confirm"));
    expect(cloneMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "s1",
        gameId: "g1",
        saveId: 102, // wire-format save ID (stringified uint) parsed back to number.
        name: "My Session (Copy)",
      }),
      expect.any(Object),
    );
  });

  it("does not expose a standalone Clone button outside the `…` menu (#553)", () => {
    renderPage();
    // Close-state check: clone is only reachable via the menu items
    // opened above. There must be no direct button anywhere.
    expect(
      screen.queryByRole("button", { name: /^clone session$/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^clone from this save$/i }),
    ).not.toBeInTheDocument();
  });

  it("shows loading state", () => {
    mockUseSession.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows not-found state for invalid session", () => {
    mockUseSession.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("invalid");
    expect(screen.getByText(/Session not found/i)).toBeInTheDocument();
  });

  it("shows empty saves state", () => {
    mockUseSessionSaves.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(
      screen.getByText(/No save states yet/i),
    ).toBeInTheDocument();
  });

  it("shows cheats disabled message when cheats are off", () => {
    renderPage();
    expect(
      screen.getByText(/Cheats are disabled for this session\. 3 cheats available\./i),
    ).toBeInTheDocument();
  });
});
