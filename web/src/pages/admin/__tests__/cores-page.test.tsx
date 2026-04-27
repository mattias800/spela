import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { CoresPage } from "../cores-page";

vi.mock("@/hooks/use-admin", () => ({
  useAdminCores: vi.fn(),
  useRefreshCore: vi.fn(),
}));

// `useToast` is used for the refresh success/error feedback. Stub it so
// tests don't need to wrap in a real ToastProvider.
vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

import { useAdminCores, useRefreshCore } from "@/hooks/use-admin";
const mockUseAdminCores = useAdminCores as ReturnType<typeof vi.fn>;
const mockUseRefreshCore = useRefreshCore as ReturnType<typeof vi.fn>;

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/admin/cores"]}>
        <CoresPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const fetchedAt = "2026-04-20T12:00:00Z";

const fingerprintedCore = {
  id: 1,
  name: "nestopia",
  displayName: "Nestopia UE",
  description: "Nintendo Entertainment System emulator",
  version: "1.52",
  platforms: "windows,linux,macos,android",
  customDownloadUrl: "",
  sha256: "ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12",
  sizeBytes: 4_400_000,
  fetchedAt,
  sourceUrl: "https://buildbot.libretro.com/nightly/linux/x86_64/latest/nestopia_libretro.so.zip",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-04-20T12:00:00Z",
};

const pristineCore = {
  ...fingerprintedCore,
  id: 2,
  name: "mednafen_psx_hw",
  displayName: "Mednafen PSX HW",
  sha256: "",
  sizeBytes: 0,
  fetchedAt: null,
  sourceUrl: "",
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUseRefreshCore.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    variables: undefined,
  });
});

describe("CoresPage", () => {
  it("renders the loading skeleton while data is pending", () => {
    mockUseAdminCores.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders fingerprint data for a fetched core", () => {
    mockUseAdminCores.mockReturnValue({
      data: [fingerprintedCore],
      isLoading: false,
      isError: false,
    });
    renderPage();

    const row = screen.getByTestId("cores-row-nestopia");
    expect(row).toBeInTheDocument();
    expect(row).toHaveTextContent("Nestopia UE");
    expect(row).toHaveTextContent("nestopia");
    // SHA prefix only, not full 64 chars, and a trailing ellipsis.
    expect(row).toHaveTextContent("ab12cd34ef56…");
    expect(row).not.toHaveTextContent(fingerprintedCore.sha256);
    // File size formatted.
    expect(row).toHaveTextContent(/4\.2 MB/);
  });

  it("shows the 'not fetched' placeholders for cores with no recorded hash", () => {
    mockUseAdminCores.mockReturnValue({
      data: [pristineCore],
      isLoading: false,
      isError: false,
    });
    renderPage();

    const row = screen.getByTestId("cores-row-mednafen_psx_hw");
    expect(row).toHaveTextContent(/not fetched/);
    expect(row).toHaveTextContent(/never/);
  });

  it("surfaces a banner when at least one core has not been fetched", () => {
    mockUseAdminCores.mockReturnValue({
      data: [fingerprintedCore, pristineCore],
      isLoading: false,
      isError: false,
    });
    renderPage();

    expect(
      screen.getByTestId("cores-never-fetched-banner"),
    ).toHaveTextContent(/1 core has no recorded hash/);
  });

  it("does not render the banner when every core has been fetched", () => {
    mockUseAdminCores.mockReturnValue({
      data: [fingerprintedCore],
      isLoading: false,
      isError: false,
    });
    renderPage();

    expect(
      screen.queryByTestId("cores-never-fetched-banner"),
    ).not.toBeInTheDocument();
  });

  it("shows the error state when the API call fails", () => {
    mockUseAdminCores.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    });
    renderPage();

    expect(screen.getByText(/Failed to load cores/i)).toBeInTheDocument();
  });

  it("shows the empty state when no cores are registered", () => {
    mockUseAdminCores.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    });
    renderPage();

    expect(
      screen.getByText(/No cores registered/i),
    ).toBeInTheDocument();
  });

  it("calls the refresh mutation with the row's id when the button is clicked", async () => {
    const mutate = vi.fn();
    mockUseRefreshCore.mockReturnValue({
      mutate,
      isPending: false,
      variables: undefined,
    });
    mockUseAdminCores.mockReturnValue({
      data: [fingerprintedCore],
      isLoading: false,
      isError: false,
    });
    renderPage();

    await userEvent.click(screen.getByTestId("cores-refresh-nestopia"));

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0][0]).toEqual({ id: 1 });
  });

  it("disables only the refreshing row's button while the mutation is in flight", () => {
    mockUseRefreshCore.mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
      variables: { id: 1 }, // refreshing nestopia
    });
    mockUseAdminCores.mockReturnValue({
      data: [fingerprintedCore, pristineCore],
      isLoading: false,
      isError: false,
    });
    renderPage();

    expect(screen.getByTestId("cores-refresh-nestopia")).toBeDisabled();
    // The other row's button must remain interactive — users can queue
    // manual refreshes for different cores in parallel.
    expect(screen.getByTestId("cores-refresh-mednafen_psx_hw")).not.toBeDisabled();
  });
});
