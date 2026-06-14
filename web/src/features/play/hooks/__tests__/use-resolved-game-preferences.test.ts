import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import type { UserPreferences } from "@/types/api";
import { useResolvedGamePreferences } from "@/features/play/hooks/use-resolved-game-preferences";

function prefs(overrides: Partial<UserPreferences> = {}): UserPreferences {
  return {
    autoLoadSaveEnabled: true,
    autoSaveEnabled: true,
    autoUpdateCoresEnabled: false,
    consoleKeyMappings: {},
    consoleSaveStatePolicies: {},
    gameSaveStatePolicies: {},
    consoleShaders: {},
    customKeyMapping: {},
    defaultSecondScreenPage: "controls",
    preferredRegions: [],
    raHardcoreEnabled: false,
    raLinked: false,
    raUsername: "",
    selectedKeyMapping: "arrows-left",
    selectedShader: "none",
    selectedTheme: "default",
    showPerformanceOverlay: false,
    ...overrides,
  };
}

describe("useResolvedGamePreferences", () => {
  it("falls back to defaults when preferences are undefined", () => {
    const { result } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: undefined,
        consoleId: "snes",
      }),
    );

    expect(result.current).toMatchObject({
      shader: "none",
      showPerformanceOverlay: false,
      keyMapping: "arrows-left",
      customKeyMapping: undefined,
    });
  });

  it("uses per-console shader override when set", () => {
    const { result } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({
          selectedShader: "crt-hylian",
          consoleShaders: { snes: "crt-lottes" },
        }),
        consoleId: "snes",
      }),
    );

    // crt-lottes has no EmulatorJS mapping, so it passes through unchanged
    expect(result.current.shader).toBe("crt-lottes");
  });

  it("falls back to global shader when console has no override", () => {
    const { result } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({
          selectedShader: "crt-hylian",
          consoleShaders: { snes: "crt-lottes" },
        }),
        consoleId: "gba",
      }),
    );

    expect(result.current.shader).toBe("crt-hylian");
  });

  it("uses per-console key mapping override when set", () => {
    const { result } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({
          selectedKeyMapping: "arrows-left",
          consoleKeyMappings: {
            snes: { selectedMapping: "wasd-left", customMapping: {}, positionMappings: {} },
          },
        }),
        consoleId: "snes",
      }),
    );

    expect(result.current.keyMapping).toBe("wasd-left");
  });

  it("resolves custom mapping only when the chosen mapping is custom", () => {
    const global = { button_a: "K" };
    const consoleCustom = { button_a: "J" };

    const { result: resultCustom } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({
          selectedKeyMapping: "custom",
          customKeyMapping: global,
          consoleKeyMappings: {
            snes: {
              selectedMapping: "custom",
              customMapping: consoleCustom,
              positionMappings: {},
            },
          },
        }),
        consoleId: "snes",
      }),
    );

    expect(resultCustom.current.keyMapping).toBe("custom");
    expect(resultCustom.current.customKeyMapping).toEqual(consoleCustom);

    const { result: resultNonCustom } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({
          selectedKeyMapping: "arrows-left",
          customKeyMapping: global,
        }),
        consoleId: "snes",
      }),
    );

    expect(resultNonCustom.current.keyMapping).toBe("arrows-left");
    expect(resultNonCustom.current.customKeyMapping).toBeUndefined();
  });

  it("falls back to global custom mapping when console has no custom override", () => {
    const { result } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({
          selectedKeyMapping: "custom",
          customKeyMapping: { button_a: "K" },
          consoleKeyMappings: {
            snes: { selectedMapping: "custom", customMapping: {}, positionMappings: {} },
          },
        }),
        consoleId: "gba",
      }),
    );

    expect(result.current.keyMapping).toBe("custom");
    expect(result.current.customKeyMapping).toEqual({ button_a: "K" });
  });

  it("passes showPerformanceOverlay through from preferences", () => {
    const { result } = renderHook(() =>
      useResolvedGamePreferences({
        preferences: prefs({ showPerformanceOverlay: true }),
        consoleId: "snes",
      }),
    );

    expect(result.current.showPerformanceOverlay).toBe(true);
  });
});
