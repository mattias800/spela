import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StorageBar } from "../storage-bar";

describe("StorageBar", () => {
  it("displays used and quota formatted sizes", () => {
    render(<StorageBar usedBytes={52428800} quotaBytes={104857600} />);
    expect(screen.getByText(/50\.0 MB/)).toBeInTheDocument();
    expect(screen.getByText(/100\.0 MB/)).toBeInTheDocument();
  });

  it("shows correct percentage", () => {
    render(<StorageBar usedBytes={52428800} quotaBytes={104857600} />);
    expect(screen.getByText("50.0%")).toBeInTheDocument();
  });

  it("renders progressbar with correct aria attributes", () => {
    render(<StorageBar usedBytes={52428800} quotaBytes={104857600} />);
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "50");
    expect(bar).toHaveAttribute("aria-valuemin", "0");
    expect(bar).toHaveAttribute("aria-valuemax", "100");
  });

  it("clamps bar width to 100% when over quota", () => {
    render(<StorageBar usedBytes={209715200} quotaBytes={104857600} />);
    const bar = screen.getByRole("progressbar");
    expect(bar.style.width).toBe("100%");
    // But shows actual percentage in text
    expect(screen.getByText("200.0%")).toBeInTheDocument();
  });

  it("handles zero quota without error", () => {
    render(<StorageBar usedBytes={0} quotaBytes={0} />);
    expect(screen.getByText("0.0%")).toBeInTheDocument();
  });

  it("uses green color for low usage", () => {
    render(<StorageBar usedBytes={10485760} quotaBytes={104857600} />);
    const bar = screen.getByRole("progressbar");
    expect(bar.className).toContain("bg-brand-500");
  });

  it("uses amber color for 80-99% usage", () => {
    render(<StorageBar usedBytes={94371840} quotaBytes={104857600} />);
    const bar = screen.getByRole("progressbar");
    expect(bar.className).toContain("bg-warning-500");
  });

  it("uses red color for 100%+ usage", () => {
    render(<StorageBar usedBytes={104857600} quotaBytes={104857600} />);
    const bar = screen.getByRole("progressbar");
    expect(bar.className).toContain("bg-danger-500");
  });
});
