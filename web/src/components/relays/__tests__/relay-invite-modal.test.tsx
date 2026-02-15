import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { RelayInviteModal } from "../relay-invite-modal";

const mockMutate = vi.fn();
const mockSearchResults: { id: string; username: string; avatarUrl?: string }[] = [];

vi.mock("@/hooks/use-relays", () => ({
  useInviteToRelay: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}));

vi.mock("@/hooks/use-social", () => ({
  useSearchUsers: vi.fn(() => ({
    data: mockSearchResults,
    isLoading: false,
  })),
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: vi.fn(() => ({ toast: vi.fn() })),
  };
});

beforeEach(() => {
  vi.clearAllMocks();
  mockSearchResults.length = 0;
});

describe("RelayInviteModal", () => {
  it("renders when open", () => {
    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.getByText("Send Invite")).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    render(
      <RelayInviteModal
        relayId="relay-1"
        open={false}
        onClose={vi.fn()}
      />,
    );

    expect(screen.queryByLabelText("Username")).not.toBeInTheDocument();
  });

  it("disables submit when search input is empty", () => {
    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText("Send Invite").closest("button")).toBeDisabled();
  });

  it("calls invite mutation with typed username", async () => {
    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={vi.fn()}
      />,
    );

    const input = screen.getByLabelText("Username");
    await userEvent.type(input, "charlie");
    await userEvent.click(screen.getByText("Send Invite"));

    expect(mockMutate).toHaveBeenCalledWith(
      { relayId: "relay-1", username: "charlie" },
      expect.any(Object),
    );
  });

  it("calls onClose when done is clicked", async () => {
    const onClose = vi.fn();
    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={onClose}
      />,
    );

    await userEvent.click(screen.getByText("Done"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("shows search results dropdown when typing", async () => {
    mockSearchResults.push(
      { id: "1", username: "charlie" },
      { id: "2", username: "charlotte" },
    );

    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={vi.fn()}
      />,
    );

    const input = screen.getByLabelText("Username");
    await userEvent.type(input, "ch");

    expect(screen.getByText("charlie")).toBeInTheDocument();
    expect(screen.getByText("charlotte")).toBeInTheDocument();
  });

  it("selects user from dropdown and invites them", async () => {
    mockSearchResults.push(
      { id: "1", username: "charlie" },
    );

    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={vi.fn()}
      />,
    );

    const input = screen.getByLabelText("Username");
    await userEvent.type(input, "ch");
    await userEvent.click(screen.getByText("charlie"));
    await userEvent.click(screen.getByText("Send Invite"));

    expect(mockMutate).toHaveBeenCalledWith(
      { relayId: "relay-1", username: "charlie" },
      expect.any(Object),
    );
  });

  it("has a search placeholder", () => {
    render(
      <RelayInviteModal
        relayId="relay-1"
        open={true}
        onClose={vi.fn()}
      />,
    );

    expect(
      screen.getByPlaceholderText("Search for a user..."),
    ).toBeInTheDocument();
  });
});
