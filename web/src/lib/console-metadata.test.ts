import { describe, it, expect } from "vitest";
import { getConsoleStyle } from "./console-metadata";

describe("getConsoleStyle", () => {
  it("returns style for known console", () => {
    // Matches the server's seeded colorTheme for NES.
    const style = getConsoleStyle("nes");
    expect(style.color).toBe("#e60012");
  });

  it("is case insensitive", () => {
    const style = getConsoleStyle("NES");
    expect(style.color).toBe("#e60012");
  });

  it("returns default for unknown console", () => {
    const style = getConsoleStyle("UNKNOWN");
    expect(style.color).toBe("#adb5bd");
  });

  // The backend often sends short abbreviations that need to map to
  // our internal keys. The alias table in console-metadata.ts handles
  // these.
  it("matches backend abbreviation GEN for Genesis", () => {
    const style = getConsoleStyle("GEN");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });

  it("matches backend abbreviation SAT for Saturn", () => {
    const style = getConsoleStyle("SAT");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });

  it("matches backend abbreviation SMS for Master System", () => {
    const style = getConsoleStyle("SMS");
    expect(style.color).toBe("#4299e1");
  });

  it("matches backend abbreviation PCE for TurboGrafx", () => {
    const style = getConsoleStyle("PCE");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });

  it("matches backend abbreviation A26 for Atari", () => {
    const style = getConsoleStyle("A26");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });
});
