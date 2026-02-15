import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NetplayCreateModal } from "../netplay-create-modal";

const mockMutate = vi.fn();

vi.mock("@/hooks/use-netplay", () => ({
  useCreateNetplaySession: vi.fn(() => ({
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
          title: "Sonic the Hedgehog",
          consoleName: "Genesis",
          coverUrl: null,
        },
        {
          id: "g3",
          title: "Crash Bandicoot",
          consoleName: "PlayStation",
          coverUrl: null,
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

describe("NetplayCreateModal", () => {
  it("renders modal title when open", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    expect(screen.getByText("Create Netplay Session")).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
          open={false}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    expect(
      screen.queryByText("Create Netplay Session"),
    ).not.toBeInTheDocument();
  });

  it("disables submit when no game is selected", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    const submitButton = screen
      .getAllByText("Create Session")
      .find((el) => el.closest("button[type='submit']"));
    expect(submitButton?.closest("button")).toBeDisabled();
  });

  it("shows game search results when typing", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    const searchInput = screen.getByPlaceholderText("Search for a game...");
    await userEvent.type(searchInput, "Mario");

    // Should show supported games only (SNES and Genesis are supported)
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Sonic the Hedgehog")).toBeInTheDocument();
    // PlayStation is not supported for netplay
    expect(screen.queryByText("Crash Bandicoot")).not.toBeInTheDocument();
  });

  it("selects a game when clicked", async () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    const searchInput = screen.getByPlaceholderText("Search for a game...");
    await userEvent.type(searchInput, "Mario");
    await userEvent.click(screen.getByText("Super Mario World"));

    expect(screen.getByText("Change")).toBeInTheDocument();
  });

  it("shows supported console info text", () => {
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
          open={true}
          onClose={vi.fn()}
          onCreated={vi.fn()}
        />
      </Wrapper>,
    );

    expect(
      screen.getByText(
        "Netplay supports NES, SNES, Game Boy, Game Boy Color, Game Boy Advance, Genesis, and Mega Drive.",
      ),
    ).toBeInTheDocument();
  });

  it("calls onClose when cancel is clicked", async () => {
    const onClose = vi.fn();
    const Wrapper = createWrapper();
    render(
      <Wrapper>
        <NetplayCreateModal
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
