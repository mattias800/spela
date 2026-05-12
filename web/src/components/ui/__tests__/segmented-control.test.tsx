import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { SegmentedControl } from "../segmented-control";

const options = [
  { value: "a", label: "Alpha", testId: "seg-a" },
  { value: "b", label: "Bravo", testId: "seg-b" },
  { value: "c", label: "Charlie", testId: "seg-c" },
] as const;

function setup(initial: "a" | "b" | "c" = "a") {
  const onChange = vi.fn();
  render(
    <SegmentedControl
      options={options as unknown as { value: "a" | "b" | "c"; label: string; testId: string }[]}
      value={initial}
      onChange={onChange}
      label="Pick one"
      testId="seg"
    />,
  );
  return { onChange };
}

describe("SegmentedControl", () => {
  it("renders a radiogroup with one radio per option", () => {
    setup();
    const group = screen.getByRole("radiogroup");
    expect(group).toBeInTheDocument();
    const radios = screen.getAllByRole("radio");
    expect(radios).toHaveLength(3);
  });

  it("marks the selected option with aria-checked=true and the rest false", () => {
    setup("b");
    expect(screen.getByTestId("seg-a")).toHaveAttribute("aria-checked", "false");
    expect(screen.getByTestId("seg-b")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("seg-c")).toHaveAttribute("aria-checked", "false");
  });

  it("only the selected segment is a tab stop", () => {
    setup("b");
    expect(screen.getByTestId("seg-a")).toHaveAttribute("tabindex", "-1");
    expect(screen.getByTestId("seg-b")).toHaveAttribute("tabindex", "0");
    expect(screen.getByTestId("seg-c")).toHaveAttribute("tabindex", "-1");
  });

  it("clicking a segment calls onChange with its value", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("a");
    await user.click(screen.getByTestId("seg-c"));
    expect(onChange).toHaveBeenCalledWith("c");
  });

  it("ArrowRight moves selection forward", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("a");
    screen.getByTestId("seg-a").focus();
    await user.keyboard("{ArrowRight}");
    expect(onChange).toHaveBeenLastCalledWith("b");
  });

  it("ArrowLeft moves selection backward", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("b");
    screen.getByTestId("seg-b").focus();
    await user.keyboard("{ArrowLeft}");
    expect(onChange).toHaveBeenLastCalledWith("a");
  });

  it("ArrowRight wraps from last to first", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("c");
    screen.getByTestId("seg-c").focus();
    await user.keyboard("{ArrowRight}");
    expect(onChange).toHaveBeenLastCalledWith("a");
  });

  it("ArrowLeft wraps from first to last", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("a");
    screen.getByTestId("seg-a").focus();
    await user.keyboard("{ArrowLeft}");
    expect(onChange).toHaveBeenLastCalledWith("c");
  });

  it("Home jumps to the first option", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("c");
    screen.getByTestId("seg-c").focus();
    await user.keyboard("{Home}");
    expect(onChange).toHaveBeenLastCalledWith("a");
  });

  it("End jumps to the last option", async () => {
    const user = userEvent.setup();
    const { onChange } = setup("a");
    screen.getByTestId("seg-a").focus();
    await user.keyboard("{End}");
    expect(onChange).toHaveBeenLastCalledWith("c");
  });

  it("uses ariaLabel when no visible label is provided", () => {
    const onChange = vi.fn();
    render(
      <SegmentedControl
        options={options as unknown as { value: "a" | "b" | "c"; label: string }[]}
        value="a"
        onChange={onChange}
        ariaLabel="Pick a letter"
      />,
    );
    const group = screen.getByRole("radiogroup");
    expect(group).toHaveAttribute("aria-label", "Pick a letter");
  });

  it("renders the visible label and links it via aria-labelledby", () => {
    setup();
    const group = screen.getByRole("radiogroup");
    const labelEl = screen.getByText("Pick one");
    expect(labelEl).toBeInTheDocument();
    expect(group).toHaveAttribute("aria-labelledby", labelEl.id);
  });
});
