import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NetplayInviteModal } from "../netplay-invite-modal";

const mockMutate = vi.fn();

vi.mock("@/hooks/use-netplay", () => ({
  useSendNetplayInvite: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}));

vi.mock("@/hooks/use-social", () => ({
  useSearchUsers: vi.fn(() => ({
    data: {
      data: [
        { id: "u1", username: "alice", avatarUrl: null },
        { id: "u2", username: "bob", avatarUrl: "https://example.com/bob.png" },
      ],
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

describe("NetplayInviteModal", () => {
  it("renders modal title when open", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteModal
          sessionId="s1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.getByText("Invite Player")).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteModal
          sessionId="s1"
          open={false}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.queryByText("Invite Player")).not.toBeInTheDocument();
  });

  it("shows user list with all users", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteModal
          sessionId="s1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
  });

  it("calls mutate when invite button is clicked on a user", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteModal
          sessionId="s1"
          open={true}
          onClose={vi.fn()}
        />
      </Wrapper>,
    );

    const inviteButtons = screen.getAllByText("Invite");
    await userEvent.click(inviteButtons[0]);

    expect(mockMutate).toHaveBeenCalledWith(
      { sessionId: "s1", username: "alice" },
      expect.any(Object),
    );
  });

  it("calls onClose when Close is clicked", async () => {
    const onClose = vi.fn();
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteModal
          sessionId="s1"
          open={true}
          onClose={onClose}
        />
      </Wrapper>,
    );

    await userEvent.click(screen.getByText("Close"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("has a search input", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayInviteModal
          sessionId="s1"
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
