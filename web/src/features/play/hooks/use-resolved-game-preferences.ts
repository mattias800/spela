import type { UserPreferences } from "@/types/api";
import type { EmulatorPreferences } from "@/lib/emulator-protocol";
import { toEmulatorJsShader } from "@/lib/shader-mapping";

interface ResolvedPreferencesInput {
  preferences: UserPreferences | undefined;
  consoleId: string | undefined;
}

/**
 * Resolves the effective emulator preferences for a game given the
 * user's global + per-console overrides. Extracted from `play-page.tsx`
 * in #694 — pure derived state, no side effects, so a plain function
 * would do, but it is exposed as a `use-*` hook to keep the call site
 * consistent with the surrounding hook pattern.
 *
 * Resolution order (matches the pre-refactor inline code):
 *
 *   shader:  per-console override  → global `selectedShader`      → "none"
 *   mapping: per-console override  → global `selectedKeyMapping`  → "arrows-left"
 *   custom:  only when the chosen mapping is `"custom"`; per-console
 *            custom takes precedence over global custom, falling back
 *            to `{}` if neither is set.
 *
 * The EmulatorJS shader name is mapped through `toEmulatorJsShader`
 * (EmulatorJS uses its own shader ids; we pass the Spela id through
 * unchanged for shaders without a mapping).
 */
export function useResolvedGamePreferences({
  preferences,
  consoleId,
}: ResolvedPreferencesInput): EmulatorPreferences {
  const key = consoleId ?? "";

  const spelaShader = preferences
    ? preferences.consoleShaders[key] ||
      preferences.selectedShader ||
      "none"
    : "none";
  const resolvedShader = toEmulatorJsShader(spelaShader) || spelaShader;

  const consoleKeyMapping = preferences?.consoleKeyMappings[key];
  const resolvedKeyMapping =
    consoleKeyMapping?.selectedMapping ??
    preferences?.selectedKeyMapping ??
    "arrows-left";
  const resolvedCustomMapping =
    resolvedKeyMapping === "custom"
      ? (consoleKeyMapping?.customMapping ??
          preferences?.customKeyMapping ??
          {})
      : undefined;

  return {
    shader: resolvedShader,
    showPerformanceOverlay: preferences?.showPerformanceOverlay ?? false,
    keyMapping: resolvedKeyMapping,
    customKeyMapping: resolvedCustomMapping,
  };
}
