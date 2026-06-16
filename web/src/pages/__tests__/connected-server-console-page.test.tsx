import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter, Routes, Route } from "react-router-dom";

vi.mock("@/hooks/use-connected-servers", () => ({
  useConnectedServerConsoles: vi.fn(),
  useConnectedServerGames: vi.fn(),
}));
vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

import { useConnectedServerGames } from "@/hooks/use-connected-servers";
import { useConsoles } from "@/hooks/use-consoles";
import { ConnectedServerConsolePage } from "../connected-server-console-page";

const mockGames = useConnectedServerGames as ReturnType<typeof vi.fn>;
const mockLocalConsoles = useConsoles as ReturnType<typeof vi.fn>;

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/connected-servers/SNES"]}>
      <Routes>
        <Route
          path="/connected-servers/:console"
          element={<ConnectedServerConsolePage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockLocalConsoles.mockReturnValue({
    data: [{ abbreviation: "SNES", name: "Super Nintendo" }],
  });
});

describe("ConnectedServerConsolePage", () => {
  it("titles the page with the resolved console name", () => {
    mockGames.mockReturnValue({ data: [], isLoading: false });
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Super Nintendo" }),
    ).toBeInTheDocument();
  });

  it("shows the empty state when the console has no games", () => {
    mockGames.mockReturnValue({ data: [], isLoading: false });
    renderPage();
    expect(screen.getByText("No games for this console")).toBeInTheDocument();
  });

  it("renders a card per game", () => {
    mockGames.mockReturnValue({
      data: [
        {
          key: "igdb:1",
          title: "Chrono Trigger",
          console: "SNES",
          originCount: 1,
          local: false,
        },
        {
          key: "igdb:2",
          title: "Secret of Mana",
          console: "SNES",
          originCount: 2,
          local: false,
        },
      ],
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("connected-games-grid")).toBeInTheDocument();
    expect(screen.getByTestId("remote-game-card-igdb:1")).toHaveTextContent(
      "Chrono Trigger",
    );
    expect(screen.getByTestId("remote-game-card-igdb:2")).toHaveTextContent(
      "Secret of Mana",
    );
  });
});
