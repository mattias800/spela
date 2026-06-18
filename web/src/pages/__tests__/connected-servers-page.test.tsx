import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";

vi.mock("@/hooks/use-connected-servers", () => ({
  useConnectedServerConsoles: vi.fn(),
  useConnectedServerGames: vi.fn(),
}));
vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));
// The page mounts the self-contained FriendsPlayingNow widget, which fetches via
// its own hook; stub it so this test stays focused on the consoles overview.
vi.mock("@/hooks/use-federation-presence", () => ({
  useFederationPresence: vi.fn(() => ({ data: [], isLoading: false })),
}));

import { useConnectedServerConsoles } from "@/hooks/use-connected-servers";
import { useConsoles } from "@/hooks/use-consoles";
import { ConnectedServersPage } from "../connected-servers-page";

const mockConsoles = useConnectedServerConsoles as ReturnType<typeof vi.fn>;
const mockLocalConsoles = useConsoles as ReturnType<typeof vi.fn>;

function renderPage() {
  return render(
    <MemoryRouter>
      <ConnectedServersPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockLocalConsoles.mockReturnValue({
    data: [{ abbreviation: "SNES", name: "Super Nintendo" }],
  });
});

describe("ConnectedServersPage", () => {
  it("does not render the grid while loading", () => {
    mockConsoles.mockReturnValue({ data: undefined, isLoading: true });
    renderPage();
    expect(
      screen.queryByTestId("connected-consoles-grid"),
    ).not.toBeInTheDocument();
  });

  it("shows the empty state when there are no connected-server games", () => {
    mockConsoles.mockReturnValue({ data: [], isLoading: false });
    renderPage();
    expect(screen.getByText("No connected-server games")).toBeInTheDocument();
  });

  it("renders a card per console with resolved name, count, and link", () => {
    mockConsoles.mockReturnValue({
      data: [{ console: "SNES", count: 3 }],
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("connected-console-SNES");
    expect(card).toHaveAttribute("href", "/connected-servers/SNES");
    expect(card).toHaveTextContent("Super Nintendo");
    expect(card).toHaveTextContent("3 games");
  });

  it("falls back to the abbreviation when the console name is unknown", () => {
    mockConsoles.mockReturnValue({
      data: [{ console: "ZZZ", count: 1 }],
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("connected-console-ZZZ");
    expect(card).toHaveTextContent("ZZZ");
    expect(card).toHaveTextContent("1 game");
  });
});
