import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ScreenshotGalleryPage } from "@/pages/screenshot-gallery-page";
import type { ScreenshotGalleryResponse, Console } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useScreenshotGallery: vi.fn(),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

import { useScreenshotGallery } from "@/hooks/use-explore";
import { useConsoles } from "@/hooks/use-consoles";

const mockUseScreenshotGallery = useScreenshotGallery as ReturnType<
  typeof vi.fn
>;
const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;

const mockScreenshots: ScreenshotGalleryResponse = {
  screenshots: [
    {
      url: "/screenshots/mario1.jpg",
      gameId: "game-1",
      gameTitle: "Super Mario World",
      consoleName: "SNES",
      consoleAbbreviation: "snes",
      consoleColor: "#805ad5",
    },
    {
      url: "/screenshots/zelda1.jpg",
      gameId: "game-2",
      gameTitle: "A Link to the Past",
      consoleName: "SNES",
      consoleAbbreviation: "snes",
      consoleColor: "#805ad5",
    },
    {
      url: "/screenshots/mega1.jpg",
      gameId: "game-3",
      gameTitle: "Mega Man X",
      consoleName: "SNES",
      consoleAbbreviation: "snes",
      consoleColor: "#805ad5",
    },
  ],
  page: 1,
  totalPages: 3,
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
    logoAspectRatio: null,
    colorTheme: "#805ad5",
    generation: 4,
    iconUrl: "/icons/snes.png",
    logoUrl: "/logos/snes.png",
    gameCount: 50,
    saveStateSupport: true,
  saveStatePolicy: "small",
    browserPlayable: true,
    playable: true,
    code: "snes",
    emulatorJsCore: "",
    logoPngUrl: "",
    photoUrl: null,
    tag: null,
    maker: { code: "", name: "" },
    mediaType: { code: "", name: "", category: { code: "", name: "" } },
    releaseYear: null,
    unitsSold: null,
    summary: null,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
  },
  {
    id: "nes",
    name: "NES",
    abbreviation: "nes",
    extensions: [".nes"],
    defaultCore: "fceumm",
    coverAspectRatio: 0.75,
    logoAspectRatio: null,
    colorTheme: "#e53e3e",
    generation: 3,
    iconUrl: "/icons/nes.png",
    logoUrl: "/logos/nes.png",
    gameCount: 30,
    saveStateSupport: true,
  saveStatePolicy: "small",
    browserPlayable: true,
    playable: true,
    code: "nes",
    emulatorJsCore: "",
    logoPngUrl: "",
    photoUrl: null,
    tag: null,
    maker: { code: "", name: "" },
    mediaType: { code: "", name: "", category: { code: "", name: "" } },
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
      <MemoryRouter initialEntries={["/explore/gallery"]}>
        <Routes>
          <Route
            path="/explore/gallery"
            element={<ScreenshotGalleryPage />}
          />
          <Route path="/games/:id" element={<div>Game Detail</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ScreenshotGalleryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseScreenshotGallery.mockReturnValue({
      data: mockScreenshots,
      isLoading: false,
    });
    mockUseConsoles.mockReturnValue({
      data: mockConsoles,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(
      screen.getByTestId("screenshot-gallery-page"),
    ).toBeInTheDocument();
  });

  it("renders loading skeleton while loading", () => {
    mockUseScreenshotGallery.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(
      screen.getByTestId("screenshot-gallery-skeleton"),
    ).toBeInTheDocument();
  });

  it("renders screenshots in gallery", () => {
    renderPage();
    expect(screen.getByTestId("screenshot-grid")).toBeInTheDocument();
    const cards = screen.getAllByTestId("screenshot-card");
    expect(cards).toHaveLength(3);
  });

  it("renders screenshot images", () => {
    renderPage();
    const images = screen.getAllByRole("img");
    expect(images.some((img) => img.getAttribute("src") === "/screenshots/mario1.jpg")).toBe(true);
    expect(images.some((img) => img.getAttribute("src") === "/screenshots/zelda1.jpg")).toBe(true);
  });

  it("renders hover overlay with game title", () => {
    renderPage();
    const overlays = screen.getAllByTestId("screenshot-overlay");
    expect(overlays).toHaveLength(3);
    expect(overlays[0]).toHaveTextContent("Super Mario World");
    expect(overlays[1]).toHaveTextContent("A Link to the Past");
  });

  it("renders hover overlay with console badge", () => {
    renderPage();
    const overlays = screen.getAllByTestId("screenshot-overlay");
    expect(overlays[0]).toHaveTextContent("SNES");
  });

  it("navigates to game detail on click", () => {
    renderPage();
    const cards = screen.getAllByTestId("screenshot-card");
    const link = cards[0].querySelector("a");
    expect(link).toHaveAttribute("href", "/games/game-1");
  });

  it("renders console filter dropdown", () => {
    renderPage();
    expect(screen.getByTestId("console-filter")).toBeInTheDocument();
    const select = screen.getByTestId("console-filter");
    expect(select).toHaveTextContent("All Consoles");
    expect(select).toHaveTextContent("SNES");
    expect(select).toHaveTextContent("NES");
  });

  it("console filter triggers re-fetch", async () => {
    const user = userEvent.setup();
    renderPage();
    const select = screen.getByTestId("console-filter");
    await user.selectOptions(select, "snes");
    // The hook should be called with the new filter
    expect(mockUseScreenshotGallery).toHaveBeenCalledWith(1, {
      console: "snes",
      genre: undefined,
    });
  });

  it("renders empty state when no screenshots", () => {
    mockUseScreenshotGallery.mockReturnValue({
      data: { screenshots: [], page: 1, totalPages: 0, totalCount: 0 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No screenshots found")).toBeInTheDocument();
  });

  it("renders load more button when more pages", () => {
    renderPage();
    expect(screen.getByTestId("load-more-button")).toBeInTheDocument();
    expect(screen.getByTestId("load-more-button")).toHaveTextContent(
      "Load More",
    );
  });

  it("hides load more when on last page", () => {
    mockUseScreenshotGallery.mockReturnValue({
      data: {
        ...mockScreenshots,
        page: 3,
        totalPages: 3,
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("load-more-button")).not.toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByTestId("page-back-button"),
    ).toBeInTheDocument();
  });

  it("renders page heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Screenshot Gallery", level: 1 }),
    ).toBeInTheDocument();
  });
});
