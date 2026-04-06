import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ExploreWizardPage } from "@/pages/explore-wizard-page";
import type { WizardResponse, WizardResultsResponse } from "@/types/api";

// Mock IntersectionObserver for GameCard lazy loading
globalThis.IntersectionObserver = class MockIntersectionObserver {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
  takeRecords = vi.fn(() => [] as IntersectionObserverEntry[]);
  root = null;
  rootMargin = "";
  thresholds = [0];
  constructor() {}
} as unknown as typeof IntersectionObserver;

const mockWizardData: WizardResponse = {
  steps: [
    {
      step: 1,
      title: "What are you in the mood for?",
      type: "mood",
      options: [
        { id: "action", label: "Action & Excitement", description: "Fast-paced thrills" },
        { id: "chill", label: "Chill & Relaxing", description: "Laid-back vibes" },
      ],
    },
    {
      step: 2,
      title: "Pick an era",
      type: "era",
      options: [
        { id: "80s", label: "The 80s", description: "Birth of console gaming" },
        { id: "any", label: "Any Era", description: "Surprise me" },
      ],
    },
    {
      step: 3,
      title: "Refine your vibe",
      type: "vibe",
      options: [
        { id: "solo", label: "Solo Adventure", description: "Just me and the game" },
        { id: "any", label: "Anything Goes", description: "No preference" },
      ],
    },
  ],
};

const mockResults: WizardResultsResponse = {
  games: [
    {
      id: "g1",
      title: "Test Game 1",
      consoleId: "snes",
      consoleName: "SNES",
      fileName: "test.sfc",
      fileSize: 1024,
      discCount: 1,
      screenshotUrls: [],
      scrapeAttempts: 0,
      coverAspectRatio: 0.75,
      playable: true,
      isFavorite: false,
      isInPlayLater: false,
      averageRating: 0,
      ratingCount: 0,
      totalPlayTime: 0,
      createdAt: "2024-01-01",
      updatedAt: "2024-01-01",
      igdbCriticsRating: 85,
    },
  ],
  title: "Action-Packed Picks",
};

const mockUseWizardSteps = vi.fn();
const mockUseWizardResults = vi.fn();

vi.mock("@/hooks/use-explore", () => ({
  useWizardSteps: (...args: unknown[]) => mockUseWizardSteps(...args),
  useWizardResults: (...args: unknown[]) => mockUseWizardResults(...args),
}));

vi.mock("@/hooks/use-games", () => ({
  useToggleFavorite: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: () => ({ toggle: vi.fn() }),
}));

function renderComponent() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ExploreWizardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExploreWizardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseWizardSteps.mockReturnValue({
      data: mockWizardData,
      isLoading: false,
    });
    mockUseWizardResults.mockReturnValue({
      data: undefined,
      isLoading: false,
      refetch: vi.fn(),
    });
  });

  it("renders the wizard page with first step", () => {
    renderComponent();
    expect(screen.getByTestId("wizard-page")).toBeInTheDocument();
    expect(screen.getByText("Decision Wizard")).toBeInTheDocument();
    expect(
      screen.getByText("What are you in the mood for?"),
    ).toBeInTheDocument();
  });

  it("renders step options", () => {
    renderComponent();
    expect(screen.getByText("Action & Excitement")).toBeInTheDocument();
    expect(screen.getByText("Chill & Relaxing")).toBeInTheDocument();
  });

  it("advances to next step when option is selected", () => {
    renderComponent();
    fireEvent.click(screen.getByTestId("wizard-option-action"));
    expect(screen.getByText("Pick an era")).toBeInTheDocument();
  });

  it("shows progress bar with correct steps", () => {
    renderComponent();
    const progressBars = screen
      .getByLabelText("Wizard progress")
      .querySelectorAll("div");
    expect(progressBars).toHaveLength(3);
  });

  it("goes back to previous step", () => {
    renderComponent();
    fireEvent.click(screen.getByTestId("wizard-option-action"));
    expect(screen.getByText("Pick an era")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Back"));
    expect(
      screen.getByText("What are you in the mood for?"),
    ).toBeInTheDocument();
  });

  it("shows results after completing all steps", async () => {
    mockUseWizardResults.mockReturnValue({
      data: mockResults,
      isLoading: false,
      refetch: vi.fn(),
    });

    renderComponent();

    // Step 1
    fireEvent.click(screen.getByTestId("wizard-option-action"));
    // Step 2
    fireEvent.click(screen.getByTestId("wizard-option-80s"));
    // Step 3
    fireEvent.click(screen.getByTestId("wizard-option-solo"));

    await waitFor(() => {
      expect(screen.getByText("Action-Packed Picks")).toBeInTheDocument();
    });
  });

  it("shows empty state when no results", async () => {
    mockUseWizardResults.mockReturnValue({
      data: { games: [], title: "No Picks" },
      isLoading: false,
      refetch: vi.fn(),
    });

    renderComponent();
    fireEvent.click(screen.getByTestId("wizard-option-action"));
    fireEvent.click(screen.getByTestId("wizard-option-80s"));
    fireEvent.click(screen.getByTestId("wizard-option-solo"));

    await waitFor(() => {
      expect(screen.getByText("No games found")).toBeInTheDocument();
    });
  });

  it("shows skeleton when loading steps", () => {
    mockUseWizardSteps.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderComponent();
    expect(screen.queryByTestId("wizard-page")).not.toBeInTheDocument();
  });
});
