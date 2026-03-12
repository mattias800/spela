import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import { DropdownMenu } from "./dropdown-menu";

describe("DropdownMenu", () => {
  it("renders trigger", () => {
    render(
      <DropdownMenu trigger={<button>Open</button>}>
        <div>Item</div>
      </DropdownMenu>,
    );
    expect(screen.getByRole("button", { name: "Open" })).toBeInTheDocument();
  });

  it("opens on click", async () => {
    render(
      <DropdownMenu trigger={<button>Open</button>}>
        <div>Item</div>
      </DropdownMenu>,
    );
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getByText("Item")).toBeInTheDocument();
  });

  it("closes on outside click", async () => {
    render(
      <div>
        <DropdownMenu trigger={<button>Open</button>}>
          <div>Item</div>
        </DropdownMenu>
        <button>Outside</button>
      </div>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Outside" }));
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("closes on item click", async () => {
    render(
      <DropdownMenu trigger={<button>Open</button>}>
        <button>Item</button>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Item" }));
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("applies left align class", async () => {
    render(
      <DropdownMenu trigger={<button>Open</button>} align="left">
        <div>Item</div>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    const menu = screen.getByRole("menu");
    expect(menu.className).toContain("left-0");
    expect(menu.className).not.toContain("right-0");
  });

  it("applies right align class by default", async () => {
    render(
      <DropdownMenu trigger={<button>Open</button>}>
        <div>Item</div>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    const menu = screen.getByRole("menu");
    expect(menu.className).toContain("right-0");
  });

  it("stays open when clicking a disabled item", async () => {
    render(
      <DropdownMenu trigger={<button>Open</button>}>
        <button disabled>Disabled</button>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Disabled" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("has role menu on the dropdown panel", async () => {
    render(
      <DropdownMenu trigger={<button>Open</button>}>
        <div>Item</div>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });
});
