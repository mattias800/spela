import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { BiosUploadResults, type UploadResult } from "../bios-upload-results";
import type { BiosFile } from "@/types/api";

function makeFile(name: string): File {
  return new File(["data"], name, { type: "application/octet-stream" });
}

function makeBiosFile(overrides: Partial<BiosFile> = {}): BiosFile {
  return {
    name: "scph5501.bin",
    size: 524288,
    md5: "abc123",
    consoleId: "psx",
    consoleName: "PlayStation",
    description: "PlayStation BIOS (North America)",
    required: true,
    status: "valid",
    ...overrides,
  };
}

describe("BiosUploadResults", () => {
  it("renders nothing when results are empty", () => {
    const { container } = render(<BiosUploadResults results={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders a valid match result", () => {
    const results: UploadResult[] = [
      {
        file: makeFile("scph5501.bin"),
        result: makeBiosFile(),
      },
    ];
    render(<BiosUploadResults results={results} />);
    expect(screen.getByText("Upload Results")).toBeInTheDocument();
    expect(screen.getByText("scph5501.bin")).toBeInTheDocument();
    expect(screen.getByText(/Matched: PlayStation/)).toBeInTheDocument();
  });

  it("renders an invalid checksum result", () => {
    const results: UploadResult[] = [
      {
        file: makeFile("scph5501.bin"),
        result: makeBiosFile({ status: "invalid", md5: "expected123" }),
      },
    ];
    render(<BiosUploadResults results={results} />);
    expect(screen.getByText(/Checksum mismatch/)).toBeInTheDocument();
  });

  it("renders an unrecognized file result", () => {
    const results: UploadResult[] = [
      {
        file: makeFile("unknown.bin"),
        result: makeBiosFile({
          name: "unknown.bin",
          consoleId: null,
          consoleName: undefined,
          description: undefined,
          status: "present",
          required: false,
        }),
      },
    ];
    render(<BiosUploadResults results={results} />);
    expect(screen.getByText(/Unrecognized file/)).toBeInTheDocument();
  });

  it("renders an error result", () => {
    const results: UploadResult[] = [
      {
        file: makeFile("toolarge.bin"),
        error: "File too large",
      },
    ];
    render(<BiosUploadResults results={results} />);
    expect(screen.getByText("File too large")).toBeInTheDocument();
  });
});
