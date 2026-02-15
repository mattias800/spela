import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RelayCreateModal } from "../relay-create-modal";

const mockMutate = vi.fn();

vi.mock("@/hooks/use-relays", () => ({
  useCreateRelay: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}));

vi.mock("@/hooks/use-games", () => ({
  useGames: vi.fn(() => ({
    data: {
      data: [
        {
          id: "g1",
          title: "Super Mario World",
          consoleName: "SNES",
          coverUrl: "https://example.com/cover.png",
        },
        {
          id: "g2",
          title: "Zelda",
          consoleName: "SNES",
        },
      ],
    },
  })),
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

describe("RelayCreateModal", () => {
  it("renders form fields when open", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <RelayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
    // "Create Relay" appears as both modal title and submit button
    expect(screen.getAllByText("Create Relay")).toHaveLength(2);
  });

  it("does not render when closed", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <RelayCreateModal
          open={false}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
  });

  it("disables submit when name is empty", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <RelayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    const submitButton = screen
      .getAllByText("Create Relay")
      .find((el) => el.closest("button[type='submit']"));
    expect(submitButton?.closest("button")).toBeDisabled();
  });

  it("shows game search results when typing", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <RelayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    const searchInput = screen.getByPlaceholderText("Search for a game...");
    await userEvent.type(searchInput, "Mario");

    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Zelda")).toBeInTheDocument();
  });

  it("selects a game when clicked", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <RelayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    const searchInput = screen.getByPlaceholderText("Search for a game...");
    await userEvent.type(searchInput, "Mario");
    await userEvent.click(screen.getByText("Super Mario World"));

    // After selection, should show the selected game with a Change button
    expect(screen.getByText("Change")).toBeInTheDocument();
  });

  it("calls onClose when cancel is clicked", async () => {
    const onClose = vi.fn();
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <RelayCreateModal
          open={true}
          onClose={onClose}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    await userEvent.click(screen.getByText("Cancel"));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
