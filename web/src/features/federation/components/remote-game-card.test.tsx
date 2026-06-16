import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import { RemoteGameCard } from "./remote-game-card";
import type { CatalogAvailability } from "@/generated/schemas";

function game(overrides: Partial<CatalogAvailability> = {}): CatalogAvailability {
  return {
    key: "igdb:42",
    title: "Chrono Trigger",
    console: "SNES",
    originCount: 2,
    local: false,
    cover: "", // required string; empty means "no cover" (placeholder shows)
    ...overrides,
  };
}

function renderCard(g: CatalogAvailability) {
  return render(
    <MemoryRouter>
      <RemoteGameCard game={g} />
    </MemoryRouter>,
  );
}

describe("RemoteGameCard", () => {
  it("links to the remote-game page with the encoded key", () => {
    renderCard(game());
    const link = screen.getByTestId("remote-game-card-igdb:42");
    expect(link).toHaveAttribute("href", "/remote-games/igdb%3A42");
    expect(link).toHaveTextContent("Chrono Trigger");
  });

  it("pluralizes the connected-server count", () => {
    renderCard(game({ originCount: 1 }));
    expect(screen.getByText("on 1 connected server")).toBeInTheDocument();
  });

  it("renders the cover when present, a placeholder initial otherwise", () => {
    const { rerender } = renderCard(
      game({ cover: "https://img.example/co.jpg" }),
    );
    expect(screen.getByRole("img", { name: "Chrono Trigger" })).toHaveAttribute(
      "src",
      "https://img.example/co.jpg",
    );

    rerender(
      <MemoryRouter>
        <RemoteGameCard game={game({ cover: "" })} />
      </MemoryRouter>,
    );
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("C")).toBeInTheDocument(); // placeholder initial
  });
});
