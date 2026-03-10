import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ExplorerBadgesSection } from "@/features/explore/components/explorer-badges";
import type { ExplorerBadge } from "@/types/api";

const mockBadges: ExplorerBadge[] = [
  {
    id: "console-explorer",
    name: "Console Explorer",
    description: "Play games on 5 different consoles",
    icon: "globe",
    earned: true,
    progress: 5,
    target: 5,
  },
  {
    id: "genre-master",
    name: "Genre Master",
    description: "Play games in 8 different genres",
    icon: "palette",
    earned: false,
    progress: 3,
    target: 8,
  },
  {
    id: "centurion",
    name: "Centurion",
    description: "Play 100 different games",
    icon: "gamepad",
    earned: false,
    progress: 42,
    target: 100,
  },
];

describe("ExplorerBadgesSection", () => {
  it("renders badges with names and descriptions", () => {
    render(<ExplorerBadgesSection badges={mockBadges} isLoading={false} />);
    expect(screen.getByText("Console Explorer")).toBeInTheDocument();
    expect(screen.getByText("Genre Master")).toBeInTheDocument();
    expect(screen.getByText("Centurion")).toBeInTheDocument();
    expect(
      screen.getByText("Play games on 5 different consoles"),
    ).toBeInTheDocument();
  });

  it("shows earned badge label", () => {
    render(<ExplorerBadgesSection badges={mockBadges} isLoading={false} />);
    expect(screen.getByText("Earned")).toBeInTheDocument();
  });

  it("shows progress as fraction", () => {
    render(<ExplorerBadgesSection badges={mockBadges} isLoading={false} />);
    expect(screen.getByText("5 / 5")).toBeInTheDocument();
    expect(screen.getByText("3 / 8")).toBeInTheDocument();
    expect(screen.getByText("42 / 100")).toBeInTheDocument();
  });

  it("renders skeleton when loading", () => {
    render(<ExplorerBadgesSection badges={undefined} isLoading={true} />);
    expect(
      screen.getByTestId("explorer-badges-skeleton"),
    ).toBeInTheDocument();
  });

  it("returns null when badges is empty", () => {
    const { container } = render(
      <ExplorerBadgesSection badges={[]} isLoading={false} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders test id for each badge", () => {
    render(<ExplorerBadgesSection badges={mockBadges} isLoading={false} />);
    expect(screen.getByTestId("badge-console-explorer")).toBeInTheDocument();
    expect(screen.getByTestId("badge-genre-master")).toBeInTheDocument();
    expect(screen.getByTestId("badge-centurion")).toBeInTheDocument();
  });

  it("shows percentage for each badge", () => {
    render(<ExplorerBadgesSection badges={mockBadges} isLoading={false} />);
    expect(screen.getByText("100%")).toBeInTheDocument();
    expect(screen.getByText("38%")).toBeInTheDocument(); // 3/8
    expect(screen.getByText("42%")).toBeInTheDocument(); // 42/100
  });
});
