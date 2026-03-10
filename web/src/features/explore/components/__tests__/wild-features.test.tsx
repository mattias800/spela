import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { WildFeaturesSection } from "@/features/explore/components/wild-features";

const mockRefetch = vi.fn();
vi.mock("@/hooks/use-explore", () => ({
  useSurpriseGame: vi.fn(() => ({
    data: undefined,
    refetch: mockRefetch,
    isFetching: false,
  })),
}));

function renderComponent() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <WildFeaturesSection />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("WildFeaturesSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the section heading", () => {
    renderComponent();
    expect(screen.getByText("Feeling Adventurous?")).toBeInTheDocument();
  });

  it("renders the I'm Feeling Lucky button", () => {
    renderComponent();
    expect(screen.getByTestId("lucky-button")).toBeInTheDocument();
    expect(screen.getByText("I'm Feeling Lucky")).toBeInTheDocument();
  });

  it("renders the Decision Wizard link", () => {
    renderComponent();
    expect(screen.getByTestId("wizard-cta")).toBeInTheDocument();
    expect(screen.getByText("Decision Wizard")).toBeInTheDocument();
  });

  it("wizard CTA links to /explore/wizard", () => {
    renderComponent();
    const link = screen.getByTestId("wizard-cta");
    expect(link).toHaveAttribute("href", "/explore/wizard");
  });

  it("calls refetch when lucky button is clicked", () => {
    renderComponent();
    fireEvent.click(screen.getByTestId("lucky-button"));
    expect(mockRefetch).toHaveBeenCalled();
  });

  it("renders the wild-features test id", () => {
    renderComponent();
    expect(screen.getByTestId("wild-features")).toBeInTheDocument();
  });
});
