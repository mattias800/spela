import { useParams, useNavigate } from "react-router-dom";
import { useState, useEffect, useRef, useCallback } from "react";
import { AlertTriangle } from "lucide-react";
import { Button, Skeleton } from "@/components/ui";
import { useGame } from "@/hooks/use-games";
import { useUserPreferences } from "@/hooks/use-preferences";
import { useConsoles } from "@/hooks/use-consoles";
import { useBiosStatus, getBiosFileUrl } from "@/hooks/use-bios";
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
import { api } from "@/lib/api-client";
import { toEmulatorJsShader } from "@/lib/shader-mapping";
import { PlayToolbar } from "@/features/play/components/play-toolbar";
import { EmulatorOverlay } from "@/features/play/components/emulator-overlay";
import {
  CoreMismatchModal,
  type CoreMismatchChoice,
} from "@/features/play/components/core-mismatch-modal";
import type { EmulatorPreferences } from "@/lib/emulator-protocol";
import type { GameSession } from "@/types/api";

export function PlayPage() {
  const { id, sessionId: sessionIdParam } = useParams<{ id: string; sessionId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const isFreshStart = sessionIdParam === "new";
  const [createdSessionId, setCreatedSessionId] = useState<string | undefined>();
  const sessionId = sessionIdParam === "new" ? createdSessionId : sessionIdParam;

  // Auto-create session when sessionId is "new"
  const creatingRef = useRef(false);
  useEffect(() => {
    if (sessionIdParam !== "new" || creatingRef.current || createdSessionId) return;
    creatingRef.current = true;
    api
      .post<GameSession>(`/games/${id}/sessions`, { name: "Default" })
      .then((session) => setCreatedSessionId(session.id))
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
  const [isSwitchingDisc, setIsSwitchingDisc] = useState(false);
  const [currentDisc, setCurrentDisc] = useState(1);
  const [showCoreMismatchModal, setShowCoreMismatchModal] = useState(false);
  const coreMismatchChoiceRef = useRef<CoreMismatchChoice | null>(null);
  const sessionStartRef = useRef<number>(Date.now());
  const pendingDiscSwitchRef = useRef<{
    targetDisc: number; // 0-indexed for EmulatorJS
    saveData: string;
  } | null>(null);
  const buildMultiDiscBundleRef = useRef<() => ArrayBuffer | null>(() => null);

  // Resolve the EmulatorJS core identifier for this game's console
  const consoleInfo = consoles?.find((c) => c.id === game?.consoleId);
  const emulatorJsCore = consoleInfo?.emulatorJsCore || null;
  const isSupported = !!emulatorJsCore;

  // Resolve shader: per-console override -> global default -> none, then map to EmulatorJS name
  const spelaShader = preferences
    ? preferences.consoleShaders[game?.consoleId ?? ""] ||
      preferences.selectedShader ||
      "none"
    : "none";
  const resolvedShader = toEmulatorJsShader(spelaShader) || spelaShader;

  // Resolve key mapping: per-console override -> global default -> arrows-left
  const consoleKeyMapping =
    preferences?.consoleKeyMappings[game?.consoleId ?? ""];
  const resolvedKeyMapping =
    consoleKeyMapping?.selectedMapping ??
    preferences?.selectedKeyMapping ??
    "arrows-left";
  const resolvedCustomMapping =
    resolvedKeyMapping === "custom"
      ? (consoleKeyMapping?.customMapping ??
        preferences?.customKeyMapping ??
        {})
      : undefined;

  const emulatorPrefs: EmulatorPreferences = {
    shader: resolvedShader,
    showPerformanceOverlay: preferences?.showPerformanceOverlay ?? false,
    keyMapping: resolvedKeyMapping,
    customKeyMapping: resolvedCustomMapping,
  };

  const emulator = useEmulatorIframe({
    onGameStarted: () => {
      sessionStartRef.current = Date.now();
    },
    onSaveStateResponse: (data, screenshot) => {
      // Check if this save state is part of a disc switch flow
      if (pendingDiscSwitchRef.current) {
        pendingDiscSwitchRef.current.saveData = data;

        // Reload iframe — the init useEffect will detect the pending
        // disc switch and build the combined bundle there
        setIframeLoaded(false);
        const iframe = emulator.iframeRef.current;
        if (iframe) {
          iframe.src = "/emulator.html";
        }
        return;
      }

      saveManager.handleSaveStateData(data, screenshot);
    },
    onSaveStateError: (err) => {
      if (pendingDiscSwitchRef.current) {
        pendingDiscSwitchRef.current = null;
        setIsSwitchingDisc(false);
        toast("error", `Disc switch failed: ${err}`);
        return;
      }
      toast("error", `Save failed: ${err}`);
    },
    onError: (err) => {
      toast("error", `Emulator error: ${err}`);
      api.post("/emulator/error", {
        error: err,
        gameId: id ?? "",
        core: emulatorJsCore ?? "",
      }).catch(() => {});
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
  buildMultiDiscBundleRef.current = discManager.buildMultiDiscBundle;

  usePlaySession(id, emulator.status);
  const { handleFullscreen } = useFullscreen(emulator.iframeRef);
  const gamepadConnected = useGamepadConnected();

  const handleDiscSwitch = useCallback(
    (targetDiscNumber: number) => {
      if (isSwitchingDisc || !discManager.allDiscsReady) return;
      setIsSwitchingDisc(true);

      // Store target disc (0-indexed for EmulatorJS) — currentDisc is
      // updated after the switch succeeds in the init useEffect
      pendingDiscSwitchRef.current = {
        targetDisc: targetDiscNumber - 1,
        saveData: "",
      };

      // Pause and request save state — the flow continues in onSaveStateResponse
      emulator.pause();
      emulator.requestSaveState();
    },
    [isSwitchingDisc, discManager.allDiscsReady, emulator],
  );

  // Initialize emulator once iframe is loaded and we have game data
  useEffect(() => {
    if (!iframeLoaded || !game || !isSupported || !emulatorJsCore) return;
    // Wait for auto-save info to settle before initializing (avoids double-init)
    if (!isFreshStart && autoSaveInfoLoading) return;

    // Disc switch flow: reload with combined bundle + save state + target disc
    if (pendingDiscSwitchRef.current && pendingDiscSwitchRef.current.saveData) {
      const { targetDisc, saveData } = pendingDiscSwitchRef.current;
      const bundle = buildMultiDiscBundleRef.current();
      if (!bundle) {
        toast("error", "Failed to build multi-disc bundle");
        pendingDiscSwitchRef.current = null;
        setIsSwitchingDisc(false);
        return;
      }

      const token = api.getAccessToken();
      const tokenSuffix = token
        ? `?token=${encodeURIComponent(token)}`
        : "";
      // Filter BIOS files for the current console only
      const consoleBiosFiles = biosConsole?.files
        ?.filter((f) => f.status !== "missing")
        .map((f) => f.fileName) ?? [];
      const biosUrls =
        consoleBiosFiles.length > 0
          ? consoleBiosFiles.map((f) => getBiosFileUrl(f) + tokenSuffix)
          : undefined;

      // Update currentDisc now that the switch is committed
      setCurrentDisc(targetDisc + 1); // convert back to 1-indexed
      pendingDiscSwitchRef.current = null;

      emulator.initEmulator({
        romUrl: "", // unused when romData is provided
        romData: bundle,
        targetDisc,
        core: emulatorJsCore!,
        gameName: game!.title,
        saveStateData: saveData,
        biosUrls,
        preferences: emulatorPrefs,
      });

      setIsSwitchingDisc(false);
      return;
    }

    // Normal initialization flow
    async function init() {
      // Check for core mismatch before loading the auto-save
      const currentAutoSave = autoSaveInfoRef.current;
      if (
        !isFreshStart &&
        currentAutoSave &&
        currentAutoSave.coreMatch === false &&
        !coreMismatchChoiceRef.current
      ) {
        setShowCoreMismatchModal(true);
        return; // Wait for user choice — will re-enter after choice is made
      }

      const token = api.getAccessToken();
      const tokenSuffix = token
        ? `?token=${encodeURIComponent(token)}`
        : "";
      // Multi-disc games (e.g. PS1 .m3u): load disc 1 via the disc endpoint.
      // The main /download endpoint serves the .m3u playlist file which
      // EmulatorJS can't use (it needs the actual disc image).
      // For multi-file disc formats (cue+bin), request format=zip since
      // EmulatorJS can extract zip but not tar.
      const isMultiDisc = game!.discCount > 0 && game!.discs && game!.discs.length > 0;
      // Include the filename in the URL path so EmulatorJS can detect the
      // file extension (e.g. .gg for Game Gear). Without it, genesis_plus_gx
      // can't distinguish Game Gear from Genesis ROMs.
      const romUrl = isMultiDisc
        ? `/api/games/${game!.id}/discs/1/download?format=zip${token ? `&token=${encodeURIComponent(token)}` : ""}`
        : `/api/games/${game!.id}/download/${encodeURIComponent(game!.fileName)}${tokenSuffix}`;

      // Determine whether to load the save state based on user choice
      const choice = coreMismatchChoiceRef.current;
      const skipSaveState = choice === "fresh" || choice === "sram-only";
      const saveStateData = await saveManager.loadInitialSave(
        isFreshStart || skipSaveState,
      );

      // Build authenticated BIOS file URLs (filtered for current console)
      const consoleBiosFiles2 = biosConsole?.files
        ?.filter((f) => f.status !== "missing")
        .map((f) => f.fileName) ?? [];
      const biosUrls =
        consoleBiosFiles2.length > 0
          ? consoleBiosFiles2.map((f) => getBiosFileUrl(f) + tokenSuffix)
          : undefined;

      emulator.initEmulator({
        romUrl,
        core: emulatorJsCore!,
        gameName: game!.title,
        saveStateData,
        biosUrls,
        preferences: emulatorPrefs,
      });
    }

    init();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [iframeLoaded, game?.id, isSupported, emulatorJsCore, autoSaveInfoLoading]);

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
      const iframe = emulator.iframeRef.current;
      if (iframe) {
        iframe.src = "/emulator.html";
      }
    },
    [emulator.iframeRef],
  );

  // ── Loading state ─────────────────────────────────────────────────

  if (gameLoading || consolesLoading) {
    return (
      <div className="flex flex-col h-screen">
        <div className="flex items-center justify-between px-4 py-2 border-b border-surface-800 bg-surface-950/80">
          <div className="flex items-center gap-3">
            <Skeleton className="h-5 w-16" />
            <Skeleton className="h-5 w-40" />
          </div>
          <div className="flex items-center gap-2">
            <Skeleton className="h-8 w-8 rounded" />
            <Skeleton className="h-8 w-8 rounded" />
            <Skeleton className="h-8 w-8 rounded" />
          </div>
        </div>
        <div className="flex-1 bg-surface-950" />
      </div>
    );
  }

  if (!game) {
    return (
      <div className="text-center py-20">
        <p className="text-surface-400">Game not found</p>
        <Button variant="ghost" onClick={() => navigate(-1)} className="mt-4">
          Go back
        </Button>
      </div>
    );
  }

  if (!isSupported) {
    return (
      <div className="text-center py-20 space-y-4">
        <AlertTriangle className="h-12 w-12 text-warning-500 mx-auto" />
        <h2 className="text-xl font-bold text-surface-100">
          Browser Play Not Available
        </h2>
        <p className="text-surface-400 max-w-md mx-auto">
          {game.consoleName} is not yet supported for browser play. Use the
          Spela player app to play this game.
        </p>
        <Button variant="secondary" onClick={() => navigate(`/games/${id}`)}>
          Back to Game Details
        </Button>
      </div>
    );
  }

  // ── Error state ──────────────────────────────────────────────────

  if (emulator.status === "error") {
    return (
      <div className="text-center py-20 space-y-4">
        <AlertTriangle className="h-12 w-12 text-danger-500 mx-auto" />
        <h2 className="text-xl font-bold text-surface-100">
          Emulation Error
        </h2>
        <p className="text-surface-400 max-w-md mx-auto">
          {emulator.error || "The emulator encountered an unexpected error."}
        </p>
        <div className="flex items-center justify-center gap-3 pt-2">
          <Button
            variant="secondary"
            onClick={() => navigate(`/games/${id}`)}
          >
            Back to Game Details
          </Button>
          <Button onClick={() => window.location.reload()}>
            Try Again
          </Button>
        </div>
      </div>
    );
  }

  // ── Main emulator layout ──────────────────────────────────────────

  return (
    <div className="flex flex-col h-screen">
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
        currentDisc={discManager.isMultiDisc ? currentDisc : undefined}
        isSwitchingDisc={isSwitchingDisc}
        onSwitchDisc={discManager.isMultiDisc ? handleDiscSwitch : undefined}
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
          isSwitchingDisc={isSwitchingDisc}
          switchingToDisc={pendingDiscSwitchRef.current ? pendingDiscSwitchRef.current.targetDisc + 1 : currentDisc}
          onRetry={() => {
            setIframeLoaded(false);
            const iframe = emulator.iframeRef.current;
            if (iframe) {
              iframe.src = "/emulator.html";
            }
          }}
          onBack={() => navigate(`/games/${id}`)}
        />

        <iframe
          ref={emulator.iframeRef}
          src="/emulator.html"
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
