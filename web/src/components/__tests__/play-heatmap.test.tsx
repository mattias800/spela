import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { PlayHeatmap } from "../play-heatmap";
import type { DailyPlayActivity } from "@/types/api";

function makeData(days: number): DailyPlayActivity[] {
  const result: DailyPlayActivity[] = [];
  const today = new Date();
  for (let i = 0; i < days; i++) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    result.push({
      date: `${y}-${m}-${day}`,
      playTime: (i + 1) * 600,
    });
  }
  return result;
}

describe("PlayHeatmap", () => {
  it("renders empty state when data is empty", () => {
    render(<PlayHeatmap data={[]} />);
    expect(screen.getByText("No play activity yet")).toBeInTheDocument();
  });

  it("renders SVG with cells when data is provided", () => {
    const data = makeData(7);
    render(<PlayHeatmap data={data} />);
    const svg = screen.getByRole("img", { name: /play activity heatmap/i });
    expect(svg).toBeInTheDocument();
    const cells = screen.getAllByTestId("heatmap-cell");
    expect(cells.length).toBeGreaterThan(0);
  });

  it("assigns correct color levels based on play time", () => {
    const today = new Date();
    const y = today.getFullYear();
    const m = String(today.getMonth() + 1).padStart(2, "0");
    const day = String(today.getDate()).padStart(2, "0");
    const todayKey = `${y}-${m}-${day}`;

    const data: DailyPlayActivity[] = [{ date: todayKey, playTime: 3600 }];
    render(<PlayHeatmap data={data} />);

    const cells = screen.getAllByTestId("heatmap-cell");
    const cell = cells.find(
      (el) => el.getAttribute("data-date") === todayKey,
    );
    expect(cell).toBeDefined();
    // With only one entry, it should be the max level (4)
    expect(cell?.getAttribute("data-level")).toBe("4");
  });

  it("renders month and day labels", () => {
    const data = makeData(90);
    render(<PlayHeatmap data={data} />);
    // Should have day labels
    expect(screen.getByText("Mon")).toBeInTheDocument();
    expect(screen.getByText("Wed")).toBeInTheDocument();
    expect(screen.getByText("Fri")).toBeInTheDocument();
  });

  it("renders legend with Less and More labels", () => {
    const data = makeData(7);
    render(<PlayHeatmap data={data} />);
    expect(screen.getByText("Less")).toBeInTheDocument();
    expect(screen.getByText("More")).toBeInTheDocument();
  });

  it("shows tooltip on cell hover", () => {
    const data = makeData(7);
    render(<PlayHeatmap data={data} />);
    const cells = screen.getAllByTestId("heatmap-cell");
    fireEvent.mouseEnter(cells[0]);
    // Tooltip should appear somewhere in the document
    // The tooltip contains formatted play time or "No activity"
    const tooltip = document.querySelector(".fixed.z-50");
    expect(tooltip).toBeInTheDocument();
  });

  it("hides tooltip on mouse leave", () => {
    const data = makeData(7);
    render(<PlayHeatmap data={data} />);
    const cells = screen.getAllByTestId("heatmap-cell");
    fireEvent.mouseEnter(cells[0]);
    fireEvent.mouseLeave(cells[0]);
    const tooltip = document.querySelector(".fixed.z-50");
    expect(tooltip).not.toBeInTheDocument();
  });

  it("applies custom className", () => {
    const data = makeData(1);
    const { container } = render(
      <PlayHeatmap data={data} className="my-custom-class" />,
    );
    expect(container.firstChild).toHaveClass("my-custom-class");
  });
});
