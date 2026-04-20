import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AdminSystemEventsPage } from "../system-events-page";
import type { SystemEventsListResponse } from "@/types/api";

vi.mock("@/hooks/use-system-events", () => ({
  useSystemEvents: vi.fn(),
  useSystemEvent: vi.fn(),
  useSystemEventTypes: vi.fn(() => ({ data: undefined })),
  useSystemEventCategories: vi.fn(() => ({ data: undefined })),
  useDismissSystemEvent: vi.fn(() => ({ mutate: vi.fn() })),
}));

import { useSystemEvents } from "@/hooks/use-system-events";

const mockUseSystemEvents = useSystemEvents as ReturnType<typeof vi.fn>;

function renderPage(initialPath = "/admin/system-events") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AdminSystemEventsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const sampleResponse: SystemEventsListResponse = {
  data: [
    {
      id: 1,
      createdAt: "2026-04-10T09:00:00Z",
      categoryCode: "security",
      categoryName: "Security",
      eventType: "login_failed",
      reason: "bad_password",
      username: "alice",
      userId: 0,
      ip: "10.0.0.1",
      path: "",
      metadata: { failedCount: 3 },
      metadataRaw: "",
      dismissedAt: null,
    },
    {
      id: 2,
      createdAt: "2026-04-10T08:55:00Z",
      categoryCode: "security",
      categoryName: "Security",
      eventType: "account_locked",
      reason: "",
      username: "alice",
      userId: 0,
      ip: "10.0.0.1",
      path: "",
      metadata: { failedCount: 5, lockedUntil: "2026-04-10T09:10:00Z" },
      metadataRaw: "",
      dismissedAt: null,
    },
  ],
  total: 2,
  page: 1,
  pageSize: 50,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSystemEvents.mockReturnValue({
    data: sampleResponse,
    isLoading: false,
  });
});

describe("AdminSystemEventsPage", () => {
  it("renders heading and description", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /System Events/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Audit log of system events/),
    ).toBeInTheDocument();
  });

  it("renders events in the table", () => {
    renderPage();
    expect(screen.getAllByText("alice")).toHaveLength(2);
    expect(screen.getAllByText("10.0.0.1")).toHaveLength(2);
    expect(screen.getAllByText("Login failed").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Account locked").length).toBeGreaterThanOrEqual(1);
  });

  it("shows total count", () => {
    renderPage();
    expect(screen.getByText(/2 events/)).toBeInTheDocument();
  });

  it("shows loading state when isLoading is true", () => {
    mockUseSystemEvents.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.queryByText("No system events")).not.toBeInTheDocument();
  });

  it("shows empty state when no events match", () => {
    mockUseSystemEvents.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 50 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No system events")).toBeInTheDocument();
  });

  it("uses 24h as default time range", () => {
    renderPage();
    expect(mockUseSystemEvents).toHaveBeenCalledWith(
      expect.objectContaining({ since: "24h" }),
    );
  });

  it("reads filters from URL query params", () => {
    renderPage(
      "/admin/system-events?eventType=login_failed&username=alice&ip=10&since=7d",
    );
    expect(mockUseSystemEvents).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: ["login_failed"],
        username: "alice",
        ip: "10",
        since: "7d",
      }),
    );
  });

  it("toggling an event type chip updates the query", async () => {
    const user = userEvent.setup();
    renderPage();
    const chips = screen.getAllByRole("button", { name: /Login failed/ });
    await user.click(chips[0]);

    await waitFor(() => {
      expect(mockUseSystemEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({
          eventType: ["login_failed"],
        }),
      );
    });
  });

  it("opens detail modal when clicking a row", async () => {
    const user = userEvent.setup();
    renderPage();

    const rows = screen.getAllByRole("row");
    await user.click(rows[1]);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    expect(screen.getByText(/Metadata/)).toBeInTheDocument();
  });

  it("renders a Clear button when filters are active", () => {
    renderPage("/admin/system-events?username=alice");
    expect(
      screen.getByRole("button", { name: /Clear/ }),
    ).toBeInTheDocument();
  });

  it("does not render a Clear button on default view", () => {
    renderPage();
    expect(screen.queryByRole("button", { name: /^Clear$/ })).not.toBeInTheDocument();
  });

  it("renders a distinct error state on API failure", () => {
    mockUseSystemEvents.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("boom"),
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("system-events-error")).toBeInTheDocument();
    expect(screen.getByText(/Failed to load system events/)).toBeInTheDocument();
    expect(screen.getByText(/boom/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Try again/ })).toBeInTheDocument();
  });

  it("hides the event count row when empty", () => {
    mockUseSystemEvents.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 50 },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByText(/0 events/)).not.toBeInTheDocument();
  });

  it("does not open detail modal when click is finishing a text selection", async () => {
    const user = userEvent.setup();
    renderPage();

    const originalGetSelection = window.getSelection.bind(window);
    window.getSelection = () =>
      ({ toString: () => "10.0.0.1" }) as unknown as Selection;

    const rows = screen.getAllByRole("row");
    await user.click(rows[1]);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    window.getSelection = originalGetSelection;
  });

  it("detail modal shows pivot actions for IP and username", async () => {
    const user = userEvent.setup();
    renderPage();

    const rows = screen.getAllByRole("row");
    await user.click(rows[1]);

    await waitFor(() => {
      expect(screen.getByTestId("system-event-pivot-actions")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /View all events from user alice/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /View all events from IP 10\.0\.0\.1/ }),
    ).toBeInTheDocument();
  });

  it("clicking the username pivot narrows the filters and closes the modal", async () => {
    const user = userEvent.setup();
    renderPage();

    const rows = screen.getAllByRole("row");
    await user.click(rows[1]);
    await waitFor(() =>
      expect(screen.getByRole("dialog")).toBeInTheDocument(),
    );

    await user.click(
      screen.getByRole("button", { name: /View all events from user alice/ }),
    );

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(mockUseSystemEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({
          username: "alice",
          since: "all",
          eventType: [],
        }),
      );
    });
  });

  it("clicking the IP pivot narrows the filters and closes the modal", async () => {
    const user = userEvent.setup();
    renderPage();

    const rows = screen.getAllByRole("row");
    await user.click(rows[1]);
    await waitFor(() =>
      expect(screen.getByRole("dialog")).toBeInTheDocument(),
    );

    await user.click(
      screen.getByRole("button", { name: /View all events from IP 10\.0\.0\.1/ }),
    );

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(mockUseSystemEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({
          ip: "10.0.0.1",
          since: "all",
          eventType: [],
        }),
      );
    });
  });
});
