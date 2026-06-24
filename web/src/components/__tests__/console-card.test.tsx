import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import { ConsoleCard } from "../console-card";
import type { Console } from "@/types/api";

function makeConsole(overrides: Partial<Console> = {}): Console {
  return {
    id: "nes",
    name: "Nintendo Entertainment System",
    abbreviation: "NES",
    extensions: [],
    defaultCore: "",
    coverAspectRatio: 0.75,
    logoAspectRatio: null,
    colorTheme: "",
    generation: 3,
    iconUrl: "/api/consoles/nes/icon",
    logoUrl: "/api/consoles/nes/logo",
    gameCount: 10,
    saveStateSupport: true,
    saveStatePolicy: "small",
    browserPlayable: false,
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
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function renderCard(props: Parameters<typeof ConsoleCard>[0]) {
  return render(
    <MemoryRouter>
      <ConsoleCard {...props} />
    </MemoryRouter>,
  );
}

describe("ConsoleCard", () => {
  it("renders the console name (as image alt) and game count", () => {
    renderCard({ console: makeConsole() });
    // The name is conveyed via the logo/photo image alt rather than text.
    expect(
      screen.getAllByAltText("Nintendo Entertainment System").length,
    ).toBeGreaterThan(0);
    expect(screen.getByText("10 games")).toBeInTheDocument();
  });

  it("renders the qualifier tag chip when present (data-driven)", () => {
    renderCard({ console: makeConsole({ tag: "Demos" }) });
    expect(screen.getByText("Demos")).toBeInTheDocument();
  });

  it("omits the tag chip when the console has no tag", () => {
    renderCard({ console: makeConsole() });
    expect(screen.queryByText("Demos")).not.toBeInTheDocument();
  });

  // #933: amber ⚠ badge in the top-right indicator group when the
  // console has missing required BIOS files. Mirrors the player app.
  it("shows the BIOS-missing warning when hasMissingBios is true", () => {
    renderCard({
      console: makeConsole({ name: "Fairchild Channel F" }),
      hasMissingBios: true,
    });
    const warning = screen.getByTestId("console-card-bios-warning");
    expect(warning).toBeInTheDocument();
    expect(warning).toHaveAttribute(
      "aria-label",
      "BIOS missing for Fairchild Channel F",
    );
  });

  it("does not show the BIOS-missing warning by default", () => {
    renderCard({ console: makeConsole() });
    expect(
      screen.queryByTestId("console-card-bios-warning"),
    ).not.toBeInTheDocument();
  });

  it("does not show the BIOS-missing warning when hasMissingBios is false", () => {
    renderCard({ console: makeConsole(), hasMissingBios: false });
    expect(
      screen.queryByTestId("console-card-bios-warning"),
    ).not.toBeInTheDocument();
  });
});
