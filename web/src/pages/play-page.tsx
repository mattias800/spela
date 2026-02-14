import { useParams, useNavigate } from "react-router-dom";
import { useState, useEffect, useRef } from "react";
import { AlertTriangle } from "lucide-react";
import { Button, Skeleton } from "@/components/ui";
import { useGame, useGameSaves } from "@/hooks/use-games";
import { useUserPreferences } from "@/hooks/use-preferences";
import { useConsoles } from "@/hooks/use-consoles";
import { useEmulatorIframe } from "@/hooks/use-emulator-iframe";
import { useEmulatorSaves } from "@/hooks/use-emulator-saves";
import { usePlaySession } from "@/hooks/use-play-session";
import { useFullscreen } from "@/hooks/use-fullscreen";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api-client";
import { toEmulatorJsShader } from "@/lib/shader-mapping";
import { PlayToolbar } from "@/components/play/play-toolbar";
import { EmulatorOverlay } from "@/components/play/emulator-overlay";
import { LoadSaveModal } from "@/components/play/load-save-modal";
import type { SaveState } from "@/types/api";
import type { EmulatorPreferences } from "@/lib/emulator-protocol";

export function PlayPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();

  const { data: game, isLoading: gameLoading } = useGame(id ?? "");
  const { data: saves } = useGameSaves(id ?? "");
  const { data: preferences } = useUserPreferences();
  const { data: consoles } = useConsoles();

  const [showLoadModal, setShowLoadModal] = useState(false);
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const [isExitSaving, setIsExitSaving] = useState(false);
  const sessionStartRef = useRef<number>(Date.now());

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
      saveManager.handleSaveStateData(data, screenshot);
    },
    onSaveStateError: (err) => {
      toast("error", `Save failed: ${err}`);
    },
    onError: (err) => {
      toast("error", `Emulator error: ${err}`);
    },
    onSramUpdate: (data) => {
      saveManager.enqueueSave(data, true, "sram_autosave");
    },
  });

  const saveManager = useEmulatorSaves({
    gameId: id,
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

  usePlaySession(id, emulator.status);
  const { handleFullscreen } = useFullscreen(emulator.iframeRef);

  // Initialize emulator once iframe is loaded and we have game data
  useEffect(() => {
    if (!iframeLoaded || !game || !isSupported || !emulatorJsCore) return;

    async function init() {
      const token = api.getAccessToken();
      const romUrl = `/api/games/${game!.id}/download${token ? `?token=${encodeURIComponent(token)}` : ""}`;
      const saveStateData = await saveManager.loadInitialSave();

      emulator.initEmulator({
        romUrl,
        core: emulatorJsCore!,
        gameName: game!.title,
        saveStateData,
        preferences: emulatorPrefs,
      });
    }

    init();
  }, [iframeLoaded, game?.id, isSupported, emulatorJsCore]);

  function handleLoadSave(save: SaveState) {
    setShowLoadModal(false);
    saveManager
      .loadSave(save)
      .then((data) => {
        if (data) {
          emulator.loadSaveState(data, save.name);
          toast("success", `Loaded: ${save.name}`);
        } else {
          toast("error", "Failed to load save state");
        }
      })
      .catch(() => {
        toast("error", "Failed to load save state");
      });
  }

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
    navigate(-1);
  }

  // ── Loading state ─────────────────────────────────────────────────

  if (gameLoading) {
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

  // ── Main emulator layout ──────────────────────────────────────────

  return (
    <div className="flex flex-col h-screen">
      <PlayToolbar
        game={game}
        emulatorStatus={emulator.status}
        isSaving={saveManager.isSaving}
        isExitSaving={isExitSaving}
        onBack={handleBack}
        onSave={() => saveManager.requestManualSave()}
        onLoad={() => setShowLoadModal(true)}
        onFullscreen={handleFullscreen}
      />

      <div className="flex-1 relative bg-black">
        <EmulatorOverlay
          status={emulator.status}
          error={emulator.error}
          romProgress={emulator.romProgress}
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

      <LoadSaveModal
        saves={saves}
        open={showLoadModal}
        onClose={() => setShowLoadModal(false)}
        onLoad={handleLoadSave}
      />
    </div>
  );
}
