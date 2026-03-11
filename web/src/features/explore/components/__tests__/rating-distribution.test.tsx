import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { RatingDistribution } from "@/features/explore/components/rating-distribution";
import type { RatingDistribution as RatingDistributionType } from "@/types/api";

const fullDistribution: RatingDistributionType = {
  excellent: 10,
  good: 8,
  average: 5,
  poor: 2,
  unrated: 3,
};

describe("RatingDistribution", () => {
  it("renders the section with test id", () => {
    render(<RatingDistribution distribution={fullDistribution} />);
    expect(screen.getByTestId("rating-distribution")).toBeInTheDocument();
  });

  it("renders the heading", () => {
    render(<RatingDistribution distribution={fullDistribution} />);
    expect(
      screen.getByRole("heading", { name: "Rating Distribution" }),
    ).toBeInTheDocument();
  });

  it("renders all non-zero bars", () => {
    render(<RatingDistribution distribution={fullDistribution} />);
    expect(screen.getByTestId("rating-bar-excellent")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-good")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-average")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-poor")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-unrated")).toBeInTheDocument();
  });

  it("renders labels and counts for each bar", () => {
    render(<RatingDistribution distribution={fullDistribution} />);
    const excellent = screen.getByTestId("rating-bar-excellent");
    expect(excellent).toHaveTextContent("Excellent");
    expect(excellent).toHaveTextContent("10");

    const good = screen.getByTestId("rating-bar-good");
    expect(good).toHaveTextContent("Good");
    expect(good).toHaveTextContent("8");

    const average = screen.getByTestId("rating-bar-average");
    expect(average).toHaveTextContent("Average");
    expect(average).toHaveTextContent("5");

    const poor = screen.getByTestId("rating-bar-poor");
    expect(poor).toHaveTextContent("Poor");
    expect(poor).toHaveTextContent("2");

    const unrated = screen.getByTestId("rating-bar-unrated");
    expect(unrated).toHaveTextContent("Unrated");
    expect(unrated).toHaveTextContent("3");
  });

  it("hides bars with zero count", () => {
    render(
      <RatingDistribution
        distribution={{
          excellent: 5,
          good: 3,
          average: 0,
          poor: 0,
          unrated: 2,
        }}
      />,
    );
    expect(screen.getByTestId("rating-bar-excellent")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-good")).toBeInTheDocument();
    expect(
      screen.queryByTestId("rating-bar-average"),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("rating-bar-poor")).not.toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-unrated")).toBeInTheDocument();
  });

  it("renders nothing when fewer than 5 rated games", () => {
    const { container } = render(
      <RatingDistribution
        distribution={{
          excellent: 2,
          good: 1,
          average: 1,
          poor: 0,
          unrated: 10,
        }}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders when exactly 5 rated games", () => {
    render(
      <RatingDistribution
        distribution={{
          excellent: 3,
          good: 2,
          average: 0,
          poor: 0,
          unrated: 0,
        }}
      />,
    );
    expect(screen.getByTestId("rating-distribution")).toBeInTheDocument();
  });

  it("renders nothing when all ratings are zero", () => {
    const { container } = render(
      <RatingDistribution
        distribution={{
          excellent: 0,
          good: 0,
          average: 0,
          poor: 0,
          unrated: 20,
        }}
      />,
    );
    expect(container.firstChild).toBeNull();
  });
});
