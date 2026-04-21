import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GameSessions } from "../game-sessions";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "u1", username: "testuser", role: "user" },
  })),
}));

vi.mock("@/hooks/use-sessions", () => ({
  useGameSessions: vi.fn(),
  useCreateSession: vi.fn(),
  useRenameSession: vi.fn(),
  useDeleteSession: vi.fn(),
  useCloneSession: vi.fn(),
}));

import {
  useGameSessions,
  useCreateSession,
  useRenameSession,
  useDeleteSession,
  useCloneSession,
} from "@/hooks/use-sessions";

const mockUseGameSessions = useGameSessions as ReturnType<typeof vi.fn>;
const mockUseCreateSession = useCreateSession as ReturnType<typeof vi.fn>;
const mockUseRenameSession = useRenameSession as ReturnType<typeof vi.fn>;
const mockUseDeleteSession = useDeleteSession as ReturnType<typeof vi.fn>;
const mockUseCloneSession = useCloneSession as ReturnType<typeof vi.fn>;

const mockSessions = [
  {
    id: "ses-1",
    gameId: "g1",
    name: "Main Playthrough",
    lastPlayedAt: "2026-03-01T10:00:00Z",
    lastPlayedByUsername: "alice",
    totalPlayTime: 3600,
    screenshotUrl: null,
    cheatsEnabled: false,
    isSharedSession: false,
    memberCount: 1,
    memberUsernames: [],
    memberAvatars: [],
    createdAt: "2026-02-28T10:00:00Z",
    updatedAt: "2026-03-01T10:00:00Z",
  },
  {
    id: "ses-2",
    gameId: "g1",
    name: "Cheat Run",
    lastPlayedAt: null,
    lastPlayedByUsername: null,
    totalPlayTime: 0,
    screenshotUrl: null,
    cheatsEnabled: true,
    isSharedSession: false,
    memberCount: 2,
    memberUsernames: [],
    memberAvatars: ["https://example.com/a1.png", "https://example.com/a2.png"],
    createdAt: "2026-03-02T10:00:00Z",
    updatedAt: "2026-03-02T10:00:00Z",
  },
];

const mockMutate = vi.fn();

function renderComponent(gameId = "g1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <GameSessions gameId={gameId} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseCreateSession.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
  });
  mockUseRenameSession.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
  });
  mockUseDeleteSession.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
    variables: undefined,
  });
  mockUseCloneSession.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
    variables: undefined,
  });
});

describe("GameSessions", () => {
  it("renders loading skeleton", () => {
    mockUseGameSessions.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderComponent();

    expect(screen.getByText("Sessions")).toBeInTheDocument();
  });

  it("renders empty state when no sessions", () => {
    mockUseGameSessions.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderComponent();

    expect(screen.getByText("No sessions yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Start a new session to track your progress separately.",
      ),
    ).toBeInTheDocument();
  });

  it("renders session cards", () => {
    mockUseGameSessions.mockReturnValue({
      data: mockSessions,
      isLoading: false,
    });
    renderComponent();

    expect(screen.getByText("Main Playthrough")).toBeInTheDocument();
    expect(screen.getByText("Cheat Run")).toBeInTheDocument();
    expect(screen.getByText("(2)")).toBeInTheDocument();
  });

  it("shows new session form when button clicked", () => {
    mockUseGameSessions.mockReturnValue({
      data: mockSessions,
      isLoading: false,
    });
    renderComponent();

    fireEvent.click(screen.getByText("New Session"));

    expect(screen.getByTestId("new-session-form")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("Session name (optional)"),
    ).toBeInTheDocument();
  });

  it("calls create mutation when form submitted", () => {
    mockUseGameSessions.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderComponent();

    fireEvent.click(screen.getByText("New Session"));

    const input = screen.getByPlaceholderText("Session name (optional)");
    fireEvent.change(input, { target: { value: "Speed Run" } });
    fireEvent.click(screen.getByText("Create"));

    expect(mockMutate).toHaveBeenCalledWith(
      { gameId: "g1", name: "Speed Run" },
      expect.any(Object),
    );
  });

  it("uses default name when creating with empty input", () => {
    mockUseGameSessions.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderComponent();

    fireEvent.click(screen.getByText("New Session"));
    fireEvent.click(screen.getByText("Create"));

    expect(mockMutate).toHaveBeenCalledWith(
      { gameId: "g1", name: "Session 1" },
      expect.any(Object),
    );
  });

  it("hides new session form when cancelled", () => {
    mockUseGameSessions.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderComponent();

    fireEvent.click(screen.getByText("New Session"));
    expect(screen.getByTestId("new-session-form")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Cancel"));
    expect(screen.queryByTestId("new-session-form")).not.toBeInTheDocument();
  });

  it("shows continue button on each session", () => {
    mockUseGameSessions.mockReturnValue({
      data: [mockSessions[0]],
      isLoading: false,
    });
    renderComponent();

    expect(screen.getByText("Play")).toBeInTheDocument();
  });

  it("navigates to play route with session ID when Play clicked", () => {
    mockUseGameSessions.mockReturnValue({
      data: [mockSessions[0]],
      isLoading: false,
    });
    renderComponent();

    fireEvent.click(screen.getByText("Play"));

    expect(mockNavigate).toHaveBeenCalledWith("/games/g1/play/ses-1");
  });

  it("shows Current badge on first session only", () => {
    mockUseGameSessions.mockReturnValue({
      data: mockSessions,
      isLoading: false,
    });
    renderComponent();

    const currentBadges = screen.getAllByText("Current");
    expect(currentBadges).toHaveLength(1);
  });

  it("opens the clone dialog from the first card's actions menu", () => {
    mockUseGameSessions.mockReturnValue({
      data: [mockSessions[0]],
      isLoading: false,
    });
    renderComponent();

    // Dialog must not be mounted before the user triggers clone — the
    // name input should be absent.
    expect(
      screen.queryByTestId("clone-session-dialog"),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("actions-menu-btn"));
    fireEvent.click(screen.getByRole("menuitem", { name: /clone session/i }));

    expect(screen.getByTestId("clone-session-dialog")).toBeInTheDocument();
    expect(screen.getByTestId("clone-session-name-input")).toHaveValue(
      "Main Playthrough (Copy)",
    );
  });

  it("fires the clone mutation with the edited name when confirmed", () => {
    mockUseGameSessions.mockReturnValue({
      data: [mockSessions[0]],
      isLoading: false,
    });
    renderComponent();

    fireEvent.click(screen.getByTestId("actions-menu-btn"));
    fireEvent.click(screen.getByRole("menuitem", { name: /clone session/i }));

    const input = screen.getByTestId("clone-session-name-input");
    fireEvent.change(input, { target: { value: "Boss attempt" } });
    fireEvent.click(screen.getByTestId("clone-session-confirm"));

    expect(mockMutate).toHaveBeenCalledWith(
      { id: "ses-1", gameId: "g1", name: "Boss attempt" },
      expect.any(Object),
    );
  });

  it("keeps clone behind the `…` menu — no standalone Clone CTA (#553)", () => {
    mockUseGameSessions.mockReturnValue({
      data: [mockSessions[0]],
      isLoading: false,
    });
    renderComponent();

    expect(
      screen.queryByRole("button", { name: /^clone session$/i }),
    ).not.toBeInTheDocument();
  });
});
