import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { DeveloperHeroBanner } from "@/features/explore/components/developer-hero-banner";

describe("DeveloperHeroBanner", () => {
  it("renders the developer name as heading", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={23}
        avgRating={82.3}
        consoleCount={3}
      />,
    );
    expect(
      screen.getByRole("heading", { name: "Capcom", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders avatar with first letter", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );
    const avatar = screen.getByTestId("developer-avatar");
    expect(avatar).toHaveTextContent("C");
  });

  it("renders game count", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={23}
        avgRating={0}
        consoleCount={0}
      />,
    );
    expect(screen.getByTestId("developer-stats")).toHaveTextContent("23 games");
  });

  it("renders singular game text", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={1}
        avgRating={0}
        consoleCount={0}
      />,
    );
    expect(screen.getByTestId("developer-stats")).toHaveTextContent("1 game");
  });

  it("renders average rating when > 0", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={82.3}
        consoleCount={1}
      />,
    );
    expect(screen.getByTestId("developer-stats")).toHaveTextContent("82.3");
  });

  it("hides average rating when zero", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );
    expect(screen.getByTestId("developer-stats")).not.toHaveTextContent("0.0");
  });

  it("renders platform count", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={3}
      />,
    );
    expect(screen.getByTestId("developer-stats")).toHaveTextContent(
      "3 platforms",
    );
  });

  it("renders singular platform text", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );
    expect(screen.getByTestId("developer-stats")).toHaveTextContent(
      "1 platform",
    );
  });

  it("hides platform count when zero", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={0}
      />,
    );
    expect(screen.getByTestId("developer-stats")).not.toHaveTextContent(
      "platform",
    );
  });

  it("renders hero image when heroUrl provided", () => {
    const { container } = render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
        heroUrl="/images/hero.jpg"
      />,
    );
    const img = container.querySelector("img");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "/images/hero.jpg");
  });

  it("renders gradient fallback when no heroUrl", () => {
    const { container } = render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });
});
