import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RetroAchievementsCard } from "../retroachievements-card";

const mockMutate = vi.fn();
const mockUnlinkMutate = vi.fn();
const mockSettingsMutate = vi.fn();

vi.mock("@/hooks/use-retroachievements", () => ({
  useRAStatus: vi.fn(),
  useLinkRA: vi.fn(),
  useUnlinkRA: vi.fn(),
  useRASettings: vi.fn(),
}));

import { useRAStatus, useLinkRA, useUnlinkRA, useRASettings } from "@/hooks/use-retroachievements";

const mockUseRAStatus = useRAStatus as ReturnType<typeof vi.fn>;
const mockUseLinkRA = useLinkRA as ReturnType<typeof vi.fn>;
const mockUseUnlinkRA = useUnlinkRA as ReturnType<typeof vi.fn>;
const mockUseRASettings = useRASettings as ReturnType<typeof vi.fn>;

function renderCard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RetroAchievementsCard />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseLinkRA.mockReturnValue({ mutate: mockMutate, isPending: false });
  mockUseUnlinkRA.mockReturnValue({ mutate: mockUnlinkMutate, isPending: false });
  mockUseRASettings.mockReturnValue({ mutate: mockSettingsMutate, isPending: false });
});

describe("RetroAchievementsCard", () => {
  it("renders loading state", () => {
    mockUseRAStatus.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    renderCard();
    expect(screen.getByText("RetroAchievements")).toBeInTheDocument();
    expect(screen.queryByTestId("ra-username-input")).not.toBeInTheDocument();
  });

  it("renders error state", () => {
    mockUseRAStatus.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    renderCard();
    expect(screen.getByText("Failed to load RetroAchievements status.")).toBeInTheDocument();
  });

  it("renders unlinked state with form", () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: false, username: "", hardcoreEnabled: false },
      isLoading: false,
      isError: false,
    });
    renderCard();

    expect(screen.getByTestId("ra-username-input")).toBeInTheDocument();
    expect(screen.getByTestId("ra-password-input")).toBeInTheDocument();
    expect(screen.getByTestId("link-ra-btn")).toBeInTheDocument();
    expect(screen.getByText("Your password is only used to obtain a token and is never stored.")).toBeInTheDocument();
  });

  it("disables link button when fields are empty", () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: false, username: "", hardcoreEnabled: false },
      isLoading: false,
      isError: false,
    });
    renderCard();

    expect(screen.getByTestId("link-ra-btn")).toBeDisabled();
  });

  it("submits link form with credentials", async () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: false, username: "", hardcoreEnabled: false },
      isLoading: false,
      isError: false,
    });
    renderCard();

    await userEvent.type(screen.getByTestId("ra-username-input"), "player1");
    await userEvent.type(screen.getByTestId("ra-password-input"), "secret123");
    await userEvent.click(screen.getByTestId("link-ra-btn"));

    expect(mockMutate).toHaveBeenCalledWith(
      { username: "player1", password: "secret123" },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it("renders linked state with username and controls", () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: true, username: "player1", hardcoreEnabled: false },
      isLoading: false,
      isError: false,
    });
    renderCard();

    expect(screen.getByText("player1")).toBeInTheDocument();
    expect(screen.getByText("Linked Account")).toBeInTheDocument();
    expect(screen.getByTestId("unlink-ra-btn")).toBeInTheDocument();
    expect(screen.getByText("Hardcore Mode")).toBeInTheDocument();
  });

  it("shows hardcore toggle in correct state", () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: true, username: "player1", hardcoreEnabled: true },
      isLoading: false,
      isError: false,
    });
    renderCard();

    const toggle = screen.getByRole("switch");
    expect(toggle).toHaveAttribute("aria-checked", "true");
  });

  it("toggles hardcore mode", async () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: true, username: "player1", hardcoreEnabled: false },
      isLoading: false,
      isError: false,
    });
    renderCard();

    await userEvent.click(screen.getByRole("switch"));
    expect(mockSettingsMutate).toHaveBeenCalledWith({ hardcoreEnabled: true });
  });

  it("calls unlink on button click", async () => {
    mockUseRAStatus.mockReturnValue({
      data: { linked: true, username: "player1", hardcoreEnabled: false },
      isLoading: false,
      isError: false,
    });
    renderCard();

    await userEvent.click(screen.getByTestId("unlink-ra-btn"));
    expect(mockUnlinkMutate).toHaveBeenCalledWith(
      undefined,
      expect.objectContaining({ onError: expect.any(Function) }),
    );
  });
});
