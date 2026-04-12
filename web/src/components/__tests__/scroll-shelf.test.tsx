import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Star } from "lucide-react";
import { ScrollShelf } from "../scroll-shelf";

describe("ScrollShelf", () => {
  it("renders title and children", () => {
    render(
      <ScrollShelf title="Test Shelf" testId="test-shelf" isLoading={false} isEmpty={false}>
        <div>Child 1</div>
        <div>Child 2</div>
      </ScrollShelf>,
    );
    expect(screen.getByRole("heading", { name: "Test Shelf", level: 2 })).toBeInTheDocument();
    expect(screen.getByText("Child 1")).toBeInTheDocument();
    expect(screen.getByText("Child 2")).toBeInTheDocument();
  });

  it("renders subtitle when provided", () => {
    render(
      <ScrollShelf title="Shelf" subtitle="Some subtitle" testId="s" isLoading={false} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByText("Some subtitle")).toBeInTheDocument();
  });

  it("renders icon when provided", () => {
    render(
      <ScrollShelf title="Shelf" icon={Star} testId="s" isLoading={false} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    const section = screen.getByTestId("s");
    expect(section.querySelector("svg")).toBeInTheDocument();
  });

  it("renders headerRight slot", () => {
    render(
      <ScrollShelf title="Shelf" testId="s" isLoading={false} isEmpty={false} headerRight={<span>View all</span>}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByText("View all")).toBeInTheDocument();
  });

  it("renders scrollable list with title as aria label", () => {
    render(
      <ScrollShelf title="My Shelf" testId="s" isLoading={false} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByRole("list", { name: "My Shelf" })).toBeInTheDocument();
  });

  it("returns null when isEmpty is true", () => {
    const { container } = render(
      <ScrollShelf title="Shelf" testId="s" isLoading={false} isEmpty={true}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when isLoading", () => {
    render(
      <ScrollShelf title="Shelf" testId="s" isLoading={true} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByTestId("s-skeleton")).toBeInTheDocument();
  });

  it("renders custom loading skeleton when provided", () => {
    render(
      <ScrollShelf
        title="Shelf"
        testId="s"
        isLoading={true}
        isEmpty={false}
        loadingSkeleton={<div data-testid="custom-skeleton">Loading...</div>}
      >
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByTestId("custom-skeleton")).toBeInTheDocument();
  });
});
