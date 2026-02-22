import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AdminSettingsPage } from "./settings-page";

const mockSettings = {
  gameDirectories: "/games",
  allowRegistration: "true",
  scrapeOnScan: "true",
  igdb_client_id: "test-client-id",
  igdb_client_secret: "test-client-secret",
};

const mockSettingsEmpty = {
  gameDirectories: "/games",
  allowRegistration: "true",
  scrapeOnScan: "true",
  igdb_client_id: "",
  igdb_client_secret: "",
};

const mockUpdateMutate = vi.fn();
const mockTestMutate = vi.fn();

vi.mock("@/hooks/use-admin", () => ({
  useServerSettings: vi.fn(),
  useUpdateSettings: vi.fn(),
  useTestIgdbCredentials: vi.fn(),
  useIgdbStatus: vi.fn(),
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
  useServerSettings,
  useUpdateSettings,
  useTestIgdbCredentials,
  useIgdbStatus,
} from "@/hooks/use-admin";

const mockUseServerSettings = useServerSettings as ReturnType<typeof vi.fn>;
const mockUseUpdateSettings = useUpdateSettings as ReturnType<typeof vi.fn>;
const mockUseTestIgdbCredentials = useTestIgdbCredentials as ReturnType<
  typeof vi.fn
>;
const mockUseIgdbStatus = useIgdbStatus as ReturnType<typeof vi.fn>;

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminSettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseServerSettings.mockReturnValue({
    data: mockSettings,
    isLoading: false,
  });
  mockUseUpdateSettings.mockReturnValue({
    mutate: mockUpdateMutate,
    isPending: false,
  });
  mockUseTestIgdbCredentials.mockReturnValue({
    mutate: mockTestMutate,
    isPending: false,
  });
  mockUseIgdbStatus.mockReturnValue({
    data: { configured: true, status: "connected" },
  });
});

describe("AdminSettingsPage - IGDB Configuration", () => {
  it("renders the IGDB Configuration card with Client ID and Client Secret fields", () => {
    renderPage();
    expect(screen.getByText("IGDB Configuration")).toBeInTheDocument();
    expect(screen.getByText("Client ID")).toBeInTheDocument();
    expect(screen.getByText("Client Secret")).toBeInTheDocument();
  });

  it("renders Test Connection button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /Test Connection/ }),
    ).toBeInTheDocument();
  });

  it("disables Test Connection button when both fields are empty", () => {
    mockUseServerSettings.mockReturnValue({
      data: mockSettingsEmpty,
      isLoading: false,
    });
    renderPage();
    expect(
      screen.getByRole("button", { name: /Test Connection/ }),
    ).toBeDisabled();
  });

  it("disables Test Connection button when Client ID is empty", () => {
    mockUseServerSettings.mockReturnValue({
      data: { ...mockSettings, igdb_client_id: "" },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.getByRole("button", { name: /Test Connection/ }),
    ).toBeDisabled();
  });

  it("disables Test Connection button when Client Secret is empty", () => {
    mockUseServerSettings.mockReturnValue({
      data: { ...mockSettings, igdb_client_secret: "" },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.getByRole("button", { name: /Test Connection/ }),
    ).toBeDisabled();
  });

  it("enables Test Connection button when both fields are populated", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /Test Connection/ }),
    ).toBeEnabled();
  });

  it("shows success badge on successful test", async () => {
    mockTestMutate.mockImplementation(
      (
        _data: unknown,
        options: { onSuccess: (data: { success: boolean }) => void },
      ) => {
        options.onSuccess({ success: true });
      },
    );
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Test Connection/ }),
    );
    expect(screen.getByText("Authenticated")).toBeInTheDocument();
  });

  it("shows error badge on failed test", async () => {
    mockTestMutate.mockImplementation(
      (
        _data: unknown,
        options: {
          onSuccess: (data: { success: boolean; error: string }) => void;
        },
      ) => {
        options.onSuccess({
          success: false,
          error: "Invalid credentials",
        });
      },
    );
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Test Connection/ }),
    );
    expect(screen.getByText("Invalid credentials")).toBeInTheDocument();
  });

  it("shows error badge on connection failure", async () => {
    mockTestMutate.mockImplementation(
      (_data: unknown, options: { onError: () => void }) => {
        options.onError();
      },
    );
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: /Test Connection/ }),
    );
    expect(screen.getByText("Connection failed")).toBeInTheDocument();
  });

  it("clears badge when Client ID input changes", async () => {
    mockTestMutate.mockImplementation(
      (
        _data: unknown,
        options: { onSuccess: (data: { success: boolean }) => void },
      ) => {
        options.onSuccess({ success: true });
      },
    );
    renderPage();

    await userEvent.click(
      screen.getByRole("button", { name: /Test Connection/ }),
    );
    expect(screen.getByText("Authenticated")).toBeInTheDocument();

    const clientIdInput = screen.getByPlaceholderText("Twitch Client ID");
    await userEvent.type(clientIdInput, "x");
    expect(screen.queryByText("Authenticated")).not.toBeInTheDocument();
  });

  it("clears badge when Client Secret input changes", async () => {
    mockTestMutate.mockImplementation(
      (
        _data: unknown,
        options: { onSuccess: (data: { success: boolean }) => void },
      ) => {
        options.onSuccess({ success: true });
      },
    );
    renderPage();

    await userEvent.click(
      screen.getByRole("button", { name: /Test Connection/ }),
    );
    expect(screen.getByText("Authenticated")).toBeInTheDocument();

    const secretInput = screen.getByPlaceholderText("Twitch Client Secret");
    await userEvent.type(secretInput, "x");
    expect(screen.queryByText("Authenticated")).not.toBeInTheDocument();
  });
});

describe("AdminSettingsPage - Warning banner", () => {
  it("shows IGDB warning banner when not configured", () => {
    mockUseIgdbStatus.mockReturnValue({
      data: { configured: false, status: "not_configured" },
    });
    renderPage();
    expect(screen.getByTestId("igdb-warning-banner")).toBeInTheDocument();
    expect(
      screen.getByText(/IGDB credentials have not been configured/),
    ).toBeInTheDocument();
  });

  it("does not show IGDB warning banner when configured", () => {
    mockUseIgdbStatus.mockReturnValue({
      data: { configured: true, status: "connected" },
    });
    renderPage();
    expect(screen.queryByTestId("igdb-warning-banner")).not.toBeInTheDocument();
  });
});

describe("AdminSettingsPage - No ScreenScraper UI", () => {
  it("does not render any ScreenScraper elements", () => {
    renderPage();
    expect(screen.queryByText(/ScreenScraper/)).not.toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText(/ScreenScraper/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Test Credentials/)).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Default Scraper Source/),
    ).not.toBeInTheDocument();
  });
});
