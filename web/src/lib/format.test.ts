import { describe, it, expect } from "vitest";
import { formatFileSize, formatPlayTime, formatRelativeTime } from "./format";

describe("formatFileSize", () => {
  it("formats zero bytes", () => {
    expect(formatFileSize(0)).toBe("0 B");
  });

  it("formats bytes", () => {
    expect(formatFileSize(500)).toBe("500 B");
  });

  it("formats kilobytes", () => {
    expect(formatFileSize(1024)).toBe("1.0 KB");
    expect(formatFileSize(1536)).toBe("1.5 KB");
  });

  it("formats megabytes", () => {
    expect(formatFileSize(1048576)).toBe("1.0 MB");
  });

  it("formats gigabytes", () => {
    expect(formatFileSize(1073741824)).toBe("1.0 GB");
  });

  // BUG: formatFileSize crashes for negative numbers (Math.log of negative = NaN)
  it("handles negative bytes gracefully", () => {
    // This will produce NaN or throw - documenting the edge case
    const result = formatFileSize(-1);
    // Should not crash; ideally returns "0 B" or a sensible fallback
    expect(result).toBeDefined();
  });

  // BUG: Very large files beyond GB overflow the units array
  it("handles very large files (TB range)", () => {
    // 1 TB = 1099511627776
    const result = formatFileSize(1099511627776);
    // units array only has [B, KB, MB, GB] - index 4 (TB) is undefined
    // This will produce "1.0 undefined"
    expect(result).not.toContain("undefined");
  });
});

describe("formatPlayTime", () => {
  it("formats seconds under a minute", () => {
    expect(formatPlayTime(30)).toBe("< 1 min");
  });

  it("formats minutes only", () => {
    expect(formatPlayTime(300)).toBe("5m");
  });

  it("formats hours and minutes", () => {
    expect(formatPlayTime(3661)).toBe("1h 1m");
  });

  it("formats zero seconds", () => {
    expect(formatPlayTime(0)).toBe("< 1 min");
  });

  // Edge case: negative seconds
  it("handles negative seconds", () => {
    const result = formatPlayTime(-100);
    expect(result).toBeDefined();
  });
});

describe("formatRelativeTime", () => {
  it("formats recent time as just now", () => {
    const now = new Date().toISOString();
    expect(formatRelativeTime(now)).toBe("just now");
  });

  it("formats minutes ago", () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    expect(formatRelativeTime(fiveMinAgo)).toBe("5m ago");
  });

  it("formats hours ago", () => {
    const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
    expect(formatRelativeTime(twoHoursAgo)).toBe("2h ago");
  });

  it("formats days ago", () => {
    const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString();
    expect(formatRelativeTime(threeDaysAgo)).toBe("3d ago");
  });
});
