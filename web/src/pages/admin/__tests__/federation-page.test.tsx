import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ToastProvider } from "@/components/ui";

vi.mock("@/hooks/use-federation", () => ({
  useFederationPeers: vi.fn(),
  useFederationExchanges: vi.fn(),
  useTestFederationPeer: vi.fn(),
  useIssueFederationInvite: vi.fn(),
  useAcceptFederationInvite: vi.fn(),
  useRevokeFederationPeer: vi.fn(),
}));

import {
  useFederationPeers,
  useFederationExchanges,
  useTestFederationPeer,
  useIssueFederationInvite,
  useAcceptFederationInvite,
  useRevokeFederationPeer,
} from "@/hooks/use-federation";
import { AdminFederationPage } from "../federation-page";

const mockUsePeers = useFederationPeers as ReturnType<typeof vi.fn>;
const mockUseExchanges = useFederationExchanges as ReturnType<typeof vi.fn>;
const mockUseTest = useTestFederationPeer as ReturnType<typeof vi.fn>;
const mockUseIssue = useIssueFederationInvite as ReturnType<typeof vi.fn>;
const mockUseAccept = useAcceptFederationInvite as ReturnType<typeof vi.fn>;
const mockUseRevoke = useRevokeFederationPeer as ReturnType<typeof vi.fn>;
const mockTestMutate = vi.fn();
const mockIssueMutate = vi.fn();
const mockAcceptMutate = vi.fn();
const mockRevokeMutate = vi.fn();

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <AdminFederationPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUsePeers.mockReturnValue({ data: { peers: [] }, isLoading: false });
  mockUseExchanges.mockReturnValue({ data: { exchanges: [] }, isLoading: false });
  mockUseTest.mockReturnValue({ mutate: mockTestMutate, isPending: false });
  mockUseIssue.mockReturnValue({ mutate: mockIssueMutate, isPending: false });
  mockUseAccept.mockReturnValue({ mutate: mockAcceptMutate, isPending: false });
  mockUseRevoke.mockReturnValue({ mutate: mockRevokeMutate, isPending: false });
});

const onePeer = {
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
};

describe("AdminFederationPage", () => {
  it("renders peers with a status badge and an exchange row", () => {
    mockUsePeers.mockReturnValue(onePeer);
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

    renderPage();
    await userEvent.setup().click(screen.getByTestId("test-connection-button"));

    expect(mockTestMutate).toHaveBeenCalledWith(
      "fp-to-test-0000000",
      expect.anything(),
    );
  });

  it("opens the pair dialog on the accept-invite panel and generates an invite", async () => {
    mockIssueMutate.mockImplementation((_vars, opts) =>
      opts.onSuccess({ invite: "INVITE-XYZ" }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("pair-friend-button"));
    expect(screen.getByTestId("accept-invite-panel")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Invite a friend" }));
    await user.click(screen.getByTestId("generate-invite-button"));

    expect(mockIssueMutate).toHaveBeenCalled();
    expect(screen.getByTestId("generated-invite")).toHaveValue("INVITE-XYZ");
  });

  it("submits an accepted invite with the pasted string and name", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("pair-friend-button"));
    await user.type(screen.getByTestId("accept-invite-input"), "friend-invite");
    await user.type(screen.getByTestId("accept-invite-name"), "Carol");
    await user.click(screen.getByTestId("accept-invite-submit"));

    expect(mockAcceptMutate).toHaveBeenCalledWith(
      { invite: "friend-invite", name: "Carol" },
      expect.anything(),
    );
  });

  it("revokes a peer only after confirmation", async () => {
    mockUsePeers.mockReturnValue(onePeer);
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("revoke-peer-button"));
    // Confirmation modal — confirm button lives inside the dialog.
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Revoke" }));

    expect(mockRevokeMutate).toHaveBeenCalledWith(
      "abcdef1234567890xyz",
      expect.anything(),
    );
  });
});
