import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { VerificationBadge } from "../verification-badge";
import type { Game } from "@/types/api";

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "game-1",
    title: "Test Game",
    consoleId: "nes",
    consoleName: "NES",
    fileName: "test.nes",
    fileSize: 1024,
    discCount: 1,
    screenshotUrls: [],
    scrapeAttempts: 1,
    coverAspectRatio: 0.75,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

describe("VerificationBadge", () => {
  it("renders nothing when no verification status", () => {
    const game = makeGame();
    const { container } = renderWithQuery(
      <VerificationBadge game={game} isAdmin={false} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders "Verified" badge when status is "verified"', () => {
    const game = makeGame({ verificationStatus: "verified" });
    renderWithQuery(<VerificationBadge game={game} isAdmin={false} />);

    expect(screen.getByText("Verified")).toBeInTheDocument();
    expect(screen.getByTestId("verification-badge")).toBeInTheDocument();
  });

  it('renders "Unverified" badge when status is "unverified"', () => {
    const game = makeGame({ verificationStatus: "unverified" });
    renderWithQuery(<VerificationBadge game={game} isAdmin={false} />);

    expect(screen.getByText("Unverified")).toBeInTheDocument();
    expect(screen.getByTestId("verification-badge")).toBeInTheDocument();
  });

  it("renders tag text instead of Unverified when tag is set", () => {
    const game = makeGame({
      verificationStatus: "unverified",
      verificationTag: "ROM Hack",
    });
    renderWithQuery(<VerificationBadge game={game} isAdmin={false} />);

    expect(screen.getByText("ROM Hack")).toBeInTheDocument();
    expect(screen.queryByText("Unverified")).not.toBeInTheDocument();
  });

  it("closes overlay when Escape key is pressed", async () => {
    const game = makeGame({ verificationStatus: "unverified" });
    renderWithQuery(<VerificationBadge game={game} isAdmin={true} />);

    // Open overlay
    await userEvent.click(screen.getByText("Unverified"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    // Press Escape
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes overlay when clicking outside", async () => {
    const game = makeGame({ verificationStatus: "unverified" });
    const { container } = renderWithQuery(
      <div>
        <div data-testid="outside">Outside</div>
        <VerificationBadge game={game} isAdmin={true} />
      </div>,
    );

    // Open overlay
    await userEvent.click(screen.getByText("Unverified"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    // Click outside
    fireEvent.mouseDown(screen.getByTestId("outside"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
