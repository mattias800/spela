import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { DropZone } from "../drop-zone";

describe("DropZone", () => {
  it("renders the drop zone with instructions", () => {
    render(<DropZone onFiles={vi.fn()} isUploading={false} />);
    expect(
      screen.getByText(/Drop ROM files or ZIP archives here/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Select Files/ }),
    ).toBeInTheDocument();
  });

  it("calls onFiles when files are dropped", () => {
    const onFiles = vi.fn();
    render(<DropZone onFiles={onFiles} isUploading={false} />);

    const dropZone = screen.getByTestId("upload-drop-zone");

    const file = new File(["rom data"], "game.nes", {
      type: "application/octet-stream",
    });

    fireEvent.drop(dropZone, {
      dataTransfer: {
        files: [file],
      },
    });

    expect(onFiles).toHaveBeenCalledWith([file]);
  });

  it("calls onFiles when files are selected via input", () => {
    const onFiles = vi.fn();
    render(<DropZone onFiles={onFiles} isUploading={false} />);

    const input = screen.getByTestId("upload-file-input");
    const file = new File(["rom data"], "game.nes", {
      type: "application/octet-stream",
    });

    fireEvent.change(input, { target: { files: [file] } });

    expect(onFiles).toHaveBeenCalledWith([file]);
  });

  it("shows loading state when uploading", () => {
    render(<DropZone onFiles={vi.fn()} isUploading={true} />);
    const button = screen.getByRole("button", { name: /Select Files/ });
    expect(button).toBeDisabled();
  });
});
