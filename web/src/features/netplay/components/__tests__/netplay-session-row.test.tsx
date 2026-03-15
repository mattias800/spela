import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NetplaySessionRow } from "../netplay-session-row";
import type { NetplaySession } from "@/types/api";

const mockMutate = vi.fn();

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "u1", username: "alice", role: "user" },
  })),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

vi.mock("@/hooks/use-netplay", () => ({
  useDeleteNetplaySession: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}));

const baseSession: NetplaySession = {
  id: "s1",
  hostId: "u1",
  hostUsername: "alice",
  hostAvatarUrl: null,
  clientId: "u2",
  clientUsername: "bob",
  clientAvatarUrl: null,
  gameId: "g1",
  gameTitle: "Super Mario World",
  gameCoverUrl: null,
  consoleName: "SNES",
  coverAspectRatio: 1,
  status: "ended",
  endReason: "completed",
  inputDelay: 2,
  inviteCode: "ABC123",
  createdAt: "2026-03-01T10:00:00Z",
  startedAt: "2026-03-01T10:01:00Z",
  endedAt: "2026-03-01T11:00:00Z",
};

function renderRow(session: NetplaySession = baseSession) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NetplaySessionRow session={session} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("NetplaySessionRow", () => {
  it("renders game title and badges", () => {
    renderRow();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("SNES")).toBeInTheDocument();
  });

  it("renders host and client usernames", () => {
    renderRow();
    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
  });

  it("shows delete button for ended sessions where user is host", () => {
    renderRow();
    expect(screen.getByLabelText("Delete session")).toBeInTheDocument();
  });

  it("does not show delete button for waiting sessions", () => {
    renderRow({ ...baseSession, status: "waiting" });
    expect(screen.queryByLabelText("Delete session")).not.toBeInTheDocument();
  });

  it("does not show delete button for in-progress sessions", () => {
    renderRow({ ...baseSession, status: "in_progress" });
    expect(screen.queryByLabelText("Delete session")).not.toBeInTheDocument();
  });

  it("does not show delete button when user is not the host", () => {
    renderRow({ ...baseSession, hostId: "other-user" });
    expect(screen.queryByLabelText("Delete session")).not.toBeInTheDocument();
  });

  it("opens confirm modal when delete button is clicked", () => {
    renderRow();
    fireEvent.click(screen.getByLabelText("Delete session"));
    expect(screen.getByText("Delete Session")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Permanently delete this netplay session. This action cannot be undone.",
      ),
    ).toBeInTheDocument();
  });

  it("calls delete mutation when confirmed", () => {
    renderRow();
    fireEvent.click(screen.getByLabelText("Delete session"));
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(mockMutate).toHaveBeenCalledWith("s1", expect.any(Object));
  });

  it("closes modal when cancel is clicked", () => {
    renderRow();
    fireEvent.click(screen.getByLabelText("Delete session"));
    expect(screen.getByText("Delete Session")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByText("Delete Session")).not.toBeInTheDocument();
  });

  it("shows invite code for waiting sessions", () => {
    renderRow({ ...baseSession, status: "waiting" });
    expect(screen.getByText("ABC123")).toBeInTheDocument();
  });
});
