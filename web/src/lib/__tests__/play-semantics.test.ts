import { describe, expect, it } from "vitest";
import {
  effectiveSaveStateChoice,
  playSemanticsLabel,
  resolvePlaySemantics,
  type SaveStateChoice,
} from "@/lib/play-semantics";

// #900 — TS port of the Kotlin PlaySemanticsTest. Same six branches
// the Kotlin side covers; ensures the two stay in sync as inputs
// change (the resolver is intentionally not codegen'd across).

describe("resolvePlaySemantics", () => {
  it("no session → 'no-session'", () => {
    expect(
      resolvePlaySemantics({
        hasSession: false,
        consoleSaveStateSupport: true,
        effectiveChoice: "enabled",
      }),
    ).toBe("no-session");
  });

  it("no session overrides everything else", () => {
    // Even when save states are unavailable, an absent session means
    // "new game" — the label must never imply continuation when
    // there's nothing to continue from.
    expect(
      resolvePlaySemantics({
        hasSession: false,
        consoleSaveStateSupport: false,
        effectiveChoice: "disabled",
      }),
    ).toBe("no-session");
  });

  it("session + console supports + enabled → 'resumes-from-save-state'", () => {
    expect(
      resolvePlaySemantics({
        hasSession: true,
        consoleSaveStateSupport: true,
        effectiveChoice: "enabled",
      }),
    ).toBe("resumes-from-save-state");
  });

  it("ask-once counts as auto-load", () => {
    // The in-game flow treats AskOnce as Enabled until the prompt
    // resolves; the hero label matches honestly.
    expect(
      resolvePlaySemantics({
        hasSession: true,
        consoleSaveStateSupport: true,
        effectiveChoice: "ask-once",
      }),
    ).toBe("resumes-from-save-state");
  });

  it("session + console doesn't support → 'launches-fresh'", () => {
    // ScummVM, demo cores. Engine starts at the title screen.
    expect(
      resolvePlaySemantics({
        hasSession: true,
        consoleSaveStateSupport: false,
        effectiveChoice: "enabled",
      }),
    ).toBe("launches-fresh");
  });

  it("session + user disabled → 'launches-fresh'", () => {
    // Per-console / per-game opt-out (#804). "Resume" would over-
    // promise — launch goes to the title screen.
    expect(
      resolvePlaySemantics({
        hasSession: true,
        consoleSaveStateSupport: true,
        effectiveChoice: "disabled",
      }),
    ).toBe("launches-fresh");
  });
});

describe("effectiveSaveStateChoice", () => {
  it("game override wins over console policy", () => {
    expect(
      effectiveSaveStateChoice("snes", { snes: "enabled" }, "disabled"),
    ).toBe("disabled");
  });

  it("console policy wins over default when no game override", () => {
    expect(
      effectiveSaveStateChoice("snes", { snes: "disabled" }, null),
    ).toBe("disabled");
  });

  it("console policy is case-insensitive on the abbreviation", () => {
    expect(
      effectiveSaveStateChoice(
        "SNES",
        { snes: "disabled" } as Record<string, SaveStateChoice>,
        null,
      ),
    ).toBe("disabled");
  });

  it("falls back to enabled when no override applies", () => {
    expect(effectiveSaveStateChoice("snes", {}, null)).toBe("enabled");
  });
});

describe("playSemanticsLabel", () => {
  // Locks the player-app-matching strings (#900).
  it("exhaustive label mapping", () => {
    expect(playSemanticsLabel("no-session")).toBe("New game");
    expect(playSemanticsLabel("resumes-from-save-state")).toBe("Resume");
    expect(playSemanticsLabel("launches-fresh")).toBe("Continue");
  });
});
