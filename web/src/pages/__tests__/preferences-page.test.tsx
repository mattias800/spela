import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { PreferencesPage } from "../preferences-page";

const mockPreferences = {
  showPerformanceOverlay: true,
  autoSaveEnabled: false,
  autoLoadSaveEnabled: true,
  selectedShader: "crt-simple",
  consoleShaders: { "1": "bilinear" },
  selectedKeyMapping: "default",
  customKeyMapping: {},
  consoleKeyMappings: {},
  raLinked: false,
  raUsername: "",
  raHardcoreEnabled: false,
};

const mockDevices = [
  {
    id: 1,
    userId: 10,
    deviceUuid: "uuid-abc",
    name: "My Phone",
    platform: "android",
    lastSeenAt: new Date().toISOString(),
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: new Date().toISOString(),
    consoleShaders: { "1": "crt-simple" },
  },
  {
    id: 2,
    userId: 10,
    deviceUuid: "uuid-def",
    name: "Desktop",
    platform: "linux",
    lastSeenAt: new Date().toISOString(),
    createdAt: "2025-01-02T00:00:00Z",
    updatedAt: new Date().toISOString(),
    consoleShaders: {},
  },
];

const mockConsoles = [
  {
    id: "1",
    name: "NES",
    abbreviation: "nes",
    extensions: [".nes"],
    defaultCore: "fceumm",
    coverAspectRatio: 0.75,
    colorTheme: "red",
    generation: 3,
    iconUrl: "",
    gameCount: 5,
    createdAt: "",
    updatedAt: "",
  },
  {
    id: "2",
    name: "SNES",
    abbreviation: "snes",
    extensions: [".sfc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "purple",
    generation: 4,
    iconUrl: "",
    gameCount: 3,
    createdAt: "",
    updatedAt: "",
  },
];

const mockMutate = vi.fn();
const mockUpdatePrefs = { mutate: mockMutate, isPending: false };
const mockUpdateDevice = { mutate: vi.fn(), isPending: false };
const mockDeleteDevice = { mutate: vi.fn(), isPending: false };
const mockUpdateDevicePrefs = { mutate: vi.fn(), isPending: false };

vi.mock("@/hooks/use-preferences", () => ({
  useUserPreferences: vi.fn(),
  useUpdatePreferences: vi.fn(),
}));

vi.mock("@/hooks/use-devices", () => ({
  useDevices: vi.fn(),
  useUpdateDevice: vi.fn(),
  useDeleteDevice: vi.fn(),
  useUpdateDevicePreferences: vi.fn(),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

vi.mock("@/hooks/use-retroachievements", () => ({
  useRAStatus: vi.fn(() => ({
    data: undefined,
    isLoading: false,
    isError: false,
  })),
  useLinkRA: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUnlinkRA: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useRASettings: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
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
  useUserPreferences,
  useUpdatePreferences,
} from "@/hooks/use-preferences";
import {
  useDevices,
  useUpdateDevice,
  useDeleteDevice,
  useUpdateDevicePreferences,
} from "@/hooks/use-devices";
import { useConsoles } from "@/hooks/use-consoles";

const mockUseUserPreferences = useUserPreferences as ReturnType<typeof vi.fn>;
const mockUseUpdatePreferences = useUpdatePreferences as ReturnType<
  typeof vi.fn
>;
const mockUseDevices = useDevices as ReturnType<typeof vi.fn>;
const mockUseUpdateDevice = useUpdateDevice as ReturnType<typeof vi.fn>;
const mockUseDeleteDevice = useDeleteDevice as ReturnType<typeof vi.fn>;
const mockUseUpdateDevicePreferences = useUpdateDevicePreferences as ReturnType<
  typeof vi.fn
>;
const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PreferencesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseUserPreferences.mockReturnValue({
    data: mockPreferences,
    isLoading: false,
  });
  mockUseUpdatePreferences.mockReturnValue(mockUpdatePrefs);
  mockUseDevices.mockReturnValue({ data: mockDevices, isLoading: false });
  mockUseUpdateDevice.mockReturnValue(mockUpdateDevice);
  mockUseDeleteDevice.mockReturnValue(mockDeleteDevice);
  mockUseUpdateDevicePreferences.mockReturnValue(mockUpdateDevicePrefs);
  mockUseConsoles.mockReturnValue({ data: mockConsoles });
});

describe("PreferencesPage", () => {
  it("renders page heading", () => {
    renderPage();
    expect(screen.getByText("Preferences")).toBeInTheDocument();
  });

  it("renders loading state for preferences", () => {
    mockUseUserPreferences.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByText("Emulation Settings")).toBeInTheDocument();
    // Skeleton elements should be present (no toggle switches)
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("displays current toggle preferences", () => {
    renderPage();
    const switches = screen.getAllByRole("switch");
    // Performance Overlay is true
    expect(switches[0]).toHaveAttribute("aria-checked", "true");
    // Auto Save is false
    expect(switches[1]).toHaveAttribute("aria-checked", "false");
    // Auto Load Save is true
    expect(switches[2]).toHaveAttribute("aria-checked", "true");
  });

  it("toggles a preference switch", async () => {
    renderPage();
    const switches = screen.getAllByRole("switch");
    await userEvent.click(switches[1]); // Auto Save (currently false)
    expect(mockMutate).toHaveBeenCalledWith(
      { autoSaveEnabled: true },
      expect.objectContaining({ onError: expect.any(Function) }),
    );
  });

  it("displays global shader selection", () => {
    renderPage();
    expect(screen.getByText("Global Default Shader")).toBeInTheDocument();
  });

  it("shows per-console shader table", () => {
    renderPage();
    const overrideLabels = screen.getAllByText("Per-Console Overrides");
    expect(overrideLabels.length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("NES").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("SNES").length).toBeGreaterThanOrEqual(1);
  });

  it("shows devices list", () => {
    renderPage();
    expect(screen.getByText("My Phone")).toBeInTheDocument();
    expect(screen.getByText("Desktop")).toBeInTheDocument();
  });

  it("shows empty state when no devices", () => {
    mockUseDevices.mockReturnValue({ data: [], isLoading: false });
    renderPage();
    expect(screen.getByText("No devices registered")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Connect with the Spela player app to register a device.",
      ),
    ).toBeInTheDocument();
  });

  it("renders loading state for devices", () => {
    mockUseDevices.mockReturnValue({ data: undefined, isLoading: true });
    renderPage();
    expect(screen.getByText("Devices")).toBeInTheDocument();
    // Should not show any device names while loading
    expect(screen.queryByText("My Phone")).not.toBeInTheDocument();
  });

  it("allows inline device rename", async () => {
    renderPage();
    // Click the rename (pencil) button on the first device
    const renameButtons = screen.getAllByTitle("Rename device");
    await userEvent.click(renameButtons[0]);

    // Should show an input with the current name
    const input = screen.getByDisplayValue("My Phone");
    expect(input).toBeInTheDocument();

    // Change the name and press Enter
    await userEvent.clear(input);
    await userEvent.type(input, "New Name{Enter}");

    expect(mockUpdateDevice.mutate).toHaveBeenCalledWith(
      { id: 1, name: "New Name" },
      expect.objectContaining({
        onSuccess: expect.any(Function),
        onError: expect.any(Function),
      }),
    );
  });

  it("cancels device rename on Escape", async () => {
    renderPage();
    const renameButtons = screen.getAllByTitle("Rename device");
    await userEvent.click(renameButtons[0]);

    screen.getByDisplayValue("My Phone");
    await userEvent.keyboard("{Escape}");

    // Input should be gone, original name shown
    expect(screen.queryByDisplayValue("My Phone")).not.toBeInTheDocument();
    expect(screen.getByText("My Phone")).toBeInTheDocument();
  });

  it("opens delete confirmation modal", async () => {
    renderPage();
    const deleteButtons = screen.getAllByTitle("Delete device");
    await userEvent.click(deleteButtons[0]);

    expect(
      screen.getByRole("heading", { name: "Delete Device" }),
    ).toBeInTheDocument();
    // The modal contains a confirmation message with the device name
    expect(screen.getByText(/This will remove/)).toBeInTheDocument();
  });

  it("confirms device deletion", async () => {
    renderPage();
    const deleteButtons = screen.getAllByTitle("Delete device");
    await userEvent.click(deleteButtons[0]);

    const deleteBtn = screen.getByRole("button", { name: "Delete Device" });
    await userEvent.click(deleteBtn);

    expect(mockDeleteDevice.mutate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        onSuccess: expect.any(Function),
        onError: expect.any(Function),
      }),
    );
  });

  it("cancels device deletion", async () => {
    renderPage();
    const deleteButtons = screen.getAllByTitle("Delete device");
    await userEvent.click(deleteButtons[0]);

    const cancelBtn = screen.getByRole("button", { name: "Cancel" });
    await userEvent.click(cancelBtn);

    // Modal should be gone
    expect(screen.queryByText("Delete Device")).not.toBeInTheDocument();
  });

  it("expands device to show shader overrides", async () => {
    renderPage();
    const expandButtons = screen.getAllByTitle("Shader overrides");
    await userEvent.click(expandButtons[0]);

    expect(screen.getByText("Device Shader Overrides")).toBeInTheDocument();
  });
});
