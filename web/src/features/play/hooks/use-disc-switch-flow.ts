import {
  useCallback,
  useRef,
  useState,
  type MutableRefObject,
  type RefObject,
} from "react";

interface EmulatorControls {
  pause: () => void;
  requestSaveState: () => void;
  iframeRef: RefObject<HTMLIFrameElement | null>;
}

interface UseDiscSwitchFlowArgs {
  emulator: EmulatorControls;
  /** Whether every disc in the game has finished downloading. */
  allDiscsReady: boolean;
  /**
   * Latest `buildMultiDiscBundle` from `useDiscManager`. The hook
   * stores a ref that `useEmulatorInit` reads when the pending
   * switch resolves, so the init effect always sees the newest
   * closure even though the useEffect's dep array stays narrow.
   */
  buildMultiDiscBundle: () => ArrayBuffer | null;
  /** Iframe URL used when the flow reloads the emulator frame. */
  emulatorSrc: string;
  onToastError: (message: string) => void;
  /**
   * Called right before the iframe is reloaded for a disc switch so
   * the page can reset its `iframeLoaded` gate. Without this the
   * init effect wouldn't fire again — Compose... er, React — sees
   * no state change and skips the re-render.
   */
  onIframeWillReload: () => void;
}

export interface UseDiscSwitchFlowResult {
  /** True while we're mid-way through a pause → save → reload sequence. */
  isSwitchingDisc: boolean;
  /** 1-indexed disc currently loaded in the emulator. */
  currentDisc: number;
  /**
   * Click handler for the disc-switch dropdown. No-ops if a switch
   * is already pending or the discs aren't fully downloaded yet.
   */
  handleDiscSwitch: (targetDiscNumber: number) => void;
  /**
   * Handle an incoming save-state response. Returns `true` if the
   * save belongs to a disc-switch (the hook swallowed it and is
   * reloading the iframe), `false` if the caller should treat it as
   * a regular save.
   */
  handleSaveStateResponse: (saveData: string) => boolean;
  /**
   * Handle an incoming save-state error. Returns `true` if the error
   * belongs to a disc-switch (the hook surfaced the failure toast
   * and cleaned up), `false` otherwise.
   */
  handleSaveStateError: (error: string) => boolean;
  /**
   * Refs / callbacks wired into `useEmulatorInit` so the init effect
   * can commit the switch when the new iframe comes up.
   */
  pendingDiscSwitchRef: MutableRefObject<{
    targetDisc: number;
    saveData: string;
  } | null>;
  buildMultiDiscBundleRef: MutableRefObject<() => ArrayBuffer | null>;
  onDiscSwitchCurrentChanged: (newCurrent: number) => void;
  onDiscSwitchSettled: () => void;
}

/**
 * Owns the multi-disc switch state machine. Extracted from
 * `play-page.tsx` in #694 / PR 2 — behind the Playwright test added
 * in PR 1. The flow:
 *
 *   1. User clicks switch. `handleDiscSwitch` sets
 *      `pendingDiscSwitchRef` + `isSwitchingDisc`, pauses the
 *      emulator, and asks for a save state.
 *   2. Emulator responds with save-state.
 *      `handleSaveStateResponse` stores the bytes on the ref and
 *      reloads the iframe (new `emulator.html` load).
 *   3. After the iframe reloads, `useEmulatorInit`'s effect sees the
 *      pending switch, calls `buildMultiDiscBundle`, and re-inits
 *      with `targetDisc` + `saveStateData`. It then calls
 *      `onDiscSwitchCurrentChanged` → `onDiscSwitchSettled` to
 *      finalise `currentDisc` and clear `isSwitchingDisc`.
 *   4. If the save-state request errors out (`handleSaveStateError`),
 *      we abort the flow immediately.
 */
export function useDiscSwitchFlow({
  emulator,
  allDiscsReady,
  buildMultiDiscBundle,
  emulatorSrc,
  onToastError,
  onIframeWillReload,
}: UseDiscSwitchFlowArgs): UseDiscSwitchFlowResult {
  const [isSwitchingDisc, setIsSwitchingDisc] = useState(false);
  const [currentDisc, setCurrentDisc] = useState(1);

  const pendingDiscSwitchRef = useRef<{
    targetDisc: number;
    saveData: string;
  } | null>(null);
  const buildMultiDiscBundleRef = useRef<() => ArrayBuffer | null>(
    () => null,
  );
  buildMultiDiscBundleRef.current = buildMultiDiscBundle;

  const handleDiscSwitch = useCallback(
    (targetDiscNumber: number) => {
      if (isSwitchingDisc || !allDiscsReady) return;
      setIsSwitchingDisc(true);

      // Target is 0-indexed for EmulatorJS; currentDisc is committed
      // later when the new iframe finishes re-init.
      pendingDiscSwitchRef.current = {
        targetDisc: targetDiscNumber - 1,
        saveData: "",
      };

      emulator.pause();
      emulator.requestSaveState();
    },
    [isSwitchingDisc, allDiscsReady, emulator],
  );

  const handleSaveStateResponse = useCallback(
    (saveData: string): boolean => {
      const pending = pendingDiscSwitchRef.current;
      if (!pending) return false;

      pending.saveData = saveData;

      // Reload the iframe; the init effect will detect the pending
      // switch and finish the flow there. `onIframeWillReload`
      // resets the parent's `iframeLoaded` gate so the subsequent
      // onLoad actually triggers a re-render.
      onIframeWillReload();
      const iframe = emulator.iframeRef.current;
      if (iframe) {
        iframe.src = emulatorSrc;
      }
      return true;
    },
    [emulator, emulatorSrc, onIframeWillReload],
  );

  const handleSaveStateError = useCallback(
    (error: string): boolean => {
      if (!pendingDiscSwitchRef.current) return false;
      pendingDiscSwitchRef.current = null;
      setIsSwitchingDisc(false);
      onToastError(`Disc switch failed: ${error}`);
      return true;
    },
    [onToastError],
  );

  const onDiscSwitchCurrentChanged = useCallback((n: number) => {
    setCurrentDisc(n);
  }, []);

  const onDiscSwitchSettled = useCallback(() => {
    setIsSwitchingDisc(false);
  }, []);

  return {
    isSwitchingDisc,
    currentDisc,
    handleDiscSwitch,
    handleSaveStateResponse,
    handleSaveStateError,
    pendingDiscSwitchRef,
    buildMultiDiscBundleRef,
    onDiscSwitchCurrentChanged,
    onDiscSwitchSettled,
  };
}
