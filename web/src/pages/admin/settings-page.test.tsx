import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AdminSettingsPage } from "./settings-page";

const mockSettings = {
  registration_enabled: "true",
  scrapeOnScan: "true",
  igdb_client_id: "test-client-id",
  igdb_client_secret: "test-client-secret",
};

const mockSettingsEmpty = {
  registration_enabled: "true",
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
  useSteamGridDBStatus: vi.fn(),
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
  useSteamGridDBStatus,
} from "@/hooks/use-admin";

const mockUseServerSettings = useServerSettings as ReturnType<typeof vi.fn>;
const mockUseUpdateSettings = useUpdateSettings as ReturnType<typeof vi.fn>;
const mockUseTestIgdbCredentials = useTestIgdbCredentials as ReturnType<
  typeof vi.fn
>;
const mockUseIgdbStatus = useIgdbStatus as ReturnType<typeof vi.fn>;
const mockUseSteamGridDBStatus = useSteamGridDBStatus as ReturnType<
  typeof vi.fn
>;

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
  mockUseSteamGridDBStatus.mockReturnValue({
    data: { configured: false, source: "none" },
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

describe("AdminSettingsPage - No Game Directories UI", () => {
  it("does not render a Game Directories card", () => {
    renderPage();
    expect(screen.queryByText("Game Directories")).not.toBeInTheDocument();
  });

  it("does not render directory input or add button", () => {
    renderPage();
    expect(
      screen.queryByPlaceholderText("/path/to/games"),
    ).not.toBeInTheDocument();
  });

  it("does not include gameDirectories in save payload", async () => {
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: /Save/ }));
    expect(mockUpdateMutate).toHaveBeenCalledTimes(1);
    const payload = mockUpdateMutate.mock.calls[0][0] as Record<
      string,
      string
    >;
    expect(payload).not.toHaveProperty("gameDirectories");
  });
});

describe("AdminSettingsPage - Library Defaults", () => {
  it("renders default region select", () => {
    renderPage();
    expect(screen.getByText("Default Region")).toBeInTheDocument();
  });

  it("renders hide pre-release default toggle", () => {
    renderPage();
    expect(screen.getByText("Hide Pre-release by Default")).toBeInTheDocument();
  });

  it("initializes default region from settings", () => {
    mockUseServerSettings.mockReturnValue({
      data: { ...mockSettings, default_region: "Europe" },
      isLoading: false,
    });
    renderPage();
    const select = screen.getByLabelText("Default Region") as HTMLSelectElement;
    expect(select.value).toBe("Europe");
  });

  it("defaults to USA when no default_region setting", () => {
    renderPage();
    const select = screen.getByLabelText("Default Region") as HTMLSelectElement;
    expect(select.value).toBe("USA");
  });

  it("includes default_region and hide_pre_release_default in save payload", async () => {
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: /Save/ }));
    expect(mockUpdateMutate).toHaveBeenCalledTimes(1);
    const payload = mockUpdateMutate.mock.calls[0][0] as Record<string, string>;
    expect(payload).toHaveProperty("default_region", "USA");
    expect(payload).toHaveProperty("hide_pre_release_default", "true");
  });
});

describe("AdminSettingsPage - SteamGridDB env var detection", () => {
  it("shows env-configured message when SteamGridDB is set via env", () => {
    mockUseSteamGridDBStatus.mockReturnValue({
      data: { configured: true, source: "env" },
    });
    renderPage();
    expect(
      screen.getByText("Configured via environment variables"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("SPELA_STEAMGRIDDB_API_KEY"),
    ).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText("SteamGridDB API Key"),
    ).not.toBeInTheDocument();
  });

  it("shows input field when SteamGridDB is configured via database", () => {
    mockUseSteamGridDBStatus.mockReturnValue({
      data: { configured: true, source: "database" },
    });
    renderPage();
    expect(
      screen.getByPlaceholderText("SteamGridDB API Key"),
    ).toBeInTheDocument();
  });

  it("shows input field when SteamGridDB is not configured", () => {
    mockUseSteamGridDBStatus.mockReturnValue({
      data: { configured: false, source: "none" },
    });
    renderPage();
    expect(
      screen.getByPlaceholderText("SteamGridDB API Key"),
    ).toBeInTheDocument();
  });

  it("does not send steamgriddb_api_key in payload when configured via env", async () => {
    mockUseSteamGridDBStatus.mockReturnValue({
      data: { configured: true, source: "env" },
    });
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: /Save/ }));
    expect(mockUpdateMutate).toHaveBeenCalledTimes(1);
    const payload = mockUpdateMutate.mock.calls[0][0] as Record<
      string,
      string
    >;
    expect(payload).not.toHaveProperty("steamgriddb_api_key");
  });
});
