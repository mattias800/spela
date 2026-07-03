import { useCallback, useEffect, useRef, useState } from "react";
import { typedApi } from "@/lib/api-client";
import type { EmulatorStatus } from "./use-emulator-iframe";

export function usePlaySession(
  gameId: string | undefined,
  emulatorStatus: EmulatorStatus,
) {
  const [initialReportTime] = useState(() => Date.now());
  const lastReportedRef = useRef<number>(initialReportTime);

  const flushPlayTime = useCallback(() => {
    if (!gameId) return;
    const now = Date.now();
    const seconds = Math.floor((now - lastReportedRef.current) / 1000);
    if (seconds > 5) {
      lastReportedRef.current = now;
      typedApi
        .POST("/api/games/{id}/play-time", {
          params: { path: { id: gameId } },
          body: { seconds },
        })
        .catch(() => {});
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
