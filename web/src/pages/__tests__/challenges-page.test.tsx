import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ChallengesPage } from "../challenges-page";

vi.mock("@/hooks/use-challenges", () => ({
  useChallenges: vi.fn(),
  useMyChallenges: vi.fn(),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

import { useChallenges, useMyChallenges } from "@/hooks/use-challenges";
import { useConsoles } from "@/hooks/use-consoles";

const mockUseChallenges = useChallenges as ReturnType<typeof vi.fn>;
const mockUseMyChallenges = useMyChallenges as ReturnType<typeof vi.fn>;
const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;

// ---------------------------------------------------------------------------
// Mock Data
// ---------------------------------------------------------------------------

const mockChallengesData = {
  data: [
    {
      id: "1",
      creatorId: "u1",
      creatorUsername: "alice",
      gameId: "g1",
      gameTitle: "Super Mario Bros.",
      gameCoverUrl: "",
      gameConsoleName: "NES",
      name: "Speed Run World 1",
      description: "Complete World 1 as fast as possible!",
      type: "speedrun",
      difficulty: "medium",
      status: "active",
      screenshotUrl: "/api/challenges/1/screenshot",
      coreName: "fceumm",
      saveFileSize: 1024,
      attemptCount: 12,
      completionCount: 8,
      expiresAt: null,
      createdAt: "2026-02-01T10:00:00Z",
      updatedAt: "2026-02-01T10:00:00Z",
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

const mockConsoles = [
  {
    id: "1",
    name: "NES",
    abbreviation: "nes",
    extensions: [".nes"],
    defaultCore: "fceumm",
    coverAspectRatio: 0.75,
    colorTheme: "red",
    generation: 3,
    iconUrl: "",
    gameCount: 5,
    saveStateSupport: true,
    browserPlayable: true,
    createdAt: "",
    updatedAt: "",
  },
  {
    id: "2",
    name: "SNES",
    abbreviation: "snes",
    extensions: [".sfc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "purple",
    generation: 4,
    iconUrl: "",
    gameCount: 3,
    saveStateSupport: true,
    browserPlayable: true,
    createdAt: "",
    updatedAt: "",
  },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ChallengesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseChallenges.mockReturnValue({
    data: mockChallengesData,
    isLoading: false,
  });
  mockUseMyChallenges.mockReturnValue({
    data: { data: [], total: 0, page: 1, pageSize: 20 },
    isLoading: false,
  });
  mockUseConsoles.mockReturnValue({ data: mockConsoles });
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ChallengesPage", () => {
  it("renders page heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Challenges/i, level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders challenge cards when data is loaded", () => {
    renderPage();
    expect(screen.getByText("Speed Run World 1")).toBeInTheDocument();
  });

  it("shows empty state when no challenges", () => {
    mockUseChallenges.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 20 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No challenges yet")).toBeInTheDocument();
  });

  it("renders loading skeletons when loading", () => {
    mockUseChallenges.mockReturnValue({ data: undefined, isLoading: true });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders tabs: Popular, Recent, My Challenges", () => {
    renderPage();
    expect(screen.getByText("Popular")).toBeInTheDocument();
    expect(screen.getByText("Recent")).toBeInTheDocument();
    expect(screen.getByText("My Challenges")).toBeInTheDocument();
  });

  it("switches to My Challenges tab", async () => {
    renderPage();
    await userEvent.click(screen.getByText("My Challenges"));
    // After switching tab, the My Challenges data should be displayed
    // Since mock returns empty, we should see the "No challenges created" state
    expect(screen.getByText("No challenges created")).toBeInTheDocument();
  });

  it("renders difficulty filter options", () => {
    renderPage();
    expect(screen.getByDisplayValue("All Difficulties")).toBeInTheDocument();
  });

  it("renders console filter dropdown", () => {
    renderPage();
    expect(screen.getByDisplayValue("All Consoles")).toBeInTheDocument();
  });
});
