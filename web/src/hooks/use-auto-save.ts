import { useEffect, useRef } from "react";
import type { EmulatorStatus } from "./use-emulator-iframe";

const AUTO_SAVE_INTERVAL_MS = 60_000;

interface UseAutoSaveOptions {
  emulatorStatus: EmulatorStatus;
  autoSaveEnabled: boolean;
  requestAutoSave: () => void;
}

export function useAutoSave({
  emulatorStatus,
  autoSaveEnabled,
  requestAutoSave,
}: UseAutoSaveOptions) {
  const autoSaveIntervalRef = useRef<ReturnType<typeof setInterval> | null>(
    null,
  );

  useEffect(() => {
    if (emulatorStatus !== "playing" || !autoSaveEnabled) return;

    autoSaveIntervalRef.current = setInterval(() => {
      requestAutoSave();
    }, AUTO_SAVE_INTERVAL_MS);

    return () => {
      if (autoSaveIntervalRef.current) {
        clearInterval(autoSaveIntervalRef.current);
        autoSaveIntervalRef.current = null;
      }
    };
  }, [emulatorStatus, autoSaveEnabled, requestAutoSave]);
}
