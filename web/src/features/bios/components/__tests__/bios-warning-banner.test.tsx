import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { BiosWarningBanner } from "../bios-warning-banner";

function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe("BiosWarningBanner", () => {
  it("renders the warning message", () => {
    renderWithRouter(
      <BiosWarningBanner message="Missing BIOS files" isAdmin={false} />,
    );
    expect(screen.getByText("Missing BIOS files")).toBeInTheDocument();
  });

  it("shows admin link when user is admin", () => {
    renderWithRouter(
      <BiosWarningBanner message="Missing BIOS" isAdmin={true} />,
    );
    const link = screen.getByText("Go to BIOS Management");
    expect(link).toBeInTheDocument();
    expect(link.closest("a")).toHaveAttribute("href", "/admin/bios");
  });

  it("shows contact admin message for non-admins", () => {
    renderWithRouter(
      <BiosWarningBanner message="Missing BIOS" isAdmin={false} />,
    );
    expect(
      screen.getByText(
        "Contact your server admin to upload the required BIOS files.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("Go to BIOS Management"),
    ).not.toBeInTheDocument();
  });

  it("shows missing file names", () => {
    renderWithRouter(
      <BiosWarningBanner
        message="Missing BIOS"
        isAdmin={false}
        missingFiles={["scph5501.bin", "scph5502.bin"]}
      />,
    );
    expect(
      screen.getByText("scph5501.bin, scph5502.bin"),
    ).toBeInTheDocument();
  });

  it("shows dismiss button when onDismiss is provided", async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    renderWithRouter(
      <BiosWarningBanner
        message="Missing BIOS"
        isAdmin={false}
        onDismiss={onDismiss}
      />,
    );

    const dismissButton = screen.getByLabelText("Dismiss");
    await user.click(dismissButton);
    expect(onDismiss).toHaveBeenCalled();
  });

  it("does not show dismiss button when onDismiss is not provided", () => {
    renderWithRouter(
      <BiosWarningBanner message="Missing BIOS" isAdmin={false} />,
    );
    expect(screen.queryByLabelText("Dismiss")).not.toBeInTheDocument();
  });
});
