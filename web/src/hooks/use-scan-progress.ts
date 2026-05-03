import { useState, useCallback, useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useScanStatus } from "@/hooks/use-admin";

interface ScanProgressPayload {
  phase: string;
  current: number;
  total: number;
  message: string;
}

interface ScanCompletePayload {
  newGames: number;
  updatedGames: number;
  removedGames: number;
  totalGames: number;
  newGamesList?: { id: string; title: string; consoleName: string }[];
}

interface ScanErrorPayload {
  error: string;
}

export type ScanPhase = "idle" | "active" | "complete" | "error";

export interface ScanProgressState {
  phase: ScanPhase;
  message: string;
  current: number;
  total: number;
  result: ScanCompletePayload | null;
  error: string | null;
  dismiss: () => void;
}

export function useScanProgress(enabled: boolean = true): ScanProgressState {
  const { data: initialStatus } = useScanStatus(enabled);
  const queryClient = useQueryClient();

  const [phase, setPhase] = useState<ScanPhase>("idle");
  const [message, setMessage] = useState("");
  const [current, setCurrent] = useState(0);
  const [total, setTotal] = useState(0);
  const [result, setResult] = useState<ScanCompletePayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Sync from polled status — runs on every refetch (3s interval) so the UI
  // recovers after page refresh or WebSocket disconnect. The `complete`/`error`
  // early return prevents the (lagging) poll response from clobbering a
  // terminal phase the WebSocket has already set.
  useEffect(() => {
    if (!initialStatus) return;
    if (phase === "complete" || phase === "error") return;
    if (initialStatus.active) {
      setPhase("active");
      setMessage(initialStatus.message || "Scanning...");
      setCurrent(initialStatus.current);
      setTotal(initialStatus.total);
    } else if (phase === "active") {
      setPhase("idle");
    }
  }, [initialStatus, phase]);

  useWebSocketEvent(
    "scan_started",
    useCallback(() => {
      setPhase("active");
      setMessage("Starting scan...");
      setCurrent(0);
      setTotal(0);
      setResult(null);
      setError(null);
    }, []),
  );

  useWebSocketEvent(
    "scan_progress",
    useCallback((payload: ScanProgressPayload) => {
      setPhase("active");
      setMessage(payload.message);
      setCurrent(payload.current);
      setTotal(payload.total);
    }, []),
  );

  useWebSocketEvent(
    "scan_complete",
    useCallback(
      (payload: ScanCompletePayload) => {
        setPhase("complete");
        setResult(payload);
        queryClient.invalidateQueries({ queryKey: ["games"] });
        queryClient.invalidateQueries({ queryKey: ["consoles"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "stats"] });
      },
      [queryClient],
    ),
  );

  useWebSocketEvent(
    "scan_error",
    useCallback((payload: ScanErrorPayload) => {
      setPhase("error");
      setError(payload.error);
    }, []),
  );

  const dismiss = useCallback(() => {
    setPhase("idle");
    setMessage("");
    setCurrent(0);
    setTotal(0);
    setResult(null);
    setError(null);
  }, []);

  return { phase, message, current, total, result, error, dismiss };
}
