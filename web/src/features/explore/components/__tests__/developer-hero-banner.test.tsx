import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { DeveloperHeroBanner } from "@/features/explore/components/developer-hero-banner";

// Mock the animated counter to return the target value immediately (no animation in tests)
vi.mock("@/hooks/use-animated-counter", () => ({
  useAnimatedCounter: (target: number) => target,
}));

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
    // No hero image, but there could be the logo or avatar images; check specifically for hero
    const imgs = container.querySelectorAll("img");
    // Should have no images (no hero, no logo)
    expect(imgs).toHaveLength(0);
  });

  it("renders company logo instead of letter avatar when logoUrl provided", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
        logoUrl="/images/companies/capcom-logo.png"
      />,
    );
    const logo = screen.getByTestId("developer-logo");
    expect(logo).toBeInTheDocument();
    expect(logo).toHaveAttribute("src", "/images/companies/capcom-logo.png");
    expect(logo).toHaveAttribute("alt", "Capcom logo");
    expect(screen.queryByTestId("developer-avatar")).not.toBeInTheDocument();
  });

  it("renders letter avatar when logoUrl is not provided", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );
    expect(screen.getByTestId("developer-avatar")).toBeInTheDocument();
    expect(screen.queryByTestId("developer-logo")).not.toBeInTheDocument();
  });

  // --- Share button ---

  it("renders share button", () => {
    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );
    expect(screen.getByTestId("share-button")).toBeInTheDocument();
    expect(screen.getByTestId("share-button")).toHaveTextContent("Share");
  });

  it("shows copied state when share button is clicked", async () => {
    const user = userEvent.setup();
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      writable: true,
      configurable: true,
    });

    render(
      <DeveloperHeroBanner
        name="Capcom"
        gameCount={5}
        avgRating={0}
        consoleCount={1}
      />,
    );

    await user.click(screen.getByTestId("share-button"));

    expect(screen.getByTestId("share-button")).toHaveTextContent("Copied!");
    expect(screen.getByTestId("share-toast")).toHaveTextContent("Link copied to clipboard");
  });
});
