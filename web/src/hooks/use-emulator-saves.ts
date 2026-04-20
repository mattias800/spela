import { useCallback, useRef, useState } from "react";
import { api, multipart, typedApi, unwrap } from "@/lib/api-client";
import { uint8ArrayToBase64 } from "@/lib/encoding";
import { useSaveQueue } from "./use-save-queue";
import { useAutoSave } from "./use-auto-save";
import { useBeforeUnloadSave } from "./use-before-unload-save";
import type { UserPreferences } from "@/types/api";
import type { EmulatorStatus } from "./use-emulator-iframe";

interface UseEmulatorSavesOptions {
  sessionId: string | undefined;
  emulatorStatus: EmulatorStatus;
  preferences: UserPreferences | undefined;
  onSaveSuccess?: (isAuto: boolean) => void;
  onSaveError?: (error: string, isAuto: boolean) => void;
  requestSaveState: () => void;
}

export function useEmulatorSaves({
  sessionId,
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

  // Wrap enqueueSave to bind sessionId
  const enqueueSave = useCallback(
    (data: string, isAuto: boolean, name?: string, screenshot?: string) => {
      if (!sessionId) return;
      queueEnqueue(sessionId, data, isAuto, name, screenshot);
    },
    [sessionId, queueEnqueue],
  );

  // Handle incoming save state data from the iframe
  const handleSaveStateData = useCallback(
    (data: string, screenshot?: string) => {
      latestStateCacheRef.current = data;

      const exitResolve = exitSaveResolveRef.current;
      if (exitResolve) {
        exitSaveResolveRef.current = null;
        if (sessionId) {
          const bytes = Uint8Array.from(atob(data), (c) => c.charCodeAt(0));
          const blob = new Blob([bytes]);
          const formData = new FormData();
          formData.append("save", blob, "auto-save.state");
          if (screenshot) {
            const ssBase64 = screenshot.replace(/^data:image\/\w+;base64,/, "");
            const ssBytes = Uint8Array.from(atob(ssBase64), (c) => c.charCodeAt(0));
            formData.append("screenshot", new Blob([ssBytes], { type: "image/png" }), "screenshot.png");
          }
          unwrap(
            typedApi.POST("/api/sessions/{id}/saves/auto", {
              params: { path: { id: sessionId } },
              ...multipart(formData),
            }),
          )
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
      enqueueSave(data, isAuto, name, screenshot);
    },
    [enqueueSave, sessionId],
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
      if (!sessionId || !preferences?.autoSaveEnabled) {
        resolve();
        return;
      }
      exitSaveResolveRef.current = resolve;
      pendingSaveTypeRef.current = "auto";
      requestSaveState();
    });
  }, [sessionId, preferences?.autoSaveEnabled, requestSaveState]);

  const loadInitialSave = useCallback(async (skipAutoLoad?: boolean): Promise<string | undefined> => {
    if (!sessionId || !preferences?.autoLoadSaveEnabled || skipAutoLoad) return undefined;

    setIsLoadingInitialSave(true);
    try {
      const token = api.getAccessToken();
      const res = await fetch(`/api/sessions/${sessionId}/saves/auto`, {
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
  }, [sessionId, preferences?.autoLoadSaveEnabled]);

  useAutoSave({
    emulatorStatus,
    autoSaveEnabled: preferences?.autoSaveEnabled ?? false,
    requestAutoSave,
  });

  useBeforeUnloadSave({
    emulatorStatus,
    autoSaveEnabled: preferences?.autoSaveEnabled ?? false,
    sessionId,
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
    enqueueSave,
    pendingCount: queueRef.current.length,
  };
}
