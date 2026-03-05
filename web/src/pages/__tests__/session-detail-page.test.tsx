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
} from "@/hooks/use-sessions";

const mockUseSession = useSession as ReturnType<typeof vi.fn>;
const mockUseSessionSaves = useSessionSaves as ReturnType<typeof vi.fn>;
const mockUseSessionCheats = useSessionCheats as ReturnType<typeof vi.fn>;
const mockUseUpdateSessionCheats = useUpdateSessionCheats as ReturnType<
  typeof vi.fn
>;

const mockSession = {
  id: "s1",
  gameId: "g1",
  name: "My Session",
  lastPlayedAt: "2026-02-01T10:00:00Z",
  lastPlayedByUsername: "admin",
  totalPlayTime: 3600,
  screenshotUrl: null,
  cheatsEnabled: false,
  memberCount: 1,
  memberAvatars: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
};

const mockSaves = [
  {
    id: "save1",
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
    id: "save2",
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

  it("shows delete session confirmation", async () => {
    renderPage();
    // The page-level Delete button (not the per-save "Delete save" buttons)
    const deleteButtons = screen.getAllByRole("button", { name: /Delete/i });
    const sessionDeleteBtn = deleteButtons.find(
      (btn) => btn.textContent?.trim() === "Delete",
    );
    expect(sessionDeleteBtn).toBeDefined();
    await userEvent.click(sessionDeleteBtn!);
    expect(
      screen.getByText(/Delete "My Session"/),
    ).toBeInTheDocument();
  });

  it("shows delete save confirmation when clicking delete on a save", async () => {
    renderPage();
    const deleteButtons = screen.getAllByRole("button", {
      name: /Delete save/i,
    });
    await userEvent.click(deleteButtons[0]);
    expect(
      screen.getByText(/Are you sure you want to delete this save state/i),
    ).toBeInTheDocument();
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
