import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { UploadProgress, type UploadFileStatus } from "../upload-progress";

function makeFile(name: string, size = 1024): File {
  return new File(["x"], name, { type: "application/octet-stream" });
}

describe("UploadProgress", () => {
  it("renders nothing when files list is empty", () => {
    const { container } = render(<UploadProgress files={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it("shows upload count", () => {
    const files: UploadFileStatus[] = [
      { file: makeFile("a.nes"), status: "complete" },
      { file: makeFile("b.nes"), status: "uploading" },
      { file: makeFile("c.nes"), status: "pending" },
    ];
    render(<UploadProgress files={files} />);
    expect(screen.getByText(/Uploaded 1 of 3/)).toBeInTheDocument();
  });

  it("shows failed count", () => {
    const files: UploadFileStatus[] = [
      { file: makeFile("a.nes"), status: "complete" },
      { file: makeFile("b.nes"), status: "failed", error: "Bad file" },
    ];
    render(<UploadProgress files={files} />);
    expect(screen.getByText(/1 failed/)).toBeInTheDocument();
  });

  it("shows file names", () => {
    const files: UploadFileStatus[] = [
      { file: makeFile("mario.nes"), status: "complete" },
    ];
    render(<UploadProgress files={files} />);
    expect(screen.getByText("mario.nes")).toBeInTheDocument();
  });

  it("shows error message for failed files", () => {
    const files: UploadFileStatus[] = [
      { file: makeFile("bad.nes"), status: "failed", error: "Too large" },
    ];
    render(<UploadProgress files={files} />);
    expect(screen.getByText("Too large")).toBeInTheDocument();
  });
});
