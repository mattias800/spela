import { useCallback, useRef, useState } from "react";
import { api } from "@/lib/api-client";
import { uint8ArrayToBase64 } from "@/lib/encoding";
import { useSaveQueue } from "./use-save-queue";
import { useAutoSave } from "./use-auto-save";
import { useBeforeUnloadSave } from "./use-before-unload-save";
import type { SaveState, UserPreferences } from "@/types/api";
import type { EmulatorStatus } from "./use-emulator-iframe";

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
  const [isLoadingInitialSave, setIsLoadingInitialSave] = useState(false);
  const pendingSaveTypeRef = useRef<"auto" | "manual">("auto");
  const pendingSaveNameRef = useRef<string | undefined>(undefined);
  const latestStateCacheRef = useRef<string | null>(null);
  const exitSaveResolveRef = useRef<(() => void) | null>(null);

  const {
    isSaving,
    enqueueSave: queueEnqueue,
    queueRef,
  } = useSaveQueue({
    onSaveSuccess,
    onSaveError,
  });

  // Wrap enqueueSave to bind gameId
  const enqueueSave = useCallback(
    (data: string, isAuto: boolean, name?: string) => {
      if (!gameId) return;
      queueEnqueue(gameId, data, isAuto, name);
    },
    [gameId, queueEnqueue],
  );

  // Handle incoming save state data from the iframe
  const handleSaveStateData = useCallback(
    (data: string, _screenshot?: string) => {
      latestStateCacheRef.current = data;

      const exitResolve = exitSaveResolveRef.current;
      if (exitResolve) {
        exitSaveResolveRef.current = null;
        if (gameId) {
          const bytes = Uint8Array.from(atob(data), (c) => c.charCodeAt(0));
          const blob = new Blob([bytes]);
          const formData = new FormData();
          formData.append("save", blob, "auto-save.state");
          api
            .upload(`/games/${gameId}/saves/auto`, formData)
            .then(() => exitResolve())
            .catch(() => exitResolve());
        } else {
          exitResolve();
        }
        return;
      }

      const isAuto = pendingSaveTypeRef.current === "auto";
      const name = isAuto ? undefined : pendingSaveNameRef.current;
      pendingSaveNameRef.current = undefined;
      enqueueSave(data, isAuto, name);
    },
    [enqueueSave, gameId],
  );

  const requestManualSave = useCallback(
    (name?: string) => {
      pendingSaveTypeRef.current = "manual";
      pendingSaveNameRef.current = name;
      requestSaveState();
    },
    [requestSaveState],
  );

  const requestAutoSave = useCallback(() => {
    pendingSaveTypeRef.current = "auto";
    requestSaveState();
  }, [requestSaveState]);

  const requestExitSave = useCallback((): Promise<void> => {
    return new Promise<void>((resolve) => {
      if (!gameId || !preferences?.autoSaveEnabled) {
        resolve();
        return;
      }
      exitSaveResolveRef.current = resolve;
      pendingSaveTypeRef.current = "auto";
      requestSaveState();
    });
  }, [gameId, preferences?.autoSaveEnabled, requestSaveState]);

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
      return uint8ArrayToBase64(new Uint8Array(buf));
    } catch {
      return undefined;
    } finally {
      setIsLoadingInitialSave(false);
    }
  }, [gameId, preferences?.autoLoadSaveEnabled]);

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
        return uint8ArrayToBase64(new Uint8Array(buf));
      } catch {
        return undefined;
      }
    },
    [gameId],
  );

  useAutoSave({
    emulatorStatus,
    autoSaveEnabled: preferences?.autoSaveEnabled ?? false,
    requestAutoSave,
  });

  useBeforeUnloadSave({
    emulatorStatus,
    autoSaveEnabled: preferences?.autoSaveEnabled ?? false,
    gameId,
    queueRef,
    latestStateCacheRef,
  });

  return {
    isSaving,
    isLoadingInitialSave,
    handleSaveStateData,
    requestManualSave,
    requestAutoSave,
    requestExitSave,
    loadInitialSave,
    loadSave,
    enqueueSave,
    pendingCount: queueRef.current.length,
  };
}
