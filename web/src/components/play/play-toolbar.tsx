import { ArrowLeft, Save, FolderOpen, Maximize, Loader2 } from "lucide-react";
import { Button } from "@/components/ui";
import type { EmulatorStatus } from "@/hooks/use-emulator-iframe";
import type { Game } from "@/types/api";

interface PlayToolbarProps {
  game: Game;
  emulatorStatus: EmulatorStatus;
  isSaving: boolean;
  isExitSaving: boolean;
  onBack: () => void;
  onSave: () => void;
  onLoad: () => void;
  onFullscreen: () => void;
}

export function PlayToolbar({
  game,
  emulatorStatus,
  isSaving,
  isExitSaving,
  onBack,
  onSave,
  onLoad,
  onFullscreen,
}: PlayToolbarProps) {
  return (
    <div className="flex items-center justify-between px-4 py-2 border-b border-surface-800 bg-surface-950/80">
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
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
        {(isSaving || isExitSaving) && (
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
          onClick={onSave}
          disabled={emulatorStatus !== "playing"}
          title="Save State"
          aria-label="Save State"
        >
          <Save className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={onLoad}
          disabled={emulatorStatus !== "playing"}
          title="Load State"
          aria-label="Load State"
        >
          <FolderOpen className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={onFullscreen}
          disabled={emulatorStatus === "loading"}
          title="Fullscreen (F11)"
          aria-label="Fullscreen"
        >
          <Maximize className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
