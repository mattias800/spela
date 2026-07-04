import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReplaceRomModal } from "../replace-rom-modal";

const mockMutate = vi.fn();
const mockReset = vi.fn();

vi.mock("@/hooks/use-games", () => ({
  useReplaceRom: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
    isError: false,
    error: null,
    reset: mockReset,
  })),
}));

import { useReplaceRom } from "@/hooks/use-games";

const mockUseReplaceRom = useReplaceRom as ReturnType<typeof vi.fn>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

const defaultProps = {
  gameId: "game-1",
  open: true,
  onClose: vi.fn(),
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUseReplaceRom.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
    isError: false,
    error: null,
    reset: mockReset,
  });
});

describe("ReplaceRomModal", () => {
  it("renders modal with drop zone when open", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    expect(
      screen.getByRole("dialog", { name: "Replace ROM" }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("drop-zone")).toBeInTheDocument();
    expect(
      screen.getByText("Drop a ROM file here or click to browse"),
    ).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} open={false} />
      </Wrapper>,
    );

    expect(
      screen.queryByRole("dialog", { name: "Replace ROM" }),
    ).not.toBeInTheDocument();
  });

  it("triggers upload when a file is selected via input", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    const file = new File(["rom content"], "game.nes", {
      type: "application/octet-stream",
    });
    const input = screen.getByTestId("file-input");

    await userEvent.upload(input, file);

    expect(mockMutate).toHaveBeenCalledWith(
      { gameId: "game-1", file },
      expect.objectContaining({
        onSuccess: expect.any(Function),
      }),
    );
  });

  it("shows success state when ROM is verified", async () => {
    // Simulate a successful upload with verified result
    mockMutate.mockImplementation((_args: unknown, opts: { onSuccess: (data: unknown) => void }) => {
      opts.onSuccess({
        replacementResult: {
          verified: true,
          crc32: "ABCD1234",
          canonicalName: "Super Mario Bros (USA).nes",
          previousStatus: "unverified",
          previousCrc32: "00000000",
        },
      });
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    const file = new File(["rom content"], "mario.nes", {
      type: "application/octet-stream",
    });
    const input = screen.getByTestId("file-input");
    await userEvent.upload(input, file);

    await waitFor(() => {
      expect(screen.getByTestId("replace-result")).toBeInTheDocument();
    });

    expect(screen.getByText("ROM Verified")).toBeInTheDocument();
    expect(screen.getByText("Verified")).toBeInTheDocument();
    expect(
      screen.getByText("Super Mario Bros (USA).nes"),
    ).toBeInTheDocument();
    expect(screen.getByText("CRC32: ABCD1234")).toBeInTheDocument();
  });

  it("shows warning state when ROM is unverified", async () => {
    mockMutate.mockImplementation((_args: unknown, opts: { onSuccess: (data: unknown) => void }) => {
      opts.onSuccess({
        replacementResult: {
          verified: false,
          crc32: "DEADBEEF",
          previousStatus: "unverified",
          previousCrc32: "00000000",
        },
      });
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    const file = new File(["rom content"], "hack.nes", {
      type: "application/octet-stream",
    });
    const input = screen.getByTestId("file-input");
    await userEvent.upload(input, file);

    await waitFor(() => {
      expect(screen.getByTestId("replace-result")).toBeInTheDocument();
    });

    expect(screen.getByText("ROM Replaced")).toBeInTheDocument();
    expect(screen.getByText("Unverified")).toBeInTheDocument();
    expect(
      screen.getByText(
        "ROM replaced but does not match any known verified dump",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("CRC32: DEADBEEF")).toBeInTheDocument();
  });

  it("shows uploading state during upload", () => {
    mockUseReplaceRom.mockReturnValue({
      mutate: mockMutate,
      isPending: true,
      isError: false,
      error: null,
      reset: mockReset,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    expect(screen.getByTestId("upload-progress")).toBeInTheDocument();
  });

  it("shows error state on upload failure", () => {
    mockUseReplaceRom.mockReturnValue({
      mutate: mockMutate,
      isPending: false,
      isError: true,
      error: new Error("File too large"),
      reset: mockReset,
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    expect(screen.getByTestId("upload-error")).toBeInTheDocument();
    expect(screen.getByText("File too large")).toBeInTheDocument();
  });

  it("close button works in result view", async () => {
    const onClose = vi.fn();
    mockMutate.mockImplementation((_args: unknown, opts: { onSuccess: (data: unknown) => void }) => {
      opts.onSuccess({
        replacementResult: {
          verified: true,
          crc32: "ABCD1234",
          previousStatus: "unverified",
          previousCrc32: "00000000",
        },
      });
    });

    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} onClose={onClose} />
      </Wrapper>,
    );

    const file = new File(["rom content"], "game.nes", {
      type: "application/octet-stream",
    });
    const input = screen.getByTestId("file-input");
    await userEvent.upload(input, file);

    await waitFor(() => {
      expect(screen.getByTestId("replace-result")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText("Close"));
    expect(onClose).toHaveBeenCalled();
  });

  it("rejects invalid file extensions with feedback via drag-and-drop", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    const file = new File(["not a rom"], "readme.txt", {
      type: "text/plain",
    });
    const dropZone = screen.getByTestId("drop-zone");

    // Simulate drag-and-drop which bypasses the accept attribute
    fireEvent.drop(dropZone, {
      dataTransfer: { files: [file] },
    });

    await waitFor(() => {
      expect(screen.getByTestId("validation-error")).toBeInTheDocument();
    });
    expect(mockMutate).not.toHaveBeenCalled();
    expect(screen.getByText(/unsupported file type/i)).toBeInTheDocument();
  });

  it("accepts .zip files", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <ReplaceRomModal {...defaultProps} />
      </Wrapper>,
    );

    const file = new File(["zip content"], "game.zip", {
      type: "application/zip",
    });
    const input = screen.getByTestId("file-input");
    await userEvent.upload(input, file);

    expect(mockMutate).toHaveBeenCalled();
  });
});
