import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { UploadRomsPage } from "../upload-roms-page";

const mockUploadMutate = vi.fn();

vi.mock("@/hooks/use-uploads", () => ({
  useUploadWritable: vi.fn(),
  useStagedUploads: vi.fn(),
  useUploadRoms: vi.fn(),
  useScrapeAllUploads: vi.fn(),
  useAcceptUpload: vi.fn(),
  useRejectUpload: vi.fn(),
  useAcceptAllUploads: vi.fn(),
  useRejectAllUploads: vi.fn(),
  useClearStaging: vi.fn(),
  useSetUploadConsole: vi.fn(),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

import {
  useUploadWritable,
  useStagedUploads,
  useUploadRoms,
  useScrapeAllUploads,
  useAcceptUpload,
  useRejectUpload,
  useAcceptAllUploads,
  useRejectAllUploads,
  useClearStaging,
  useSetUploadConsole,
} from "@/hooks/use-uploads";

const mockUseUploadWritable = useUploadWritable as ReturnType<typeof vi.fn>;
const mockUseStagedUploads = useStagedUploads as ReturnType<typeof vi.fn>;
const mockUseUploadRoms = useUploadRoms as ReturnType<typeof vi.fn>;
const mockUseScrapeAllUploads = useScrapeAllUploads as ReturnType<typeof vi.fn>;
const mockUseAcceptUpload = useAcceptUpload as ReturnType<typeof vi.fn>;
const mockUseRejectUpload = useRejectUpload as ReturnType<typeof vi.fn>;
const mockUseAcceptAllUploads = useAcceptAllUploads as ReturnType<typeof vi.fn>;
const mockUseRejectAllUploads = useRejectAllUploads as ReturnType<typeof vi.fn>;
const mockUseClearStaging = useClearStaging as ReturnType<typeof vi.fn>;
const mockUseSetUploadConsole = useSetUploadConsole as ReturnType<typeof vi.fn>;

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <UploadRomsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function setupDefaultMocks() {
  mockUseUploadWritable.mockReturnValue({
    data: { writable: true },
  });
  mockUseStagedUploads.mockReturnValue({
    data: [],
    isLoading: false,
  });
  mockUseUploadRoms.mockReturnValue({
    mutateAsync: mockUploadMutate,
    isPending: false,
  });
  mockUseScrapeAllUploads.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseAcceptUpload.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseRejectUpload.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseAcceptAllUploads.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseRejectAllUploads.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseClearStaging.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
  mockUseSetUploadConsole.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  setupDefaultMocks();
});

describe("UploadRomsPage", () => {
  it("renders page heading and drop zone when writable", () => {
    renderPage();
    expect(screen.getByText("Upload ROMs")).toBeInTheDocument();
    expect(screen.getByTestId("upload-drop-zone")).toBeInTheDocument();
    expect(screen.queryByTestId("readonly-warning")).not.toBeInTheDocument();
  });

  it("shows readonly warning when library is not writable", () => {
    mockUseUploadWritable.mockReturnValue({
      data: { writable: false, reason: "Game library directory is read-only" },
    });
    renderPage();
    expect(screen.getByTestId("readonly-warning")).toBeInTheDocument();
    expect(screen.getByText("Uploads unavailable")).toBeInTheDocument();
    expect(
      screen.getByText(/Game library directory is read-only/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Check that the game library directory/),
    ).toBeInTheDocument();
  });

  it("disables upload when library is not writable", () => {
    mockUseUploadWritable.mockReturnValue({
      data: { writable: false, reason: "Game library directory is read-only" },
    });
    renderPage();
    const selectButton = screen.getByRole("button", { name: /Select Files/ });
    expect(selectButton).toBeDisabled();
  });

  it("does not show readonly warning when writable data is still loading", () => {
    mockUseUploadWritable.mockReturnValue({
      data: undefined,
    });
    renderPage();
    expect(screen.queryByTestId("readonly-warning")).not.toBeInTheDocument();
  });

  it("shows empty state when no staged uploads", () => {
    renderPage();
    expect(screen.getByText("No staged uploads")).toBeInTheDocument();
  });
});
