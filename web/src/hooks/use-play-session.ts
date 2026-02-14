import { useCallback, useEffect, useRef } from "react";
import { api } from "@/lib/api-client";
import type { EmulatorStatus } from "./use-emulator-iframe";

export function usePlaySession(
  gameId: string | undefined,
  emulatorStatus: EmulatorStatus,
) {
  const lastReportedRef = useRef<number>(Date.now());

  const flushPlayTime = useCallback(() => {
    if (!gameId) return;
    const now = Date.now();
    const seconds = Math.floor((now - lastReportedRef.current) / 1000);
    if (seconds > 5) {
      lastReportedRef.current = now;
      api.post(`/games/${gameId}/play-time`, { seconds }).catch(() => {});
    }
  }, [gameId]);

  useEffect(() => {
    if (emulatorStatus !== "playing") return;

    const interval = setInterval(flushPlayTime, 5 * 60 * 1000);
    return () => {
      clearInterval(interval);
      flushPlayTime();
    };
  }, [emulatorStatus, flushPlayTime]);

  return { flushPlayTime };
}
