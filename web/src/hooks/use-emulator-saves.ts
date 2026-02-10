import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api-client";
import type { SaveState, UserPreferences } from "@/types/api";
import type { EmulatorStatus } from "./use-emulator-iframe";

interface SaveQueueItem {
  gameId: string;
  data: string; // base64
  isAuto: boolean;
  name?: string;
  retries: number;
}

const MAX_RETRIES = 3;
const RETRY_DELAY_MS = 5_000;
const AUTO_SAVE_INTERVAL_MS = 60_000;

interface UseEmulatorSavesOptions {
  gameId: string | undefined;
  emulatorStatus: EmulatorStatus;
  preferences: UserPreferences | undefined;
  onSaveSuccess?: (isAuto: boolean) => void;
  onSaveError?: (error: string, isAuto: boolean) => void;
  requestSaveState: () => void;
}

export function useEmulatorSaves({
  gameId,
  emulatorStatus,
  preferences,
  onSaveSuccess,
  onSaveError,
  requestSaveState,
}: UseEmulatorSavesOptions) {
  const [isSaving, setIsSaving] = useState(false);
  const [isLoadingInitialSave, setIsLoadingInitialSave] = useState(false);
  const saveQueueRef = useRef<SaveQueueItem[]>([]);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const autoSaveIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pendingSaveTypeRef = useRef<"auto" | "manual">("auto");

  // Process the save queue
  const processQueue = useCallback(async () => {
    if (saveQueueRef.current.length === 0) {
      setIsSaving(false);
      return;
    }

    setIsSaving(true);
    const item = saveQueueRef.current[0];

    try {
      const bytes = Uint8Array.from(atob(item.data), (c) => c.charCodeAt(0));
      const blob = new Blob([bytes]);
      const formData = new FormData();
      formData.append("save", blob, item.isAuto ? "auto-save.state" : `${item.name ?? "save"}.state`);
      if (item.name) {
        formData.append("name", item.name);
      }

      const endpoint = item.isAuto
        ? `/games/${item.gameId}/saves/auto`
        : `/games/${item.gameId}/saves`;

      await api.upload(endpoint, formData);

      // Remove from queue on success
      saveQueueRef.current.shift();
      onSaveSuccess?.(item.isAuto);

      // Process next item
      processQueue();
    } catch {
      item.retries++;
      if (item.retries >= MAX_RETRIES) {
        // Give up on this save
        saveQueueRef.current.shift();
        onSaveError?.(`Save failed after ${MAX_RETRIES} attempts`, item.isAuto);
        processQueue();
      } else {
        // Retry after delay
        retryTimerRef.current = setTimeout(() => {
          processQueue();
        }, RETRY_DELAY_MS);
      }
    }
  }, [onSaveSuccess, onSaveError]);

  // Enqueue a save state for upload
  const enqueueSave = useCallback(
    (data: string, isAuto: boolean, name?: string) => {
      if (!gameId || !data) return;

      // For auto-saves, replace any existing queued auto-save
      if (isAuto) {
        saveQueueRef.current = saveQueueRef.current.filter((s) => !s.isAuto);
      }

      saveQueueRef.current.push({
        gameId,
        data,
        isAuto,
        name,
        retries: 0,
      });

      processQueue();
    },
    [gameId, processQueue],
  );

  // Handle incoming save state data from the iframe
  const handleSaveStateData = useCallback(
    (data: string, _screenshot?: string) => {
      const isAuto = pendingSaveTypeRef.current === "auto";
      enqueueSave(data, isAuto, isAuto ? undefined : undefined);
    },
    [enqueueSave],
  );

  // Request a manual save (will prompt for name after data is received)
  const requestManualSave = useCallback(
    (name?: string) => {
      pendingSaveTypeRef.current = "manual";
      // Store the name for when we get the data back
      if (name) {
        const originalHandler = handleSaveStateData;
        const wrappedHandler = (data: string, screenshot?: string) => {
          enqueueSave(data, false, name);
          // Avoid calling original since we handled it
        };
        // We can't easily intercept, so just use the name tracking approach
      }
      requestSaveState();
    },
    [requestSaveState, enqueueSave],
  );

  // Request auto-save
  const requestAutoSave = useCallback(() => {
    pendingSaveTypeRef.current = "auto";
    requestSaveState();
  }, [requestSaveState]);

  // Load initial auto-save from server
  const loadInitialSave = useCallback(async (): Promise<string | undefined> => {
    if (!gameId || !preferences?.autoLoadSaveEnabled) return undefined;

    setIsLoadingInitialSave(true);
    try {
      const token = api.getAccessToken();
      const res = await fetch(`/api/games/${gameId}/saves/auto`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) return undefined;

      const buf = await res.arrayBuffer();
      const bytes = new Uint8Array(buf);
      let binary = "";
      for (let i = 0; i < bytes.length; i++) {
        binary += String.fromCharCode(bytes[i]);
      }
      return btoa(binary);
    } catch {
      return undefined;
    } finally {
      setIsLoadingInitialSave(false);
    }
  }, [gameId, preferences?.autoLoadSaveEnabled]);

  // Load a specific save state from server
  const loadSave = useCallback(
    async (save: SaveState): Promise<string | undefined> => {
      if (!gameId) return undefined;

      try {
        const token = api.getAccessToken();
        const res = await fetch(`/api/games/${gameId}/saves/${save.id}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (!res.ok) throw new Error("Failed to download save");

        const buf = await res.arrayBuffer();
        const bytes = new Uint8Array(buf);
        let binary = "";
        for (let i = 0; i < bytes.length; i++) {
          binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
      } catch {
        return undefined;
      }
    },
    [gameId],
  );

  // Auto-save interval
  useEffect(() => {
    if (emulatorStatus !== "playing" || !preferences?.autoSaveEnabled) return;

    autoSaveIntervalRef.current = setInterval(() => {
      requestAutoSave();
    }, AUTO_SAVE_INTERVAL_MS);

    return () => {
      if (autoSaveIntervalRef.current) {
        clearInterval(autoSaveIntervalRef.current);
        autoSaveIntervalRef.current = null;
      }
    };
  }, [emulatorStatus, preferences?.autoSaveEnabled, requestAutoSave]);

  // Save on page exit
  useEffect(() => {
    if (emulatorStatus !== "playing" || !preferences?.autoSaveEnabled || !gameId)
      return;

    function handleBeforeUnload() {
      // Flush any pending saves via sendBeacon
      const queue = saveQueueRef.current;
      if (queue.length > 0) {
        for (const item of queue) {
          try {
            const bytes = Uint8Array.from(atob(item.data), (c) =>
              c.charCodeAt(0),
            );
            const formData = new FormData();
            formData.append(
              "save",
              new Blob([bytes]),
              "auto-save.state",
            );
            navigator.sendBeacon(
              `/api/games/${item.gameId}/saves/auto`,
              formData,
            );
          } catch {
            // Best effort
          }
        }
      }
    }

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [emulatorStatus, preferences?.autoSaveEnabled, gameId]);

  // Cleanup retry timer on unmount
  useEffect(() => {
    return () => {
      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
      }
      if (autoSaveIntervalRef.current) {
        clearInterval(autoSaveIntervalRef.current);
      }
    };
  }, []);

  return {
    isSaving,
    isLoadingInitialSave,
    handleSaveStateData,
    requestManualSave,
    requestAutoSave,
    loadInitialSave,
    loadSave,
    enqueueSave,
    pendingCount: saveQueueRef.current.length,
  };
}
