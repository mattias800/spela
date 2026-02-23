import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { ActionsMenu } from "./actions-menu";

describe("ActionsMenu", () => {
  it("renders trigger button with actions-menu-btn test id", () => {
    render(<ActionsMenu items={[]} />);
    expect(screen.getByTestId("actions-menu-btn")).toBeInTheDocument();
  });

  it('has aria-label="Actions" on trigger', () => {
    render(<ActionsMenu items={[]} />);
    expect(
      screen.getByRole("button", { name: "Actions" }),
    ).toBeInTheDocument();
  });

  it("opens dropdown when clicked, showing items", async () => {
    render(
      <ActionsMenu
        items={[
          { label: "Favorite", onClick: vi.fn() },
          { label: "Delete", onClick: vi.fn(), variant: "danger" },
        ]}
      />,
    );

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: "Favorite" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: "Delete" }),
    ).toBeInTheDocument();
  });

  it("calls onClick for a menu item", async () => {
    const handleFavorite = vi.fn();
    render(
      <ActionsMenu
        items={[{ label: "Favorite", onClick: handleFavorite }]}
      />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    await userEvent.click(
      screen.getByRole("menuitem", { name: "Favorite" }),
    );
    expect(handleFavorite).toHaveBeenCalledOnce();
  });

  it("renders loading spinner when item has loading=true", async () => {
    const { container } = render(
      <ActionsMenu
        items={[{ label: "Scraping", onClick: vi.fn(), loading: true }]}
      />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(container.querySelector(".animate-spin")).toBeInTheDocument();
    // The item should be disabled when loading
    expect(
      screen.getByRole("menuitem", { name: "Scraping" }),
    ).toBeDisabled();
  });

  it("disables items when disabled=true", async () => {
    render(
      <ActionsMenu
        items={[{ label: "In Queue", onClick: vi.fn(), disabled: true }]}
      />,
    );

    await userEvent.click(screen.getByTestId("actions-menu-btn"));
    expect(
      screen.getByRole("menuitem", { name: "In Queue" }),
    ).toBeDisabled();
  });
});
