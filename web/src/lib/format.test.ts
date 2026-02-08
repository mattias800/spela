import { describe, it, expect } from "vitest";
import { formatFileSize, formatPlayTime, formatDate } from "./format";

describe("formatFileSize", () => {
  it("formats zero bytes", () => {
    expect(formatFileSize(0)).toBe("0 B");
  });

  it("formats bytes", () => {
    expect(formatFileSize(512)).toBe("512 B");
  });

  it("formats kilobytes", () => {
    expect(formatFileSize(1536)).toBe("1.5 KB");
  });

  it("formats megabytes", () => {
    expect(formatFileSize(5242880)).toBe("5.0 MB");
  });

  it("formats gigabytes", () => {
    expect(formatFileSize(1073741824)).toBe("1.0 GB");
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
    expect(formatPlayTime(5400)).toBe("1h 30m");
  });
});

describe("formatDate", () => {
  it("formats a date string", () => {
    const result = formatDate("2024-06-15T00:00:00Z");
    expect(result).toContain("2024");
    expect(result).toContain("15");
  });
});

// Bug-exposing tests added by QA
describe("formatFileSize edge cases", () => {
  // BUG: formatFileSize crashes for very large files because the units array
  // only has [B, KB, MB, GB] - index 4 (TB) is undefined.
  it("handles very large files (TB range) without producing undefined", () => {
    // 1 TB = 1099511627776
    const result = formatFileSize(1099511627776);
    expect(result).not.toContain("undefined");
  });

  it("handles negative bytes gracefully", () => {
    const result = formatFileSize(-1);
    expect(result).toBeDefined();
  });
});
