import { useParams, useNavigate } from "react-router-dom";
import { useState, useEffect, useRef, useCallback } from "react";
import { ArrowLeft, Save, FolderOpen, Maximize, AlertTriangle, Loader2 } from "lucide-react";
import { Button, Modal, Skeleton, EmptyState } from "@/components/ui";
import { useGame, useGameSaves } from "@/hooks/use-games";
import { useUserPreferences } from "@/hooks/use-preferences";
import { useConsoles } from "@/hooks/use-consoles";
import { useEmulatorIframe } from "@/hooks/use-emulator-iframe";
import { useEmulatorSaves } from "@/hooks/use-emulator-saves";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api-client";
import { formatFileSize, formatRelativeTime } from "@/lib/format";
import { toEmulatorJsShader } from "@/lib/shader-mapping";
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

  // Resolve shader: per-console override → global default → none, then map to EmulatorJS name
  const spelaShader = preferences
    ? preferences.consoleShaders[game?.consoleId ?? ""] ||
      preferences.selectedShader ||
      "none"
    : "none";
  const resolvedShader = toEmulatorJsShader(spelaShader) || spelaShader;

  const emulatorPrefs: EmulatorPreferences = {
    shader: resolvedShader,
    showPerformanceOverlay: preferences?.showPerformanceOverlay ?? false,
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
      // Sync SRAM/battery save to server
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

  // Initialize emulator once iframe is loaded and we have game data
  useEffect(() => {
    if (!iframeLoaded || !game || !isSupported || !emulatorJsCore) return;

    async function init() {
      // Build authenticated ROM URL
      const token = api.getAccessToken();
      const romUrl = `/api/games/${game!.id}/download${token ? `?token=${encodeURIComponent(token)}` : ""}`;

      // Try to load auto-save if preference enabled
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

  // Periodic play time reporting (every 5 min) + flush on unmount
  const lastReportedRef = useRef<number>(Date.now());

  const flushPlayTime = useCallback(() => {
    if (!id) return;
    const now = Date.now();
    const seconds = Math.floor((now - lastReportedRef.current) / 1000);
    if (seconds > 5) {
      lastReportedRef.current = now;
      api.post(`/games/${id}/play-time`, { seconds }).catch(() => {});
    }
  }, [id]);

  useEffect(() => {
    if (emulator.status !== "playing") return;

    const interval = setInterval(flushPlayTime, 5 * 60 * 1000);
    return () => {
      clearInterval(interval);
      flushPlayTime();
    };
  }, [emulator.status, flushPlayTime]);

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

  function handleManualSave() {
    saveManager.requestManualSave();
  }

  async function handleBack() {
    if (isExitSaving) return;
    if (emulator.status === "playing" && preferences?.autoSaveEnabled) {
      setIsExitSaving(true);
      const EXIT_SAVE_TIMEOUT_MS = 3_000;
      await Promise.race([
        saveManager.requestExitSave(),
        new Promise<void>((resolve) => setTimeout(resolve, EXIT_SAVE_TIMEOUT_MS)),
      ]);
    }
    navigate(-1);
  }

  function handleFullscreen() {
    const iframe = emulator.iframeRef.current;
    if (iframe) {
      iframe.requestFullscreen?.().catch(() => {});
    }
  }

  // F11 keyboard shortcut for fullscreen
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "F11") {
        e.preventDefault();
        handleFullscreen();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

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
      {/* Top bar */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-surface-800 bg-surface-950/80">
        <div className="flex items-center gap-3">
          <button
            onClick={handleBack}
            data-testid="back-btn"
            className="flex items-center gap-1.5 text-sm text-surface-400 hover:text-surface-100 transition-colors"
            disabled={isExitSaving}
          >
            <ArrowLeft className="h-4 w-4" />
            {isExitSaving ? "Saving..." : "Back"}
          </button>
          <span className="text-sm font-medium text-surface-200 truncate max-w-xs">
            {game.title}
          </span>
          {(saveManager.isSaving || isExitSaving) && (
            <span className="flex items-center gap-1 text-xs text-brand-400">
              <Loader2 className="h-3 w-3 animate-spin" />
              Saving...
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleManualSave}
            disabled={emulator.status !== "playing"}
            title="Save State"
            aria-label="Save State"
          >
            <Save className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setShowLoadModal(true)}
            disabled={emulator.status !== "playing"}
            title="Load State"
            aria-label="Load State"
          >
            <FolderOpen className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleFullscreen}
            disabled={emulator.status === "loading"}
            title="Fullscreen (F11)"
            aria-label="Fullscreen"
          >
            <Maximize className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Emulator iframe */}
      <div className="flex-1 relative bg-black">
        {emulator.status === "loading" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center z-10 bg-surface-950 transition-opacity duration-300">
            <Loader2 className="h-8 w-8 animate-spin text-brand-500 mb-3" />
            <p className="text-sm text-surface-400">
              {emulator.romProgress
                ? `Loading ROM... ${formatFileSize(emulator.romProgress.loaded)} / ${formatFileSize(emulator.romProgress.total)}`
                : "Initializing emulator..."}
            </p>
          </div>
        )}

        {emulator.status === "error" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center z-10 bg-surface-950 transition-opacity duration-300">
            <AlertTriangle className="h-8 w-8 text-danger-500 mb-3" />
            <p className="text-sm text-surface-300 mb-4">
              {emulator.error ?? "An error occurred"}
            </p>
            <div className="flex items-center gap-3">
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  setIframeLoaded(false);
                  const iframe = emulator.iframeRef.current;
                  if (iframe) {
                    iframe.src = "/emulator.html";
                  }
                }}
              >
                Try Again
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate(`/games/${id}`)}
              >
                Back to Game
              </Button>
            </div>
          </div>
        )}

        <iframe
          ref={emulator.iframeRef}
          src="/emulator.html"
          title={`Playing ${game.title}`}
          className="w-full h-full border-0"
          allow="autoplay; gamepad; fullscreen"
          onLoad={() => setIframeLoaded(true)}
        />
      </div>

      {/* Load save state modal */}
      <Modal
        open={showLoadModal}
        onClose={() => setShowLoadModal(false)}
        title="Load Save State"
        size="md"
      >
        {!saves || saves.length === 0 ? (
          <EmptyState
            icon={FolderOpen}
            title="No save states available"
            description="Play the game and save your progress to see saves here."
          />
        ) : (
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {saves.map((save) => (
              <button
                key={save.id}
                onClick={() => handleLoadSave(save)}
                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-surface-800 transition-colors text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-100 truncate">
                    {save.name}
                    {save.isAuto && (
                      <span className="ml-2 text-xs text-brand-400">Auto</span>
                    )}
                  </p>
                  <p className="text-xs text-surface-500">
                    {formatRelativeTime(save.createdAt)} &middot;{" "}
                    {formatFileSize(save.fileSize)}
                  </p>
                </div>
              </button>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
}

