import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ScrapeMatchModal } from "../scrape-match-modal";

const mockMutate = vi.fn();
const mockToast = vi.fn();

vi.mock("@/hooks/use-admin", () => ({
  useIgdbSearch: vi.fn(() => ({
    data: undefined,
    isLoading: false,
    isError: false,
  })),
  useApplyIgdbMatch: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
  useIgdbStatus: vi.fn(() => ({
    data: { configured: true, status: "connected", source: "database" },
  })),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: mockToast })),
  };
});

import { useIgdbSearch, useApplyIgdbMatch, useIgdbStatus } from "@/hooks/use-admin";

const mockUseIgdbSearch = useIgdbSearch as ReturnType<typeof vi.fn>;
const mockUseApplyIgdbMatch = useApplyIgdbMatch as ReturnType<typeof vi.fn>;
const mockUseIgdbStatus = useIgdbStatus as ReturnType<typeof vi.fn>;

const mockSearchResults = [
  {
    igdbId: 1234,
    name: "Super Mario Bros.",
    coverUrl: "https://images.igdb.com/cover.jpg",
    releaseYear: 1985,
    developer: "Nintendo",
    summary: "A classic platformer",
  },
  {
    igdbId: 5678,
    name: "Super Mario Bros. 2",
    coverUrl: "https://images.igdb.com/cover2.jpg",
    releaseYear: 1988,
    developer: "Nintendo",
    summary: "The sequel",
  },
];

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();

  mockUseIgdbSearch.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
  });
  mockUseApplyIgdbMatch.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
  });
  mockUseIgdbStatus.mockReturnValue({
    data: { configured: true, status: "connected", source: "database" },
  });
});

const defaultProps = {
  gameId: "game-1",
  currentTitle: "Super Mario Bros",
  open: true,
  onClose: vi.fn(),
};

describe("ScrapeMatchModal", () => {
  it("renders modal with title when open", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(
      screen.getByRole("dialog", { name: "Fix Scrape Match" }),
    ).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} open={false} />
      </Wrapper>,
    );

    expect(
      screen.queryByRole("dialog", { name: "Fix Scrape Match" }),
    ).not.toBeInTheDocument();
  });

  it("pre-fills search input with current game title", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    const input = screen.getByPlaceholderText("Search IGDB...");
    expect(input).toHaveValue("Super Mario Bros");
  });

  it("displays search results", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: mockSearchResults,
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("Super Mario Bros. 2")).toBeInTheDocument();
    expect(screen.getByText("1985")).toBeInTheDocument();
    expect(screen.getByText("1988")).toBeInTheDocument();
  });

  it("displays developer and summary for results", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: mockSearchResults,
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(screen.getAllByText("Nintendo")).toHaveLength(2);
    expect(screen.getByText("A classic platformer")).toBeInTheDocument();
    expect(screen.getByText("The sequel")).toBeInTheDocument();
  });

  it("shows loading skeletons when searching", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    const resultsContainer = screen.getByTestId("igdb-search-results");
    expect(resultsContainer).toBeInTheDocument();
    // Skeleton renders 5 placeholder rows
    expect(
      resultsContainer.querySelectorAll("[class*='animate-pulse']").length,
    ).toBeGreaterThan(0);
  });

  it("shows error message when search fails", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(
      screen.getByText("Failed to search IGDB. Please try again."),
    ).toBeInTheDocument();
  });

  it("shows empty state when no results found", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(
      screen.getByText("No results found on IGDB"),
    ).toBeInTheDocument();
  });

  it("highlights current match with badge", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: mockSearchResults,
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal
          {...defaultProps}
          currentScraperId="igdb:1234"
        />
      </Wrapper>,
    );

    expect(screen.getByText("Current")).toBeInTheDocument();
    // The current match button should be disabled
    const currentMatchBtn = screen.getByTestId("igdb-result-1234");
    expect(currentMatchBtn).toBeDisabled();
  });

  it("calls applyMatch.mutate when a result is selected", async () => {
    mockUseIgdbSearch.mockReturnValue({
      data: mockSearchResults,
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    await userEvent.click(screen.getByTestId("igdb-result-1234"));

    expect(mockMutate).toHaveBeenCalledWith(
      { gameId: "game-1", igdbId: 1234 },
      expect.objectContaining({
        onSuccess: expect.any(Function),
        onError: expect.any(Function),
      }),
    );
  });

  it("disables result buttons while applying match", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: mockSearchResults,
      isLoading: false,
      isError: false,
    });
    mockUseApplyIgdbMatch.mockReturnValue({
      mutate: mockMutate,
      isPending: true,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(screen.getByTestId("igdb-result-1234")).toBeDisabled();
    expect(screen.getByTestId("igdb-result-5678")).toBeDisabled();
  });

  it("shows IGDB not configured warning when status is not connected", () => {
    mockUseIgdbStatus.mockReturnValue({
      data: { configured: false, status: "not_configured", source: "none" },
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(
      screen.getByText(/IGDB is not configured/),
    ).toBeInTheDocument();
  });

  it("does not show warning when IGDB is connected", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(
      screen.queryByText(/IGDB is not configured/),
    ).not.toBeInTheDocument();
  });

  it("shows result cover images", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: mockSearchResults,
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    const images = screen.getAllByRole("img");
    expect(images).toHaveLength(2);
    expect(images[0]).toHaveAttribute(
      "src",
      "https://images.igdb.com/cover.jpg",
    );
  });

  it("shows fallback initial when result has no cover", () => {
    mockUseIgdbSearch.mockReturnValue({
      data: [
        {
          igdbId: 999,
          name: "Zelda",
          releaseYear: 1986,
        },
      ],
      isLoading: false,
      isError: false,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ScrapeMatchModal {...defaultProps} />
      </Wrapper>,
    );

    expect(screen.getByText("Z")).toBeInTheDocument();
  });
});
