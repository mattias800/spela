import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { ThemeCard } from "../theme-card";

describe("ThemeCard", () => {
  it("renders all theme options", () => {
    render(
      <ThemeCard
        selectedTheme="default-dark"
        isLoading={false}
        onThemeChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Color Theme")).toBeInTheDocument();
    expect(screen.getByText("Default Dark")).toBeInTheDocument();
    expect(screen.getByText("Default Light")).toBeInTheDocument();
    expect(screen.getByText("Nintendo Colorful")).toBeInTheDocument();
    expect(screen.getByText("Ocean Dark")).toBeInTheDocument();
    expect(screen.getByText("Sunset Warm")).toBeInTheDocument();
  });

  it("renders the experimental themes banner", () => {
    render(
      <ThemeCard
        selectedTheme="default-dark"
        isLoading={false}
        onThemeChange={vi.fn()}
      />,
    );

    const banner = screen.getByTestId("theme-experimental-banner");
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent(/experimental/i);
    expect(banner).toHaveTextContent(/player app/i);
  });

  it("renders the experimental themes banner while loading", () => {
    render(
      <ThemeCard
        selectedTheme={undefined}
        isLoading={true}
        onThemeChange={vi.fn()}
      />,
    );

    expect(
      screen.getByTestId("theme-experimental-banner"),
    ).toBeInTheDocument();
  });

  it("renders loading skeleton", () => {
    const { container } = render(
      <ThemeCard
        selectedTheme={undefined}
        isLoading={true}
        onThemeChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Color Theme")).toBeInTheDocument();
    expect(screen.queryByText("Default Dark")).not.toBeInTheDocument();
    // Should render 5 skeleton elements
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBe(5);
  });

  it("highlights the selected theme", () => {
    render(
      <ThemeCard
        selectedTheme="nintendo-colorful"
        isLoading={false}
        onThemeChange={vi.fn()}
      />,
    );

    // The selected theme button should have ring styling
    const nintendoButton = screen
      .getByText("Nintendo Colorful")
      .closest("button");
    expect(nintendoButton?.className).toContain("border-brand-500");

    // Other buttons should not have the selected style
    const darkButton = screen.getByText("Default Dark").closest("button");
    expect(darkButton?.className).not.toContain("border-brand-500");
  });

  it("calls onThemeChange when a theme is clicked", async () => {
    const user = userEvent.setup();
    const onThemeChange = vi.fn();

    render(
      <ThemeCard
        selectedTheme="default-dark"
        isLoading={false}
        onThemeChange={onThemeChange}
      />,
    );

    await user.click(screen.getByText("Nintendo Colorful").closest("button")!);
    expect(onThemeChange).toHaveBeenCalledWith("nintendo-colorful");
  });

  it("defaults to default-dark when selectedTheme is undefined", () => {
    render(
      <ThemeCard
        selectedTheme={undefined}
        isLoading={false}
        onThemeChange={vi.fn()}
      />,
    );

    const darkButton = screen.getByText("Default Dark").closest("button");
    expect(darkButton?.className).toContain("border-brand-500");
  });

  it("renders color swatches for each theme", () => {
    const { container } = render(
      <ThemeCard
        selectedTheme="default-dark"
        isLoading={false}
        onThemeChange={vi.fn()}
      />,
    );

    // 5 themes × 4 swatches each = 20 swatch elements
    const swatches = container.querySelectorAll("[style*='background-color']");
    expect(swatches.length).toBe(20);
  });
});
