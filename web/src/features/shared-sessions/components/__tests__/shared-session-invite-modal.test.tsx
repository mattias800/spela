import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SharedSessionInviteModal } from "../shared-session-invite-modal";

const mockMutate = vi.fn();

vi.mock("@/hooks/use-shared-sessions", () => ({
  useInviteToSharedSession: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}));

const mockUsers = [
  { id: "1", username: "charlie" },
  { id: "2", username: "charlotte" },
];

vi.mock("@/hooks/use-social", () => ({
  useSearchUsers: vi.fn(() => ({
    data: {
      data: mockUsers,
      total: 2,
      page: 1,
      pageSize: 10,
    },
    isLoading: false,
  })),
  useRecentPartners: vi.fn(() => ({
    data: [],
    isLoading: false,
  })),
}));

vi.mock("@/hooks/use-debounced-value", () => ({
  useDebouncedValue: vi.fn((value: string) => value),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("SharedSessionInviteModal", () => {
  it("renders when open", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <SharedSessionInviteModal
          sharedSessionId="ss-1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.getByText("Invite Players")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Search users...")).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <SharedSessionInviteModal
          sharedSessionId="ss-1"
          open={false}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.queryByText("Invite Players")).not.toBeInTheDocument();
  });

  it("shows user list", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <SharedSessionInviteModal
          sharedSessionId="ss-1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.getByText("charlie")).toBeInTheDocument();
    expect(screen.getByText("charlotte")).toBeInTheDocument();
  });

  it("calls invite mutation when invite button is clicked", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <SharedSessionInviteModal
          sharedSessionId="ss-1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    const inviteButtons = screen.getAllByText("Invite");
    await userEvent.click(inviteButtons[0]);

    expect(mockMutate).toHaveBeenCalledWith(
      { sharedSessionId: "ss-1", username: "charlie" },
      expect.any(Object),
    );
  });

  it("calls onClose when done is clicked", async () => {
    const onClose = vi.fn();
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <SharedSessionInviteModal
          sharedSessionId="ss-1"
          open={true}
          onClose={onClose}
        />
      </Wrapper>,
    );

    await userEvent.click(screen.getByText("Done"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("has a search placeholder", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <SharedSessionInviteModal
          sharedSessionId="ss-1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(
      screen.getByPlaceholderText("Search users..."),
    ).toBeInTheDocument();
  });
});
