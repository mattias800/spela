import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { Input } from "./input";

describe("Input", () => {
  it("renders with label", () => {
    render(<Input id="test" label="Username" />);
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
  });

  it("renders without label", () => {
    render(<Input placeholder="Enter text" />);
    expect(screen.getByPlaceholderText("Enter text")).toBeInTheDocument();
  });

  it("shows error message", () => {
    render(<Input error="Required field" />);
    expect(screen.getByText("Required field")).toBeInTheDocument();
  });

  it("handles input changes", async () => {
    const onChange = vi.fn();
    render(<Input onChange={onChange} />);
    await userEvent.type(screen.getByRole("textbox"), "hello");
    expect(onChange).toHaveBeenCalled();
  });

  it("applies error styles", () => {
    const { container } = render(<Input error="Error" />);
    const input = container.querySelector("input");
    expect(input?.className).toContain("border-danger-500");
  });

  it("renders a left icon and pads the input to make room", () => {
    const { container } = render(
      <Input
        leftIcon={<svg data-testid="left-icon" />}
        placeholder="Search"
      />,
    );
    expect(screen.getByTestId("left-icon")).toBeInTheDocument();
    expect(container.querySelector("input")?.className).toContain("pl-10");
  });

  it("renders a right icon and pads the input to make room", () => {
    const { container } = render(
      <Input rightIcon={<svg data-testid="right-icon" />} />,
    );
    expect(screen.getByTestId("right-icon")).toBeInTheDocument();
    expect(container.querySelector("input")?.className).toContain("pr-10");
  });

  it("vertically centers icons via flex (no -translate hack)", () => {
    // The wrapper span should fill the input height (`inset-y-0`) and
    // center the icon via flex — adapts to any input height without
    // hard-coded translate math.
    render(<Input leftIcon={<svg data-testid="left-icon" />} />);
    const wrapper = screen.getByTestId("left-icon").parentElement;
    expect(wrapper?.className).toContain("inset-y-0");
    expect(wrapper?.className).toContain("flex");
    expect(wrapper?.className).toContain("items-center");
    expect(wrapper?.className).not.toContain("-translate-y-1/2");
  });
});
