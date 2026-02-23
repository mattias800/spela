import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { StagedUploadCard } from "../staged-upload-card";
import type { StagedUpload } from "@/types/api";

function makeUpload(overrides: Partial<StagedUpload> = {}): StagedUpload {
  return {
    id: "1",
    fileName: "Mario.nes",
    originalFileName: "Mario.nes",
    fileSize: 40960,
    consoleId: "nes",
    consoleName: "NES",
    possibleConsoles: [],
    status: "ready",
    title: "Super Mario Bros.",
    coverUrl: "",
    rating: 4.0,
    verificationStatus: "verified",
    crc32: "abc",
    canonicalName: "Super Mario Bros. (World).nes",
    ...overrides,
  };
}

const defaultHandlers = {
  onAccept: vi.fn(),
  onReject: vi.fn(),
  onSetConsole: vi.fn(),
  isAccepting: false,
  isRejecting: false,
  isSettingConsole: false,
};

describe("StagedUploadCard", () => {
  it("renders game summary card for ready uploads", () => {
    render(
      <StagedUploadCard upload={makeUpload()} {...defaultHandlers} />,
    );
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("NES")).toBeInTheDocument();
  });

  it("shows accept and reject buttons", () => {
    render(
      <StagedUploadCard upload={makeUpload()} {...defaultHandlers} />,
    );
    expect(screen.getByTestId("accept-button")).toBeInTheDocument();
    expect(screen.getByTestId("reject-button")).toBeInTheDocument();
  });

  it("calls onAccept when accept is clicked", () => {
    const onAccept = vi.fn();
    render(
      <StagedUploadCard
        upload={makeUpload()}
        {...defaultHandlers}
        onAccept={onAccept}
      />,
    );
    fireEvent.click(screen.getByTestId("accept-button"));
    expect(onAccept).toHaveBeenCalledWith("1");
  });

  it("calls onReject when reject is clicked", () => {
    const onReject = vi.fn();
    render(
      <StagedUploadCard
        upload={makeUpload()}
        {...defaultHandlers}
        onReject={onReject}
      />,
    );
    fireEvent.click(screen.getByTestId("reject-button"));
    expect(onReject).toHaveBeenCalledWith("1");
  });

  it("shows console selector for pending_console uploads", () => {
    const upload = makeUpload({
      status: "pending_console",
      possibleConsoles: [
        { id: "genesis", name: "Sega Genesis" },
        { id: "psx", name: "PlayStation" },
      ],
    });
    render(
      <StagedUploadCard upload={upload} {...defaultHandlers} />,
    );
    expect(screen.getByTestId("console-selector")).toBeInTheDocument();
    expect(screen.getByText(/matches multiple consoles/)).toBeInTheDocument();
  });

  it("shows pending scrape state", () => {
    const upload = makeUpload({ status: "pending_scrape" });
    render(
      <StagedUploadCard upload={upload} {...defaultHandlers} />,
    );
    expect(screen.getByText(/Waiting for scrape/)).toBeInTheDocument();
  });

  it("shows duplicate badge for duplicate uploads", () => {
    const upload = makeUpload({ status: "duplicate" });
    render(
      <StagedUploadCard upload={upload} {...defaultHandlers} />,
    );
    expect(screen.getByTestId("duplicate-badge")).toHaveTextContent(
      "Duplicate",
    );
  });
});
