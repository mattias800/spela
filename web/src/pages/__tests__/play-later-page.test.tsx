import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { PlayLaterPage } from "../play-later-page";

vi.mock("@/hooks/use-play-later", () => ({
  usePlayLaterGames: vi.fn(),
  useRemoveFromPlayLater: vi.fn(() => ({ mutate: vi.fn() })),
  useReorderPlayLater: vi.fn(() => ({ mutate: vi.fn() })),
}));

import {
  usePlayLaterGames,
  useRemoveFromPlayLater,
  useReorderPlayLater,
} from "@/hooks/use-play-later";

const mockUsePlayLaterGames = usePlayLaterGames as ReturnType<typeof vi.fn>;
const mockUseRemoveFromPlayLater = useRemoveFromPlayLater as ReturnType<
  typeof vi.fn
>;
const mockUseReorderPlayLater = useReorderPlayLater as ReturnType<typeof vi.fn>;

const mockGames = [
  {
    id: "g1",
    title: "Super Mario Bros.",
    consoleId: "1",
    consoleName: "NES",
    coverUrl: "",
    isInPlayLater: true,
  },
  {
    id: "g2",
    title: "The Legend of Zelda",
    consoleId: "1",
    consoleName: "NES",
    coverUrl: "",
    isInPlayLater: true,
  },
  {
    id: "g3",
    title: "Metroid",
    consoleId: "2",
    consoleName: "SNES",
    coverUrl: "",
    isInPlayLater: true,
  },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PlayLaterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseRemoveFromPlayLater.mockReturnValue({ mutate: vi.fn() });
  mockUseReorderPlayLater.mockReturnValue({ mutate: vi.fn() });
});

describe("PlayLaterPage", () => {
  it("renders heading and description", () => {
    mockUsePlayLaterGames.mockReturnValue({ data: [], isLoading: false });
    renderPage();

    expect(
      screen.getByRole("heading", { name: "Play Later", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Your backlog — games you want to play next."),
    ).toBeInTheDocument();
  });

  it("shows empty state when queue is empty", () => {
    mockUsePlayLaterGames.mockReturnValue({ data: [], isLoading: false });
    renderPage();

    expect(
      screen.getByText("Your Play Later queue is empty"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Click the clock icon on any game to add it."),
    ).toBeInTheDocument();
  });

  it("shows empty state when data is undefined", () => {
    mockUsePlayLaterGames.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage();

    expect(
      screen.getByText("Your Play Later queue is empty"),
    ).toBeInTheDocument();
  });

  it("renders games in the queue", () => {
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("The Legend of Zelda")).toBeInTheDocument();
    expect(screen.getByText("Metroid")).toBeInTheDocument();
  });

  it("renders console names for each game", () => {
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    expect(screen.getAllByText("NES")).toHaveLength(2);
    expect(screen.getByText("SNES")).toBeInTheDocument();
  });

  it("disables move up button for first item", () => {
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    const moveUpButtons = screen.getAllByTitle("Move up");
    expect(moveUpButtons[0]).toBeDisabled();
    expect(moveUpButtons[1]).toBeEnabled();
  });

  it("disables move down button for last item", () => {
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    const moveDownButtons = screen.getAllByTitle("Move down");
    expect(moveDownButtons[2]).toBeDisabled();
    expect(moveDownButtons[0]).toBeEnabled();
  });

  it("calls remove mutation when remove button is clicked", async () => {
    const removeMutate = vi.fn();
    mockUseRemoveFromPlayLater.mockReturnValue({ mutate: removeMutate });
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    const removeButtons = screen.getAllByTitle("Remove from queue");
    await userEvent.click(removeButtons[0]);

    expect(removeMutate).toHaveBeenCalledWith("g1");
  });

  it("calls reorder mutation when move down button is clicked", async () => {
    const reorderMutate = vi.fn();
    mockUseReorderPlayLater.mockReturnValue({ mutate: reorderMutate });
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    const moveDownButtons = screen.getAllByTitle("Move down");
    await userEvent.click(moveDownButtons[0]);

    // After moving first item down, order should be g2, g1, g3
    expect(reorderMutate).toHaveBeenCalledWith(["g2", "g1", "g3"]);
  });

  it("calls reorder mutation when move up button is clicked", async () => {
    const reorderMutate = vi.fn();
    mockUseReorderPlayLater.mockReturnValue({ mutate: reorderMutate });
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    const moveUpButtons = screen.getAllByTitle("Move up");
    await userEvent.click(moveUpButtons[1]);

    // After moving second item up, order should be g2, g1, g3
    expect(reorderMutate).toHaveBeenCalledWith(["g2", "g1", "g3"]);
  });

  it("renders game links pointing to game detail pages", () => {
    mockUsePlayLaterGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
    renderPage();

    const marioLinks = screen.getAllByRole("link", {
      name: "Super Mario Bros.",
    });
    expect(marioLinks[0]).toHaveAttribute("href", "/games/g1");
  });
});
