import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { CloneSessionDialog } from "../clone-session-dialog";

const onClose = vi.fn();
const onConfirm = vi.fn();

function renderDialog(
  overrides: Partial<React.ComponentProps<typeof CloneSessionDialog>> = {},
) {
  const props: React.ComponentProps<typeof CloneSessionDialog> = {
    open: true,
    onClose,
    sourceName: "Main Playthrough",
    isPending: false,
    onConfirm,
    ...overrides,
  };
  return render(<CloneSessionDialog {...props} />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CloneSessionDialog", () => {
  it("renders nothing when closed", () => {
    renderDialog({ open: false });
    expect(screen.queryByTestId("clone-session-dialog")).not.toBeInTheDocument();
  });

  it("pre-fills the name field with '{source} (Copy)'", () => {
    renderDialog();
    const input = screen.getByTestId("clone-session-name-input") as HTMLInputElement;
    expect(input.value).toBe("Main Playthrough (Copy)");
  });

  it("lets the user edit the name", () => {
    renderDialog();
    const input = screen.getByTestId("clone-session-name-input");
    fireEvent.change(input, { target: { value: "Boss checkpoint" } });
    expect((input as HTMLInputElement).value).toBe("Boss checkpoint");
  });

  it("calls onClose when Cancel is clicked", () => {
    renderDialog();
    fireEvent.click(screen.getByTestId("clone-session-cancel"));
    expect(onClose).toHaveBeenCalled();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("submits the edited name on Clone click", () => {
    renderDialog();
    const input = screen.getByTestId("clone-session-name-input");
    fireEvent.change(input, { target: { value: "Boss checkpoint" } });
    fireEvent.click(screen.getByTestId("clone-session-confirm"));
    expect(onConfirm).toHaveBeenCalledWith("Boss checkpoint");
  });

  it("submits on Enter keypress in the name field", () => {
    renderDialog();
    const input = screen.getByTestId("clone-session-name-input");
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onConfirm).toHaveBeenCalledWith("Main Playthrough (Copy)");
  });

  it("closes on Escape keypress", () => {
    renderDialog();
    const input = screen.getByTestId("clone-session-name-input");
    fireEvent.keyDown(input, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });

  it("trims whitespace before submitting", () => {
    renderDialog();
    const input = screen.getByTestId("clone-session-name-input");
    fireEvent.change(input, { target: { value: "  Spacey   " } });
    fireEvent.click(screen.getByTestId("clone-session-confirm"));
    expect(onConfirm).toHaveBeenCalledWith("Spacey");
  });

  it("disables Cancel and shows spinner on Clone while pending", () => {
    renderDialog({ isPending: true });
    const cancel = screen.getByTestId("clone-session-cancel") as HTMLButtonElement;
    const confirm = screen.getByTestId("clone-session-confirm") as HTMLButtonElement;
    expect(cancel).toBeDisabled();
    expect(confirm).toBeDisabled();
  });

  it("does not fire onConfirm twice while pending", () => {
    renderDialog({ isPending: true });
    fireEvent.click(screen.getByTestId("clone-session-confirm"));
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("renders a description when provided", () => {
    renderDialog({ description: "Cloning from Auto-save 1" });
    expect(screen.getByText("Cloning from Auto-save 1")).toBeInTheDocument();
  });

  it("uses a custom confirm label when provided", () => {
    renderDialog({ confirmLabel: "Clone to my library" });
    expect(screen.getByTestId("clone-session-confirm")).toHaveTextContent(
      "Clone to my library",
    );
  });

  it("uses a custom title when provided", () => {
    renderDialog({ title: "Clone to my library" });
    expect(
      screen.getByRole("heading", { name: "Clone to my library" }),
    ).toBeInTheDocument();
  });

  it("resets the name input when reopened with a different source", () => {
    const { rerender } = render(
      <CloneSessionDialog
        open={true}
        onClose={onClose}
        sourceName="First"
        isPending={false}
        onConfirm={onConfirm}
      />,
    );
    // User edits, then closes
    fireEvent.change(screen.getByTestId("clone-session-name-input"), {
      target: { value: "Edited" },
    });
    rerender(
      <CloneSessionDialog
        open={false}
        onClose={onClose}
        sourceName="First"
        isPending={false}
        onConfirm={onConfirm}
      />,
    );
    // Reopen with a different source — input should reset to the new default
    rerender(
      <CloneSessionDialog
        open={true}
        onClose={onClose}
        sourceName="Second"
        isPending={false}
        onConfirm={onConfirm}
      />,
    );
    const input = screen.getByTestId(
      "clone-session-name-input",
    ) as HTMLInputElement;
    expect(input.value).toBe("Second (Copy)");
  });
});
