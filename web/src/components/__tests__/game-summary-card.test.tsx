import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { GameSummaryCard } from "../game-summary-card";

describe("GameSummaryCard", () => {
  const defaultProps = {
    title: "Super Mario Bros.",
    coverUrl: "/covers/mario.jpg",
    consoleName: "NES",
    rating: 4.5,
    verificationStatus: "verified" as const,
    fileName: "Super Mario Bros.nes",
    fileSize: 40960,
  };

  it("renders the game title", () => {
    render(<GameSummaryCard {...defaultProps} />);
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
  });

  it("renders cover art when coverUrl is provided", () => {
    render(<GameSummaryCard {...defaultProps} />);
    const img = screen.getByRole("img");
    expect(img).toHaveAttribute("src", "/covers/mario.jpg");
    expect(img).toHaveAttribute("alt", "Super Mario Bros.");
  });

  it("renders a placeholder when no coverUrl", () => {
    render(<GameSummaryCard {...defaultProps} coverUrl={undefined} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("S")).toBeInTheDocument();
  });

  it("renders the console name badge", () => {
    render(<GameSummaryCard {...defaultProps} />);
    expect(screen.getByText("NES")).toBeInTheDocument();
  });

  it("renders the verified badge", () => {
    render(<GameSummaryCard {...defaultProps} />);
    expect(screen.getByTestId("verification-badge")).toHaveTextContent(
      "Verified",
    );
  });

  it("renders the unverified badge", () => {
    render(
      <GameSummaryCard {...defaultProps} verificationStatus="unverified" />,
    );
    expect(screen.getByTestId("verification-badge")).toHaveTextContent(
      "Unverified",
    );
  });

  it("renders the N/A badge for not_applicable", () => {
    render(
      <GameSummaryCard {...defaultProps} verificationStatus="not_applicable" />,
    );
    expect(screen.getByTestId("verification-badge")).toHaveTextContent("N/A");
  });

  it("does not render a verification badge when status is absent", () => {
    render(
      <GameSummaryCard {...defaultProps} verificationStatus={undefined} />,
    );
    expect(screen.queryByTestId("verification-badge")).not.toBeInTheDocument();
  });

  it("renders star rating", () => {
    render(<GameSummaryCard {...defaultProps} />);
    expect(screen.getByText("4.5")).toBeInTheDocument();
  });

  it("does not render rating when zero", () => {
    render(<GameSummaryCard {...defaultProps} rating={0} />);
    expect(screen.queryByText("0.0")).not.toBeInTheDocument();
  });

  it("renders file name and file size", () => {
    render(<GameSummaryCard {...defaultProps} />);
    expect(screen.getByText("Super Mario Bros.nes")).toBeInTheDocument();
    expect(screen.getByText("40.0 KB")).toBeInTheDocument();
  });

  it("renders duplicate badge when isDuplicate is true", () => {
    render(<GameSummaryCard {...defaultProps} isDuplicate />);
    expect(screen.getByTestId("duplicate-badge")).toHaveTextContent(
      "Duplicate",
    );
  });

  it("does not render duplicate badge by default", () => {
    render(<GameSummaryCard {...defaultProps} />);
    expect(screen.queryByTestId("duplicate-badge")).not.toBeInTheDocument();
  });

  it("renders actions when provided", () => {
    render(
      <GameSummaryCard
        {...defaultProps}
        actions={<button>Accept</button>}
      />,
    );
    expect(screen.getByText("Accept")).toBeInTheDocument();
  });

  it("falls back to fileName as title when title is empty", () => {
    render(<GameSummaryCard {...defaultProps} title="" />);
    const heading = screen.getByRole("heading");
    expect(heading).toHaveTextContent("Super Mario Bros.nes");
  });
});
