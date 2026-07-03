import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { useState, useEffect, useRef, useCallback } from "react";
import {
  PlayPageError,
  PlayPageLoading,
  PlayPageNotFound,
  PlayPageUnsupported,
} from "@/features/play/components/play-page-state";
import { useGame } from "@/hooks/use-games";
import { useUserPreferences } from "@/hooks/use-preferences";
import { useConsoles } from "@/hooks/use-consoles";
import { useBiosStatus } from "@/hooks/use-bios";
import { useAuth } from "@/hooks/use-auth";
import { useEmulatorIframe } from "@/hooks/use-emulator-iframe";
import { useEmulatorSaves } from "@/hooks/use-emulator-saves";
import { useDiscManager } from "@/hooks/use-disc-manager";
import { usePlaySession } from "@/hooks/use-play-session";
import { useFullscreen } from "@/hooks/use-fullscreen";
import { useToast } from "@/components/ui";
import { useGamepadConnected } from "@/hooks/use-gamepad";
import { useAutoSaveInfo } from "@/hooks/use-sessions";
import { useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useDiscSwitchFlow } from "@/features/play/hooks/use-disc-switch-flow";
import { useEmulatorInit } from "@/features/play/hooks/use-emulator-init";
import { useResolvedGamePreferences } from "@/features/play/hooks/use-resolved-game-preferences";
import { PlayToolbar } from "@/features/play/components/play-toolbar";
import { EmulatorOverlay } from "@/features/play/components/emulator-overlay";
import {
  CoreMismatchModal,
  type CoreMismatchChoice,
} from "@/features/play/components/core-mismatch-modal";
/**
 * Iframe URL — `?mockEmulator=true` on the play page swaps the real
 * EmulatorJS iframe for `/emulator-mock.html`, a deterministic stub
 * used by Playwright tests. Production builds never set the flag,
 * but the mock html ships in `public/` so tests can opt in without
 * a separate build.
 */
function resolveEmulatorSrc(useMock: boolean): string {
  return useMock ? "/emulator-mock.html" : "/emulator.html";
}

export function PlayPage() {
  const { id, sessionId: sessionIdParam } = useParams<{ id: string; sessionId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [searchParams] = useSearchParams();
  const useMockEmulator = searchParams.get("mockEmulator") === "true";
  const emulatorSrc = resolveEmulatorSrc(useMockEmulator);

  const isFreshStart = sessionIdParam === "new";
  const [createdSessionId, setCreatedSessionId] = useState<string | undefined>();
  const sessionId = sessionIdParam === "new" ? createdSessionId : sessionIdParam;

  // Auto-create session when sessionId is "new"
  const creatingRef = useRef(false);
  useEffect(() => {
    if (sessionIdParam !== "new" || creatingRef.current || createdSessionId) return;
    creatingRef.current = true;
    unwrap(
      typedApi.POST("/api/games/{id}/sessions", {
        params: { path: { id: id ?? "" } },
        body: { name: "Default" },
      }),
    )
      .then((session) => {
        if (session) setCreatedSessionId(session.id);
      })
      .catch(() => {})
      .finally(() => { creatingRef.current = false; });
  }, [sessionIdParam, id, createdSessionId]);

  const { data: game, isLoading: gameLoading } = useGame(id ?? "");
  const { data: preferences } = useUserPreferences();
  const { data: consoles, isLoading: consolesLoading } = useConsoles();
  const { data: biosData } = useBiosStatus();
  const { isAdmin } = useAuth();

  const biosConsole = biosData?.consoles?.find(
    (c) => c.consoleId === game?.consoleId,
  );
  const biosMissing =
    biosConsole?.status === "missing" && biosConsole.biosRequired;
  const missingBiosFiles =
    biosConsole?.files
      ?.filter((f) => f.status === "missing" && f.required)
      .map((f) => f.fileName) ?? [];

  const { data: autoSaveInfo, isLoading: autoSaveInfoLoading } = useAutoSaveInfo(
    !isFreshStart ? sessionId : undefined,
  );
  const autoSaveInfoRef = useRef(autoSaveInfo);
  autoSaveInfoRef.current = autoSaveInfo;

  const [iframeLoaded, setIframeLoaded] = useState(false);
  const [isExitSaving, setIsExitSaving] = useState(false);
  const [showCoreMismatchModal, setShowCoreMismatchModal] = useState(false);
  const coreMismatchChoiceRef = useRef<CoreMismatchChoice | null>(null);
  const [initialSessionStart] = useState(() => Date.now());
  const sessionStartRef = useRef<number>(initialSessionStart);

  // Resolve the EmulatorJS core identifier for this game's console
  const consoleInfo = consoles?.find((c) => c.id === game?.consoleId);
  const emulatorJsCore = consoleInfo?.emulatorJsCore || null;
  const isSupported = !!emulatorJsCore;

  const emulatorPrefs = useResolvedGamePreferences({
    preferences,
    consoleId: game?.consoleId,
  });

  const emulator = useEmulatorIframe({
    onGameStarted: () => {
      sessionStartRef.current = Date.now();
    },
    onSaveStateResponse: (data, screenshot) => {
      if (discSwitch.handleSaveStateResponse(data)) return;
      saveManager.handleSaveStateData(data, screenshot);
    },
    onSaveStateError: (err) => {
      if (discSwitch.handleSaveStateError(err)) return;
      toast("error", `Save failed: ${err}`);
    },
    onError: (err) => {
      toast("error", `Emulator error: ${err}`);
      unwrap(
        typedApi.POST("/api/emulator/error", {
          body: {
            error: err,
            gameId: id ?? "",
            core: emulatorJsCore ?? "",
          },
        }),
      ).catch(() => {});
    },
    onSramUpdate: (data) => {
      saveManager.enqueueSave(data, true, "sram_autosave");
    },
  });

  const saveManager = useEmulatorSaves({
    sessionId,
    emulatorStatus: emulator.status,
    preferences,
    onSaveSuccess: (isAuto) => {
      if (!isAuto) toast("success", "State saved successfully");
    },
    onSaveError: (error, isAuto) => {
      if (!isAuto) toast("error", error);
    },
    requestSaveState: emulator.requestSaveState,
  });

  const discManager = useDiscManager({
    game,
    emulatorStatus: emulator.status,
    onDiscError: (discNumber, error) => {
      toast("error", `Disc ${discNumber} download failed: ${error}`);
    },
  });

  const discSwitch = useDiscSwitchFlow({
    emulator,
    allDiscsReady: discManager.allDiscsReady,
    buildMultiDiscBundle: discManager.buildMultiDiscBundle,
    emulatorSrc,
    onToastError: (msg) => toast("error", msg),
    onIframeWillReload: () => setIframeLoaded(false),
  });

  usePlaySession(id, emulator.status);
  const { handleFullscreen } = useFullscreen(emulator.iframeRef);
  const gamepadConnected = useGamepadConnected();

  useEmulatorInit({
    iframeLoaded,
    gameId: game?.id,
    isSupported,
    emulatorJsCore,
    autoSaveInfoLoading,
    game,
    isFreshStart,
    biosConsole,
    emulatorPrefs,
    autoSaveInfoRef,
    coreMismatchChoiceRef,
    pendingDiscSwitchRef: discSwitch.pendingDiscSwitchRef,
    buildMultiDiscBundleRef: discSwitch.buildMultiDiscBundleRef,
    emulator,
    loadInitialSave: saveManager.loadInitialSave,
    onToastError: (message) => toast("error", message),
    onRequestCoreMismatchChoice: () => setShowCoreMismatchModal(true),
    onDiscSwitchCurrentChanged: discSwitch.onDiscSwitchCurrentChanged,
    onDiscSwitchSettled: discSwitch.onDiscSwitchSettled,
  });

  async function handleBack() {
    if (isExitSaving) return;
    if (emulator.status === "playing" && preferences?.autoSaveEnabled) {
      setIsExitSaving(true);
      const EXIT_SAVE_TIMEOUT_MS = 3_000;
      await Promise.race([
        saveManager.requestExitSave(),
        new Promise<void>((resolve) =>
          setTimeout(resolve, EXIT_SAVE_TIMEOUT_MS),
        ),
      ]);
    }
    queryClient.invalidateQueries({ queryKey: ["game-sessions"] });
    navigate(-1);
  }

  const handleCoreMismatchChoice = useCallback(
    (choice: CoreMismatchChoice) => {
      coreMismatchChoiceRef.current = choice;
      setShowCoreMismatchModal(false);
      // Re-trigger init by toggling iframeLoaded — the init useEffect will
      // re-run and use the stored choice.
      setIframeLoaded(false);
      emulator.reloadEmulator(emulatorSrc);
    },
    [emulator, emulatorSrc],
  );

  // ── Loading / terminal states ─────────────────────────────────────
  // Each full-screen placeholder lives in `features/play/components`
  // so the main page body below can focus on the happy-path layout.

  if (gameLoading || consolesLoading) return <PlayPageLoading />;
  if (!game) return <PlayPageNotFound onBack={() => navigate(-1)} />;
  if (!isSupported) {
    return (
      <PlayPageUnsupported
        consoleName={game.consoleName}
        onBack={() => navigate(`/games/${id}`)}
      />
    );
  }
  if (emulator.status === "error") {
    return (
      <PlayPageError
        error={emulator.error}
        onBack={() => navigate(`/games/${id}`)}
        onRetry={() => window.location.reload()}
      />
    );
  }

  // ── Main emulator layout ──────────────────────────────────────────

  return (
    <div data-comp="PlayPage" className="flex flex-col h-screen">
      <CoreMismatchModal
        open={showCoreMismatchModal}
        onChoice={handleCoreMismatchChoice}
      />

      <PlayToolbar
        game={game}
        emulatorStatus={emulator.status}
        isSaving={saveManager.isSaving}
        isExitSaving={isExitSaving}
        gamepadConnected={gamepadConnected}
        onBack={handleBack}
        onSave={() => {
          saveManager.requestManualSave();
          emulator.focusEmulator();
        }}
        onFullscreen={() => {
          handleFullscreen();
          emulator.focusEmulator();
        }}
        discStates={discManager.isMultiDisc ? discManager.discStates : undefined}
        currentDisc={discManager.isMultiDisc ? discSwitch.currentDisc : undefined}
        isSwitchingDisc={discSwitch.isSwitchingDisc}
        onSwitchDisc={
          discManager.isMultiDisc ? discSwitch.handleDiscSwitch : undefined
        }
        onRetryDisc={discManager.isMultiDisc ? discManager.retryDisc : undefined}
      />

      <div className="flex-1 relative bg-black">
        <EmulatorOverlay
          status={emulator.status}
          error={emulator.error}
          romProgress={emulator.romProgress}
          biosMissing={biosMissing}
          missingBiosFiles={missingBiosFiles}
          isAdmin={isAdmin}
          isSwitchingDisc={discSwitch.isSwitchingDisc}
          switchingToDisc={
            discSwitch.pendingDiscSwitchRef.current
              ? discSwitch.pendingDiscSwitchRef.current.targetDisc + 1
              : discSwitch.currentDisc
          }
          onRetry={() => {
            setIframeLoaded(false);
            emulator.reloadEmulator(emulatorSrc);
          }}
          onBack={() => navigate(`/games/${id}`)}
        />

        <iframe
          ref={emulator.iframeRef}
          src={emulatorSrc}
          title={`Playing ${game.title}`}
          className="w-full h-full border-0"
          allow="autoplay; gamepad; fullscreen"
          onLoad={() => setIframeLoaded(true)}
        />
      </div>

    </div>
  );
}

export default PlayPage;
