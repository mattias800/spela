import { useRef, useEffect, useCallback, useState } from "react";
import type {
  ParentToIframeMessage,
  IframeToParentMessage,
  EmulatorPreferences,
} from "@/lib/emulator-protocol";
import { isIframeMessage } from "@/lib/emulator-protocol";

export type EmulatorStatus =
  | "loading"
  | "ready"
  | "playing"
  | "error"
  | "saving";

interface EmulatorIframeOptions {
  onReady?: () => void;
  onGameStarted?: () => void;
  onSaveStateResponse?: (data: string, screenshot?: string) => void;
  onSaveStateError?: (error: string) => void;
  onError?: (error: string) => void;
  onRomLoadProgress?: (loaded: number, total: number) => void;
  onSramUpdate?: (data: string) => void;
}

export function useEmulatorIframe(options: EmulatorIframeOptions = {}) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const [status, setStatus] = useState<EmulatorStatus>("loading");
  const [romProgress, setRomProgress] = useState<{
    loaded: number;
    total: number;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Send a typed message to the iframe
  const sendMessage = useCallback((msg: ParentToIframeMessage) => {
    const iframe = iframeRef.current;
    if (!iframe?.contentWindow) return;
    const transfer: Transferable[] = [];
    if ("romData" in msg && msg.romData instanceof ArrayBuffer) {
      transfer.push(msg.romData);
    }
    iframe.contentWindow.postMessage(msg, window.location.origin, transfer);
  }, []);

  // Listen for messages from the iframe
  useEffect(() => {
    function handleMessage(event: MessageEvent) {
      // Only accept messages from our own origin
      if (event.origin !== window.location.origin) return;
      if (!isIframeMessage(event.data)) return;

      const msg: IframeToParentMessage = event.data;

      switch (msg.type) {
        case "emulator-ready":
          setStatus("ready");
          optionsRef.current.onReady?.();
          break;
        case "game-started":
          setStatus("playing");
          setRomProgress(null);
          optionsRef.current.onGameStarted?.();
          break;
        case "save-state-response":
          setStatus("playing");
          optionsRef.current.onSaveStateResponse?.(msg.data, msg.screenshot);
          break;
        case "save-state-error":
          setStatus("playing");
          optionsRef.current.onSaveStateError?.(msg.error);
          break;
        case "emulator-error":
          setStatus("error");
          setError(msg.error);
          optionsRef.current.onError?.(msg.error);
          break;
        case "rom-load-progress":
          setRomProgress({ loaded: msg.loaded, total: msg.total });
          optionsRef.current.onRomLoadProgress?.(msg.loaded, msg.total);
          break;
        case "sram-update":
          optionsRef.current.onSramUpdate?.(msg.data);
          break;
      }
    }

    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, []);

  // High-level commands
  const initEmulator = useCallback(
    (config: {
      romUrl: string;
      romData?: ArrayBuffer;
      targetDisc?: number;
      core: string;
      gameName: string;
      saveStateData?: string;
      biosUrls?: string[];
      preferences: EmulatorPreferences;
    }) => {
      setStatus("loading");
      setError(null);
      sendMessage({ type: "init", ...config });
    },
    [sendMessage],
  );

  const requestSaveState = useCallback(() => {
    setStatus("saving");
    sendMessage({ type: "request-save-state" });
  }, [sendMessage]);

  const loadSaveState = useCallback(
    (data: string, name: string) => {
      sendMessage({ type: "load-save-state", data, name });
    },
    [sendMessage],
  );

  const updatePreferences = useCallback(
    (preferences: EmulatorPreferences) => {
      sendMessage({ type: "update-preferences", preferences });
    },
    [sendMessage],
  );

  const pause = useCallback(() => {
    sendMessage({ type: "pause" });
  }, [sendMessage]);

  const resume = useCallback(() => {
    sendMessage({ type: "resume" });
  }, [sendMessage]);

  const focusEmulator = useCallback(() => {
    setTimeout(() => {
      iframeRef.current?.focus();
    }, 50);
  }, []);

  return {
    iframeRef,
    status,
    romProgress,
    error,
    initEmulator,
    requestSaveState,
    loadSaveState,
    updatePreferences,
    pause,
    resume,
    focusEmulator,
  };
}
