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
  useSearchUsers: vi.fn((query: string) => ({
    data:
      query.length >= 2
        ? [
            { id: "u1", username: "alice", avatarUrl: null },
            { id: "u2", username: "bob", avatarUrl: "https://example.com/bob.png" },
          ]
        : [],
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

  it("disables submit when no username is entered", () => {
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

    const sendButton = screen.getByText("Send Invite").closest("button");
    expect(sendButton).toBeDisabled();
  });

  it("shows search results when typing", async () => {
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

    const searchInput = screen.getByPlaceholderText("Search for a user...");
    await userEvent.type(searchInput, "al");

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
  });

  it("selects a user and fills the input when clicked", async () => {
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

    const searchInput = screen.getByPlaceholderText("Search for a user...");
    await userEvent.type(searchInput, "al");
    await userEvent.click(screen.getByText("alice"));

    expect(searchInput).toHaveValue("alice");
  });

  it("calls mutate with sessionId and username on submit", async () => {
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

    const searchInput = screen.getByPlaceholderText("Search for a user...");
    await userEvent.type(searchInput, "al");
    await userEvent.click(screen.getByText("alice"));

    const sendButton = screen.getByText("Send Invite").closest("button")!;
    await userEvent.click(sendButton);

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

  it("shows helper text about searching users", () => {
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
      screen.getByText(
        "Search for users by name and invite them to this netplay session.",
      ),
    ).toBeInTheDocument();
  });
});
