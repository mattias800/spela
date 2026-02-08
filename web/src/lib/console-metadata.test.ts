import { describe, it, expect } from "vitest";
import { getConsoleStyle } from "./console-metadata";

describe("getConsoleStyle", () => {
  it("returns style for known console", () => {
    const style = getConsoleStyle("nes");
    expect(style.color).toBe("#e53e3e");
    expect(style.gradient).toContain("red");
  });

  it("is case insensitive", () => {
    const style = getConsoleStyle("NES");
    expect(style.color).toBe("#e53e3e");
  });

  it("returns default for unknown console", () => {
    const style = getConsoleStyle("UNKNOWN");
    expect(style.color).toBe("#adb5bd");
  });

  // BUG: The lookup keys in consoleStyles use lowercase display names
  // (e.g., "genesis", "saturn") but the backend sends abbreviations
  // (e.g., "GEN", "SAT"). After toLowerCase(), "GEN" becomes "gen",
  // but the key in the map is "genesis" - no match.
  it("matches backend abbreviation GEN for Genesis", () => {
    // Backend sends abbreviation "GEN" which lowercases to "gen"
    // But the key in consoleStyles is "genesis", not "gen"
    const style = getConsoleStyle("GEN");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });

  it("matches backend abbreviation SAT for Saturn", () => {
    // Backend sends "SAT" -> "sat", but key is "saturn"
    const style = getConsoleStyle("SAT");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });

  it("matches backend abbreviation SMS for Master System", () => {
    // Backend sends "SMS" -> "sms", key is "sms" - this one matches
    const style = getConsoleStyle("SMS");
    expect(style.color).toBe("#4299e1");
  });

  it("matches backend abbreviation PCE for TurboGrafx", () => {
    // Backend sends "PCE" -> "pce", but key is "tg16" - no match
    const style = getConsoleStyle("PCE");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });

  it("matches backend abbreviation A26 for Atari", () => {
    // Backend sends "A26" -> "a26", but key is "atari2600" - no match
    const style = getConsoleStyle("A26");
    expect(style.color).not.toBe("#adb5bd"); // Should not fall back to default
  });
});
