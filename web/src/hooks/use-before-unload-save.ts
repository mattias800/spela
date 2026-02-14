import { useEffect, type MutableRefObject } from "react";
import type { EmulatorStatus } from "./use-emulator-iframe";
import type { SaveQueueItem } from "./use-save-queue";

interface UseBeforeUnloadSaveOptions {
  emulatorStatus: EmulatorStatus;
  autoSaveEnabled: boolean;
  gameId: string | undefined;
  queueRef: MutableRefObject<SaveQueueItem[]>;
  latestStateCacheRef: MutableRefObject<string | null>;
}

export function useBeforeUnloadSave({
  emulatorStatus,
  autoSaveEnabled,
  gameId,
  queueRef,
  latestStateCacheRef,
}: UseBeforeUnloadSaveOptions) {
  useEffect(() => {
    if (emulatorStatus !== "playing" || !autoSaveEnabled || !gameId) return;

    function handleBeforeUnload() {
      // Flush any pending saves via sendBeacon
      for (const item of queueRef.current) {
        try {
          const bytes = Uint8Array.from(atob(item.data), (c) =>
            c.charCodeAt(0),
          );
          const formData = new FormData();
          formData.append("save", new Blob([bytes]), "auto-save.state");
          navigator.sendBeacon(
            `/api/games/${item.gameId}/saves/auto`,
            formData,
          );
        } catch {
          // Best effort
        }
      }

      // Send cached state via sendBeacon (at most 60s stale)
      const cached = latestStateCacheRef.current;
      if (cached) {
        try {
          const bytes = Uint8Array.from(atob(cached), (c) => c.charCodeAt(0));
          const formData = new FormData();
          formData.append("save", new Blob([bytes]), "auto-save.state");
          navigator.sendBeacon(`/api/games/${gameId}/saves/auto`, formData);
        } catch {
          // Best effort
        }
      }
    }

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [emulatorStatus, autoSaveEnabled, gameId, queueRef, latestStateCacheRef]);
}
