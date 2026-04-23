import { useEffect, type MutableRefObject } from "react";
import { api } from "@/lib/api-client";
import { getBiosFileUrl } from "@/hooks/use-bios";
import type { Game, SessionSave } from "@/types/api";
import type { EmulatorPreferences } from "@/lib/emulator-protocol";
import type { CoreMismatchChoice } from "@/features/play/components/core-mismatch-modal";

/**
 * Subset of the BIOS console status we actually use here — keeps
 * the hook decoupled from whether the caller passes the raw schema
 * object or the narrowed `BiosConsole` type.
 */
interface BiosConsoleLike {
  files?: Array<{ fileName: string; status: string }> | null;
}

interface EmulatorApi {
  initEmulator: (opts: {
    romUrl: string;
    romData?: ArrayBuffer;
    targetDisc?: number;
    core: string;
    gameName: string;
    saveStateData?: string;
    biosUrls?: string[];
    preferences: EmulatorPreferences;
  }) => void;
}

interface UseEmulatorInitArgs {
  // Effect triggers — changes to these re-run the init flow.
  iframeLoaded: boolean;
  gameId: string | undefined;
  isSupported: boolean;
  emulatorJsCore: string | null;
  autoSaveInfoLoading: boolean;

  // Data read freshly on each run.
  game: Game | undefined;
  isFreshStart: boolean;
  biosConsole: BiosConsoleLike | undefined;
  emulatorPrefs: EmulatorPreferences;

  // Mutable state read via refs so we pick up the latest value
  // without having to depend on it (the original code did the same).
  autoSaveInfoRef: MutableRefObject<SessionSave | null | undefined>;
  coreMismatchChoiceRef: MutableRefObject<CoreMismatchChoice | null>;
  pendingDiscSwitchRef: MutableRefObject<{
    targetDisc: number;
    saveData: string;
  } | null>;
  buildMultiDiscBundleRef: MutableRefObject<() => ArrayBuffer | null>;

  // Collaborators.
  emulator: EmulatorApi;
  loadInitialSave: (skipAutoLoad?: boolean) => Promise<string | undefined>;

  // Side effects back into the page.
  onToastError: (message: string) => void;
  onRequestCoreMismatchChoice: () => void;
  onDiscSwitchCurrentChanged: (newCurrentDisc: number) => void;
  onDiscSwitchSettled: () => void;
}

/**
 * Owns the big init `useEffect` that used to live inline in
 * `play-page.tsx`. It runs when the iframe finishes loading and the
 * game data is ready, then picks one of two paths:
 *
 *   1. **Disc-switch path** — when a disc switch is pending and its
 *      save state has arrived, rebuild a combined multi-disc ROM bundle
 *      and re-init the emulator with the target disc + save data.
 *
 *   2. **Normal init path** — pick the ROM URL (single-disc download or
 *      multi-disc zip of disc 1), resolve the save state (skipped on
 *      fresh start or when the user picked fresh/sram-only after a
 *      core mismatch), build authenticated BIOS URLs, and call
 *      `initEmulator`.
 *
 * The hook preserves the original `exhaustive-deps` suppression: the
 * dep array is deliberately narrow (only the five listed triggers)
 * because the other inputs are read via refs or closed-over values
 * whose changes would cause unwanted re-initialisation mid-session.
 *
 * Extracted in #694 (partial). The companion `useDiscSwitchFlow` is
 * still TODO — extracting it cleanly requires a multi-disc test rig
 * to assert no regression on the pause → save → bundle → re-init
 * sequence.
 */
export function useEmulatorInit({
  iframeLoaded,
  gameId,
  isSupported,
  emulatorJsCore,
  autoSaveInfoLoading,
  game,
  isFreshStart,
  biosConsole,
  emulatorPrefs,
  autoSaveInfoRef,
  coreMismatchChoiceRef,
  pendingDiscSwitchRef,
  buildMultiDiscBundleRef,
  emulator,
  loadInitialSave,
  onToastError,
  onRequestCoreMismatchChoice,
  onDiscSwitchCurrentChanged,
  onDiscSwitchSettled,
}: UseEmulatorInitArgs): void {
  useEffect(() => {
    if (!iframeLoaded || !game || !isSupported || !emulatorJsCore) return;
    // Wait for auto-save info to settle before initializing (avoids double-init)
    if (!isFreshStart && autoSaveInfoLoading) return;

    // Disc switch flow: reload with combined bundle + save state + target disc
    if (pendingDiscSwitchRef.current && pendingDiscSwitchRef.current.saveData) {
      const { targetDisc, saveData } = pendingDiscSwitchRef.current;
      const bundle = buildMultiDiscBundleRef.current();
      if (!bundle) {
        onToastError("Failed to build multi-disc bundle");
        pendingDiscSwitchRef.current = null;
        onDiscSwitchSettled();
        return;
      }

      const token = api.getAccessToken();
      const tokenSuffix = token
        ? `?token=${encodeURIComponent(token)}`
        : "";
      const consoleBiosFiles = biosConsole?.files
        ?.filter((f) => f.status !== "missing")
        .map((f) => f.fileName) ?? [];
      const biosUrls =
        consoleBiosFiles.length > 0
          ? consoleBiosFiles.map((f) => getBiosFileUrl(f) + tokenSuffix)
          : undefined;

      onDiscSwitchCurrentChanged(targetDisc + 1);
      pendingDiscSwitchRef.current = null;

      emulator.initEmulator({
        romUrl: "",
        romData: bundle,
        targetDisc,
        core: emulatorJsCore,
        gameName: game.title,
        saveStateData: saveData,
        biosUrls,
        preferences: emulatorPrefs,
      });

      onDiscSwitchSettled();
      return;
    }

    async function init() {
      if (!game || !emulatorJsCore) return;

      const currentAutoSave = autoSaveInfoRef.current;
      if (
        !isFreshStart &&
        currentAutoSave &&
        currentAutoSave.coreMatch === false &&
        !coreMismatchChoiceRef.current
      ) {
        onRequestCoreMismatchChoice();
        return;
      }

      const token = api.getAccessToken();
      const tokenSuffix = token
        ? `?token=${encodeURIComponent(token)}`
        : "";
      // Multi-disc games (e.g. PS1 .m3u): load disc 1 via the disc endpoint.
      // The main /download endpoint serves the .m3u playlist file which
      // EmulatorJS can't use (it needs the actual disc image).
      // For multi-file disc formats (cue+bin), request format=zip since
      // EmulatorJS can extract zip but not tar.
      const isMultiDisc =
        game.discCount > 0 && !!game.discs && game.discs.length > 0;
      // Include the filename in the URL path so EmulatorJS can detect the
      // file extension (e.g. .gg for Game Gear). Without it, genesis_plus_gx
      // can't distinguish Game Gear from Genesis ROMs.
      const romUrl = isMultiDisc
        ? `/api/games/${game.id}/discs/1/download?format=zip${token ? `&token=${encodeURIComponent(token)}` : ""}`
        : `/api/games/${game.id}/download/${encodeURIComponent(game.fileName)}${tokenSuffix}`;

      const choice = coreMismatchChoiceRef.current;
      const skipSaveState = choice === "fresh" || choice === "sram-only";
      const saveStateData = await loadInitialSave(
        isFreshStart || skipSaveState,
      );

      const consoleBiosFiles = biosConsole?.files
        ?.filter((f) => f.status !== "missing")
        .map((f) => f.fileName) ?? [];
      const biosUrls =
        consoleBiosFiles.length > 0
          ? consoleBiosFiles.map((f) => getBiosFileUrl(f) + tokenSuffix)
          : undefined;

      emulator.initEmulator({
        romUrl,
        core: emulatorJsCore,
        gameName: game.title,
        saveStateData,
        biosUrls,
        preferences: emulatorPrefs,
      });
    }

    init();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [iframeLoaded, gameId, isSupported, emulatorJsCore, autoSaveInfoLoading]);
}
