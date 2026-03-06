import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api-client";

export interface SaveQueueItem {
  sessionId: string;
  data: string; // base64
  isAuto: boolean;
  name?: string;
  screenshot?: string; // data URL (data:image/png;base64,...)
  retries: number;
}

const MAX_RETRIES = 3;
const RETRY_DELAY_MS = 5_000;

interface UseSaveQueueOptions {
  onSaveSuccess?: (isAuto: boolean) => void;
  onSaveError?: (error: string, isAuto: boolean) => void;
}

export function useSaveQueue({
  onSaveSuccess,
  onSaveError,
}: UseSaveQueueOptions) {
  const [isSaving, setIsSaving] = useState(false);
  const saveQueueRef = useRef<SaveQueueItem[]>([]);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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
      formData.append(
        "save",
        blob,
        item.isAuto ? "auto-save.state" : `${item.name ?? "save"}.state`,
      );
      if (item.name) {
        formData.append("name", item.name);
      }
      if (item.screenshot) {
        const ssBase64 = item.screenshot.replace(/^data:image\/\w+;base64,/, "");
        const ssBytes = Uint8Array.from(atob(ssBase64), (c) => c.charCodeAt(0));
        formData.append("screenshot", new Blob([ssBytes], { type: "image/png" }), "screenshot.png");
      }

      const endpoint = item.isAuto
        ? `/sessions/${item.sessionId}/saves/auto`
        : `/sessions/${item.sessionId}/saves`;

      await api.upload(endpoint, formData);

      saveQueueRef.current.shift();
      onSaveSuccess?.(item.isAuto);

      processQueue();
    } catch {
      item.retries++;
      if (item.retries >= MAX_RETRIES) {
        saveQueueRef.current.shift();
        onSaveError?.(`Save failed after ${MAX_RETRIES} attempts`, item.isAuto);
        processQueue();
      } else {
        retryTimerRef.current = setTimeout(() => {
          processQueue();
        }, RETRY_DELAY_MS);
      }
    }
  }, [onSaveSuccess, onSaveError]);

  const enqueueSave = useCallback(
    (sessionId: string, data: string, isAuto: boolean, name?: string, screenshot?: string) => {
      if (!sessionId || !data) return;

      if (isAuto) {
        saveQueueRef.current = saveQueueRef.current.filter((s) => !s.isAuto);
      }

      saveQueueRef.current.push({
        sessionId,
        data,
        isAuto,
        name,
        screenshot,
        retries: 0,
      });

      processQueue();
    },
    [processQueue],
  );

  // Cleanup retry timer on unmount
  useEffect(() => {
    return () => {
      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
      }
    };
  }, []);

  return { isSaving, enqueueSave, queueRef: saveQueueRef };
}
