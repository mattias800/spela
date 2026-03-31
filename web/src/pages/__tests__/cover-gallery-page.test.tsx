import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { CoverGalleryPage } from "@/pages/cover-gallery-page";
import type { CoverGalleryResponse, Console } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useCoverGallery: vi.fn(),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

import { useCoverGallery } from "@/hooks/use-explore";
import { useConsoles } from "@/hooks/use-consoles";

const mockUseCoverGallery = useCoverGallery as ReturnType<typeof vi.fn>;
const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;

const mockCovers: CoverGalleryResponse = {
  covers: [
    {
      coverUrl: "/covers/mario.jpg",
      gameId: "game-1",
      gameTitle: "Super Mario World",
      consoleName: "SNES",
      consoleAbbreviation: "snes",
      consoleColor: "#805ad5",
      rating: 92,
      coverAspectRatio: 0.75,
    },
    {
      coverUrl: "/covers/zelda.jpg",
      gameId: "game-2",
      gameTitle: "A Link to the Past",
      consoleName: "SNES",
      consoleAbbreviation: "snes",
      consoleColor: "#805ad5",
      rating: 95,
      coverAspectRatio: 0.75,
    },
    {
      coverUrl: "/covers/sonic.jpg",
      gameId: "game-3",
      gameTitle: "Sonic the Hedgehog",
      consoleName: "Genesis",
      consoleAbbreviation: "gen",
      consoleColor: "#3b82f6",
      rating: 85,
      coverAspectRatio: 0.75,
    },
  ],
  page: 1,
  totalPages: 2,
  totalCount: 30,
};

const mockConsoles: Console[] = [
  {
    id: "snes",
    name: "SNES",
    abbreviation: "snes",
    extensions: [".sfc", ".smc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "#805ad5",
    generation: 4,
    iconUrl: "/icons/snes.png",
    logoUrl: "/logos/snes.png",
    gameCount: 50,
    saveStateSupport: true,
    browserPlayable: true,
    playable: true,
    code: "snes",
    maker: null,
    mediaType: null,
    releaseYear: null,
    unitsSold: null,
    summary: null,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
  },
  {
    id: "gen",
    name: "Genesis",
    abbreviation: "gen",
    extensions: [".md", ".gen"],
    defaultCore: "genesis_plus_gx",
    coverAspectRatio: 0.75,
    colorTheme: "#3b82f6",
    generation: 4,
    iconUrl: "/icons/gen.png",
    logoUrl: "/logos/gen.png",
    gameCount: 30,
    saveStateSupport: true,
    browserPlayable: true,
    playable: true,
    code: "gen",
    maker: null,
    mediaType: null,
    releaseYear: null,
    unitsSold: null,
    summary: null,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
  },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/explore/covers"]}>
        <Routes>
          <Route
            path="/explore/covers"
            element={<CoverGalleryPage />}
          />
          <Route path="/games/:id" element={<div>Game Detail</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("CoverGalleryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCoverGallery.mockReturnValue({
      data: mockCovers,
      isLoading: false,
    });
    mockUseConsoles.mockReturnValue({
      data: mockConsoles,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("cover-gallery-page")).toBeInTheDocument();
  });

  it("renders loading skeleton while loading", () => {
    mockUseCoverGallery.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(
      screen.getByTestId("cover-gallery-skeleton"),
    ).toBeInTheDocument();
  });

  it("renders cover art grid", () => {
    renderPage();
    expect(screen.getByTestId("cover-grid")).toBeInTheDocument();
    const cards = screen.getAllByTestId("cover-card");
    expect(cards).toHaveLength(3);
  });

  it("renders cover images", () => {
    renderPage();
    const images = screen.getAllByRole("img");
    expect(images.some((img) => img.getAttribute("src") === "/covers/mario.jpg")).toBe(true);
    expect(images.some((img) => img.getAttribute("src") === "/covers/zelda.jpg")).toBe(true);
  });

  it("renders hover overlay with game title", () => {
    renderPage();
    const overlays = screen.getAllByTestId("cover-overlay");
    expect(overlays[0]).toHaveTextContent("Super Mario World");
    expect(overlays[1]).toHaveTextContent("A Link to the Past");
  });

  it("navigates to game detail on click", () => {
    renderPage();
    const cards = screen.getAllByTestId("cover-card");
    expect(cards[0]).toHaveAttribute("href", "/games/game-1");
  });

  it("renders zoom controls", () => {
    renderPage();
    expect(screen.getByTestId("zoom-controls")).toBeInTheDocument();
    expect(screen.getByTestId("zoom-small")).toBeInTheDocument();
    expect(screen.getByTestId("zoom-medium")).toBeInTheDocument();
    expect(screen.getByTestId("zoom-large")).toBeInTheDocument();
  });

  it("zoom controls change grid layout", async () => {
    const user = userEvent.setup();
    renderPage();

    const grid = screen.getByTestId("cover-grid");

    // Default is medium
    expect(grid.className).toContain("grid-cols-4");

    // Click small zoom
    await user.click(screen.getByTestId("zoom-small"));
    expect(grid.className).toContain("grid-cols-5");

    // Click large zoom
    await user.click(screen.getByTestId("zoom-large"));
    expect(grid.className).toContain("grid-cols-3");
  });

  it("renders console filter chips", () => {
    renderPage();
    const chips = screen.getByTestId("console-chips");
    expect(chips).toBeInTheDocument();
    expect(chips).toHaveTextContent("All");
    expect(chips).toHaveTextContent("SNES");
    expect(chips).toHaveTextContent("GEN");
  });

  it("console filter triggers re-fetch", async () => {
    const user = userEvent.setup();
    renderPage();
    const chips = screen.getByTestId("console-chips");
    const buttons = chips.querySelectorAll("button");
    // First button is "All", second is "SNES", third is "GEN"
    const snesChip = buttons[1];
    await user.click(snesChip);
    expect(mockUseCoverGallery).toHaveBeenCalledWith(1, "snes");
  });

  it("renders empty state when no covers", () => {
    mockUseCoverGallery.mockReturnValue({
      data: { covers: [], page: 1, totalPages: 0, totalCount: 0 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No covers found")).toBeInTheDocument();
  });

  it("renders load more button when more pages", () => {
    renderPage();
    expect(screen.getByTestId("load-more-button")).toBeInTheDocument();
  });

  it("hides load more when on last page", () => {
    mockUseCoverGallery.mockReturnValue({
      data: {
        ...mockCovers,
        page: 2,
        totalPages: 2,
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("load-more-button"),
    ).not.toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /back to explore/i }),
    ).toBeInTheDocument();
  });

  it("renders page heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Cover Art Wall", level: 1 }),
    ).toBeInTheDocument();
  });
});
