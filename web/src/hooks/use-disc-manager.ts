import { useState, useEffect, useCallback, useRef } from "react";
import { api } from "@/lib/api-client";
import { extractZipStore, createZipStore } from "@/lib/zip-utils";
import type { Game } from "@/types/api";
import type { EmulatorStatus } from "@/hooks/use-emulator-iframe";

export interface DiscState {
  discNumber: number;
  fileName: string;
  status: "pending" | "downloading" | "ready" | "error";
  progress: number; // 0-1
  data: ArrayBuffer | null;
}

export interface UseDiscManagerResult {
  discStates: DiscState[];
  isMultiDisc: boolean;
  allDiscsReady: boolean;
  buildMultiDiscBundle: () => ArrayBuffer | null;
  retryDisc: (discNumber: number) => void;
}

export function useDiscManager({
  game,
  emulatorStatus,
  onDiscError,
}: {
  game: Game | undefined;
  emulatorStatus: EmulatorStatus;
  onDiscError?: (discNumber: number, error: string) => void;
}): UseDiscManagerResult {
  const [discStates, setDiscStates] = useState<DiscState[]>([]);
  const downloadingRef = useRef(false);
  const onDiscErrorRef = useRef(onDiscError);
  onDiscErrorRef.current = onDiscError;
  // Read current disc states inside effects / loops without depending on them
  // (the background download effect shouldn't re-run every progress tick).
  const discStatesRef = useRef(discStates);
  discStatesRef.current = discStates;

  const isMultiDisc =
    !!game && game.discCount > 1 && !!game.discs && game.discs.length > 1;

  // Initialize disc states when game data is available
  useEffect(() => {
    if (!game?.discs || !isMultiDisc) {
      setDiscStates([]);
      return;
    }

    setDiscStates(
      game.discs.map((disc) => ({
        discNumber: disc.discNumber,
        fileName: disc.fileName,
        status: "pending",
        progress: 0,
        data: null,
      })),
    );
  }, [game?.id, isMultiDisc]);

  // AbortController for the in-flight disc download so an unmount cancels
  // the fetch + stream reader instead of letting them keep consuming
  // network and calling setState on an unmounted component.
  const abortControllerRef = useRef<AbortController | null>(null);

  // Start background downloading when game starts playing
  useEffect(() => {
    if (!isMultiDisc || emulatorStatus !== "playing" || !game?.discs) return;
    if (downloadingRef.current) return;

    const controller = new AbortController();
    abortControllerRef.current = controller;
    downloadingRef.current = true;
    downloadDiscsSequentially(game.id, game.discs.length, controller.signal);

    return () => {
      downloadingRef.current = false;
      controller.abort();
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null;
      }
    };
  }, [isMultiDisc, emulatorStatus, game?.id]);

  async function downloadSingleDisc(
    gameId: string,
    discNumber: number,
    signal?: AbortSignal,
  ) {
    setDiscStates((prev) =>
      prev.map((d) =>
        d.discNumber === discNumber
          ? { ...d, status: "downloading", progress: 0 }
          : d,
      ),
    );

    try {
      const token = api.getAccessToken();
      const tokenParam = token
        ? `&token=${encodeURIComponent(token)}`
        : "";
      const url = `/api/games/${gameId}/discs/${discNumber}/download?format=zip${tokenParam}`;

      const response = await fetch(url, { signal });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const contentLength = response.headers.get("content-length");
      const total = contentLength ? parseInt(contentLength, 10) : 0;

      if (!response.body || total === 0) {
        const data = await response.arrayBuffer();
        setDiscStates((prev) =>
          prev.map((d) =>
            d.discNumber === discNumber
              ? { ...d, status: "ready", progress: 1, data }
              : d,
          ),
        );
        return;
      }

      // Stream with progress
      const reader = response.body.getReader();
      const chunks: Uint8Array[] = [];
      let received = 0;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        received += value.length;
        const progress = total > 0 ? received / total : 0;
        setDiscStates((prev) =>
          prev.map((d) =>
            d.discNumber === discNumber ? { ...d, progress } : d,
          ),
        );
      }

      const combined = new Uint8Array(received);
      let offset = 0;
      for (const chunk of chunks) {
        combined.set(chunk, offset);
        offset += chunk.length;
      }

      setDiscStates((prev) =>
        prev.map((d) =>
          d.discNumber === discNumber
            ? { ...d, status: "ready", progress: 1, data: combined.buffer }
            : d,
        ),
      );
    } catch (err) {
      // AbortError is expected on unmount — don't surface as a download error.
      if (
        err instanceof DOMException &&
        err.name === "AbortError"
      ) {
        return;
      }
      const message =
        err instanceof Error ? err.message : "Download failed";
      setDiscStates((prev) =>
        prev.map((d) =>
          d.discNumber === discNumber ? { ...d, status: "error" } : d,
        ),
      );
      onDiscErrorRef.current?.(discNumber, message);
    }
  }

  async function downloadDiscsSequentially(
    gameId: string,
    discCount: number,
    signal?: AbortSignal,
  ) {
    for (let i = 1; i <= discCount; i++) {
      if (!downloadingRef.current) break;
      if (signal?.aborted) break;
      // Don't re-download a disc that already finished during a
      // previous pass. `emulatorStatus` flips through "saving" →
      // "playing" during a disc switch (requestSaveState), which
      // restarts this effect; without this guard we'd race the
      // already-complete first disc back to "downloading" mid-
      // switch and `buildMultiDiscBundle` would briefly return
      // null even though we already hold the bytes.
      const current = discStatesRef.current.find(
        (d) => d.discNumber === i,
      );
      if (current?.status === "ready" && current.data) continue;
      await downloadSingleDisc(gameId, i, signal);
    }
  }

  const retryDisc = useCallback(
    (discNumber: number) => {
      if (!game) return;
      // Pass the same abort signal the background loop uses so a
      // user-initiated retry that's still mid-flight when the
      // component unmounts gets cancelled too.
      downloadSingleDisc(game.id, discNumber, abortControllerRef.current?.signal);
    },
    [game?.id],
  );

  const allDiscsReady =
    isMultiDisc &&
    discStates.length > 0 &&
    discStates.every((d) => d.status === "ready");

  const buildMultiDiscBundle = useCallback((): ArrayBuffer | null => {
    if (!game?.discs || !isMultiDisc) return null;

    // All discs must be ready
    if (!discStates.every((d) => d.status === "ready" && d.data)) return null;

    try {
      const allFiles = new Map<string, Uint8Array>();

      // Extract files from each disc zip and add to combined map
      for (const disc of discStates) {
        const files = extractZipStore(disc.data!);
        for (const [name, data] of files) {
          allFiles.set(name, data);
        }
      }

      // Generate .m3u playlist content
      const m3uLines: string[] = [];
      for (const disc of game.discs) {
        m3uLines.push(disc.fileName);
      }
      const m3uContent = m3uLines.join("\n") + "\n";
      const m3uBytes = new TextEncoder().encode(m3uContent);

      // Use game title as .m3u filename
      const m3uName =
        game.title.replace(/[^a-zA-Z0-9 ._-]/g, "") + ".m3u";
      allFiles.set(m3uName, m3uBytes);

      return createZipStore(allFiles);
    } catch {
      return null;
    }
  }, [game, isMultiDisc, discStates]);

  return {
    discStates,
    isMultiDisc,
    allDiscsReady,
    buildMultiDiscBundle,
    retryDisc,
  };
}
