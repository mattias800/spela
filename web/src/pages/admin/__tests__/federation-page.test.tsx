import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

vi.mock("@/hooks/use-federation", () => ({
  useFederationPeers: vi.fn(),
  useFederationExchanges: vi.fn(),
  useTestFederationPeer: vi.fn(),
}));

import {
  useFederationPeers,
  useFederationExchanges,
  useTestFederationPeer,
} from "@/hooks/use-federation";
import { AdminFederationPage } from "../federation-page";

const mockUsePeers = useFederationPeers as ReturnType<typeof vi.fn>;
const mockUseExchanges = useFederationExchanges as ReturnType<typeof vi.fn>;
const mockUseTest = useTestFederationPeer as ReturnType<typeof vi.fn>;
const mockTestMutate = vi.fn();

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminFederationPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseTest.mockReturnValue({ mutate: mockTestMutate, isPending: false });
});

describe("AdminFederationPage", () => {
  it("renders peers with a status badge and an exchange row", () => {
    mockUsePeers.mockReturnValue({
      data: {
        peers: [
          {
            fingerprint: "abcdef1234567890xyz",
            name: "Alice's Server",
            status: "active",
            reachable: true,
            lastError: "",
            lastContactAt: "2026-06-15T00:00:00Z",
          },
        ],
      },
      isLoading: false,
    });
    mockUseExchanges.mockReturnValue({
      data: {
        exchanges: [
          {
            id: 1,
            createdAt: "2026-06-15T00:00:00Z",
            peerName: "Alice's Server",
            peerFingerprint: "abcdef1234567890xyz",
            direction: "outbound",
            operation: "stats_pull",
            status: "ok",
            itemCount: 3,
          },
        ],
      },
      isLoading: false,
    });

    renderPage();

    expect(screen.getByTestId("federation-peer-row")).toBeInTheDocument();
    expect(screen.getByTestId("peer-status-badge")).toHaveTextContent("Reachable");
    expect(screen.getByTestId("federation-exchange-row")).toBeInTheDocument();
    expect(screen.getByTestId("exchange-operation")).toHaveTextContent("Stats pull"); // humanized
  });

  it("shows empty states when there are no peers or activity", () => {
    mockUsePeers.mockReturnValue({ data: { peers: [] }, isLoading: false });
    mockUseExchanges.mockReturnValue({ data: { exchanges: [] }, isLoading: false });

    renderPage();

    expect(screen.getByText("No friend servers")).toBeInTheDocument();
    expect(screen.getByText("No federation activity yet")).toBeInTheDocument();
  });

  it("shows an error block with a working retry when the peers query fails", async () => {
    const refetchPeers = vi.fn();
    mockUsePeers.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("network down"),
      refetch: refetchPeers,
    });
    mockUseExchanges.mockReturnValue({ data: { exchanges: [] }, isLoading: false });

    renderPage();

    const errorBlock = screen.getByTestId("federation-error");
    expect(errorBlock).toHaveTextContent("Failed to load friend servers");
    expect(errorBlock).toHaveTextContent("network down");
    expect(screen.queryByTestId("federation-peer-row")).not.toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }));
    expect(refetchPeers).toHaveBeenCalled();
  });

  it("triggers the test-connection mutation for a peer", async () => {
    mockUsePeers.mockReturnValue({
      data: {
        peers: [
          {
            fingerprint: "fp-to-test-0000000",
            name: "Bob",
            status: "active",
            reachable: false,
            lastError: "",
            lastContactAt: null,
          },
        ],
      },
      isLoading: false,
    });
    mockUseExchanges.mockReturnValue({ data: { exchanges: [] }, isLoading: false });

    renderPage();
    await userEvent.setup().click(screen.getByTestId("test-connection-button"));

    expect(mockTestMutate).toHaveBeenCalledWith(
      "fp-to-test-0000000",
      expect.anything(),
    );
  });
});
