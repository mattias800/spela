import { useState, useCallback, useEffect } from "react";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useScrapeStatus } from "@/hooks/use-admin";

interface ScrapeProgressPayload {
  current: number;
  total: number;
  gameId: number;
  gameName: string;
  consoleName: string;
  consoleAbbr: string;
  successes: number;
  failures: number;
  verified: number;
}

interface ScrapeCompletePayload {
  scraped: number;
  total: number;
}

interface ScrapeErrorPayload {
  error: string;
}

export type ScrapePhase = "idle" | "active" | "complete" | "error";

export interface ScrapeProgressState {
  phase: ScrapePhase;
  current: number;
  total: number;
  gameId: number;
  gameName: string;
  consoleName: string;
  consoleAbbr: string;
  successes: number;
  failures: number;
  verified: number;
  error: string | null;
  dismiss: () => void;
}

export function useScrapeProgress(): ScrapeProgressState {
  const { data: initialStatus } = useScrapeStatus();

  const [phase, setPhase] = useState<ScrapePhase>("idle");
  const [current, setCurrent] = useState(0);
  const [total, setTotal] = useState(0);
  const [gameId, setGameId] = useState(0);
  const [gameName, setGameName] = useState("");
  const [consoleName, setConsoleName] = useState("");
  const [consoleAbbr, setConsoleAbbr] = useState("");
  const [successes, setSuccesses] = useState(0);
  const [failures, setFailures] = useState(0);
  const [verified, setVerified] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    if (!initialStatus || initialized) return;
    setInitialized(true);
    if (initialStatus.active) {
      setPhase("active");
      setCurrent(initialStatus.current ?? 0);
      setTotal(initialStatus.total ?? 0);
      setGameId(initialStatus.gameId ?? 0);
      setGameName(initialStatus.gameName ?? "");
      setConsoleName(initialStatus.consoleName ?? "");
      setConsoleAbbr(initialStatus.consoleAbbr ?? "");
      setSuccesses(initialStatus.successes ?? 0);
      setFailures(initialStatus.failures ?? 0);
      setVerified(initialStatus.verified ?? 0);
    }
  }, [initialStatus, initialized]);

  useWebSocketEvent(
    "scrape_started",
    useCallback(() => {
      setPhase("active");
      setCurrent(0);
      setTotal(0);
      setGameId(0);
      setGameName("");
      setConsoleName("");
      setConsoleAbbr("");
      setSuccesses(0);
      setFailures(0);
      setVerified(0);
      setError(null);
    }, []),
  );

  useWebSocketEvent(
    "scrape_progress",
    useCallback((payload: ScrapeProgressPayload) => {
      setPhase("active");
      setCurrent(payload.current);
      setTotal(payload.total);
      setGameId(payload.gameId);
      setGameName(payload.gameName);
      setConsoleName(payload.consoleName);
      setConsoleAbbr(payload.consoleAbbr);
      setSuccesses(payload.successes);
      setFailures(payload.failures);
      setVerified(payload.verified);
    }, []),
  );

  useWebSocketEvent(
    "scrape_complete",
    useCallback((payload: ScrapeCompletePayload) => {
      setPhase("complete");
      setSuccesses(payload.scraped);
      setTotal(payload.total);
    }, []),
  );

  useWebSocketEvent(
    "scrape_error",
    useCallback((payload: ScrapeErrorPayload) => {
      setPhase("error");
      setError(payload.error);
    }, []),
  );

  const dismiss = useCallback(() => {
    setPhase("idle");
    setCurrent(0);
    setTotal(0);
    setGameId(0);
    setGameName("");
    setConsoleName("");
    setConsoleAbbr("");
    setSuccesses(0);
    setFailures(0);
    setVerified(0);
    setError(null);
  }, []);

  return { phase, current, total, gameId, gameName, consoleName, consoleAbbr, successes, failures, verified, error, dismiss };
}
