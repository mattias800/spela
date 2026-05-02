/**
 * Mirror of the Kotlin `PlaySemantics` enum + resolver in
 * `player/shared/src/commonMain/kotlin/com/spela/player/domain/model/PlaySemantics.kt`.
 *
 * Three states drive the game-detail hero's primary action label so
 * ScummVM / demo-core / save-state-disabled launches don't promise
 * "Resume" when the engine is going to land on the title screen.
 *
 * Resolver inputs match the Kotlin side exactly; keep the two in sync
 * whenever either side changes — there's no codegen across, just a
 * thin parallel implementation. See #884 / #900.
 */
export type PlaySemantics =
  | "no-session"
  | "resumes-from-save-state"
  | "launches-fresh";

export type SaveStateChoice = "enabled" | "disabled" | "ask-once";

/**
 * Resolves the effective save-state policy for a console + game.
 * Mirrors the Kotlin `effectiveSaveStateChoice` resolver.
 *
 * Precedence: per-game override → per-console override → tier default.
 * For the hero label resolver we don't need the tier branch — any
 * SaveStateChoice that isn't `"disabled"` counts as auto-load.
 */
export function effectiveSaveStateChoice(
  consoleAbbr: string,
  consoleSaveStatePolicies: Readonly<Record<string, SaveStateChoice>>,
  gameOverride: SaveStateChoice | null | undefined,
): SaveStateChoice {
  if (gameOverride) return gameOverride;
  const key = consoleAbbr.toLowerCase();
  return consoleSaveStatePolicies[key] ?? "enabled";
}

export interface ResolvePlaySemanticsInput {
  hasSession: boolean;
  consoleSaveStateSupport: boolean;
  effectiveChoice: SaveStateChoice;
}

export function resolvePlaySemantics({
  hasSession,
  consoleSaveStateSupport,
  effectiveChoice,
}: ResolvePlaySemanticsInput): PlaySemantics {
  if (!hasSession) return "no-session";
  const willAutoLoad =
    consoleSaveStateSupport && effectiveChoice !== "disabled";
  return willAutoLoad ? "resumes-from-save-state" : "launches-fresh";
}

/**
 * The user-facing label for each state. Matches the player app
 * exactly (#900 — "New game" / "Resume" / "Continue").
 */
export function playSemanticsLabel(s: PlaySemantics): string {
  switch (s) {
    case "no-session":
      return "New game";
    case "resumes-from-save-state":
      return "Resume";
    case "launches-fresh":
      return "Continue";
  }
}
