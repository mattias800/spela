import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { MoodPicker } from "@/features/explore/components/mood-picker";
import type { MoodDefinition } from "@/types/api";

const mockMoods: MoodDefinition[] = [
  {
    id: "chill",
    name: "Chill",
    description: "Relaxing and laid-back games",
    icon: "😌",
    gradient: ["#1a237e", "#4a148c"],
  },
  {
    id: "intense",
    name: "Intense",
    description: "Heart-pounding action",
    icon: "🔥",
    gradient: ["#b71c1c", "#ff6f00"],
  },
  {
    id: "nostalgic",
    name: "Nostalgic",
    description: "Classic retro vibes",
    icon: "🕹️",
    gradient: ["#4a148c", "#880e4f"],
  },
];

function renderComponent(
  moods: MoodDefinition[] | undefined = mockMoods,
  isLoading = false,
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MoodPicker moods={moods} isLoading={isLoading} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("MoodPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders mood cards with correct names", () => {
    renderComponent();
    expect(screen.getByText("Chill")).toBeInTheDocument();
    expect(screen.getByText("Intense")).toBeInTheDocument();
    expect(screen.getByText("Nostalgic")).toBeInTheDocument();
  });

  it("renders correct test IDs", () => {
    renderComponent();
    expect(screen.getByTestId("mood-picker")).toBeInTheDocument();
    expect(screen.getByTestId("mood-card-chill")).toBeInTheDocument();
    expect(screen.getByTestId("mood-card-intense")).toBeInTheDocument();
    expect(screen.getByTestId("mood-card-nostalgic")).toBeInTheDocument();
  });

  it("shows skeleton while loading", () => {
    renderComponent(undefined, true);
    expect(screen.getByTestId("mood-picker-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("mood-picker")).not.toBeInTheDocument();
  });

  it("returns null when moods is empty", () => {
    const { container } = renderComponent([]);
    expect(container.firstChild).toBeNull();
  });

  it("returns null when moods is undefined and not loading", () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <MoodPicker moods={undefined} isLoading={false} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("cards link to correct mood routes", () => {
    renderComponent();
    const chillLink = screen.getByTestId("mood-card-chill");
    expect(chillLink).toHaveAttribute("href", "/explore/mood/chill");

    const intenseLink = screen.getByTestId("mood-card-intense");
    expect(intenseLink).toHaveAttribute("href", "/explore/mood/intense");
  });

  it("renders mood descriptions", () => {
    renderComponent();
    expect(screen.getByText("Relaxing and laid-back games")).toBeInTheDocument();
    expect(screen.getByText("Heart-pounding action")).toBeInTheDocument();
  });

  it("renders mood icons", () => {
    renderComponent();
    expect(screen.getByText("😌")).toBeInTheDocument();
    expect(screen.getByText("🔥")).toBeInTheDocument();
    expect(screen.getByText("🕹️")).toBeInTheDocument();
  });

  it("renders the section title", () => {
    renderComponent();
    expect(
      screen.getByText("What are you in the mood for?"),
    ).toBeInTheDocument();
  });
});
