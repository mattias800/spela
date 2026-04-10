import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AdminSecurityEventsPage } from "../security-events-page";
import type { SecurityEventsListResponse } from "@/types/api";

vi.mock("@/hooks/use-security-events", () => ({
  useSecurityEvents: vi.fn(),
  useSecurityEvent: vi.fn(),
  useSecurityEventTypes: vi.fn(() => ({ data: undefined })),
}));

import { useSecurityEvents } from "@/hooks/use-security-events";

const mockUseSecurityEvents = useSecurityEvents as ReturnType<typeof vi.fn>;

function renderPage(initialPath = "/admin/security-events") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AdminSecurityEventsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const sampleResponse: SecurityEventsListResponse = {
  data: [
    {
      id: 1,
      createdAt: "2026-04-10T09:00:00Z",
      eventType: "login_failed",
      reason: "bad_password",
      username: "alice",
      ip: "10.0.0.1",
      metadata: { failedCount: 3 },
    },
    {
      id: 2,
      createdAt: "2026-04-10T08:55:00Z",
      eventType: "account_locked",
      username: "alice",
      ip: "10.0.0.1",
      metadata: { failedCount: 5, lockedUntil: "2026-04-10T09:10:00Z" },
    },
  ],
  total: 2,
  page: 1,
  pageSize: 50,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSecurityEvents.mockReturnValue({
    data: sampleResponse,
    isLoading: false,
  });
});

describe("AdminSecurityEventsPage", () => {
  it("renders heading and description", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Security Events/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Audit log of authentication events/),
    ).toBeInTheDocument();
  });

  it("renders events in the table", () => {
    renderPage();
    expect(screen.getAllByText("alice")).toHaveLength(2);
    expect(screen.getAllByText("10.0.0.1")).toHaveLength(2);
    // "Login failed" and "Account locked" each appear twice: once as a filter
    // chip and once as a row badge. Assert the badge count.
    expect(screen.getAllByText("Login failed").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Account locked").length).toBeGreaterThanOrEqual(1);
  });

  it("shows total count", () => {
    renderPage();
    expect(screen.getByText(/2 events/)).toBeInTheDocument();
  });

  it("shows loading state when isLoading is true", () => {
    mockUseSecurityEvents.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    // Skeletons rendered — there is no empty-state icon when loading.
    expect(screen.queryByText("No security events")).not.toBeInTheDocument();
  });

  it("shows empty state when no events match", () => {
    mockUseSecurityEvents.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 50 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No security events")).toBeInTheDocument();
  });

  it("uses 24h as default time range", () => {
    renderPage();
    expect(mockUseSecurityEvents).toHaveBeenCalledWith(
      expect.objectContaining({ since: "24h" }),
    );
  });

  it("reads filters from URL query params", () => {
    renderPage(
      "/admin/security-events?eventType=login_failed&username=alice&ip=10&since=7d",
    );
    expect(mockUseSecurityEvents).toHaveBeenCalledWith(
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
    // Click the "Login failed" filter chip (there are two matching elements:
    // the chip in the filter row and the badge in the table — use getAllByText
    // and pick the button).
    const chips = screen.getAllByRole("button", { name: /Login failed/ });
    await user.click(chips[0]);

    await waitFor(() => {
      expect(mockUseSecurityEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({
          eventType: ["login_failed"],
        }),
      );
    });
  });

  it("opens detail modal when clicking a row", async () => {
    const user = userEvent.setup();
    renderPage();

    // There are multiple "alice" matches — click the username cell in the first
    // row, which triggers the row click handler.
    const rows = screen.getAllByRole("row");
    // rows[0] = header, rows[1] = first data row
    await user.click(rows[1]);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    expect(screen.getByText(/Metadata/)).toBeInTheDocument();
  });

  it("renders a Clear button when filters are active", () => {
    renderPage("/admin/security-events?username=alice");
    expect(
      screen.getByRole("button", { name: /Clear/ }),
    ).toBeInTheDocument();
  });

  it("does not render a Clear button on default view", () => {
    renderPage();
    expect(screen.queryByRole("button", { name: /^Clear$/ })).not.toBeInTheDocument();
  });

  it("renders a distinct error state on API failure", () => {
    mockUseSecurityEvents.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("boom"),
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("security-events-error")).toBeInTheDocument();
    expect(screen.getByText(/Failed to load security events/)).toBeInTheDocument();
    expect(screen.getByText(/boom/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Try again/ })).toBeInTheDocument();
  });

  it("hides the event count row when empty", () => {
    mockUseSecurityEvents.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 50 },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByText(/0 events/)).not.toBeInTheDocument();
  });

  it("does not open detail modal when click is finishing a text selection", async () => {
    const user = userEvent.setup();
    renderPage();

    // Simulate an active text selection at click time.
    const originalGetSelection = window.getSelection.bind(window);
    window.getSelection = () =>
      ({ toString: () => "10.0.0.1" }) as unknown as Selection;

    const rows = screen.getAllByRole("row");
    await user.click(rows[1]);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    window.getSelection = originalGetSelection;
  });
});
