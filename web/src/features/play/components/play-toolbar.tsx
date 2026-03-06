import { Save, Maximize, Loader2, Gamepad2 } from "lucide-react";
import { Button, BackButton } from "@/components/ui";
import { DiscSwitchButton } from "./disc-switch-button";
import type { EmulatorStatus } from "@/hooks/use-emulator-iframe";
import type { DiscState } from "@/hooks/use-disc-manager";
import type { Game } from "@/types/api";

interface PlayToolbarProps {
  game: Game;
  emulatorStatus: EmulatorStatus;
  isSaving: boolean;
  isExitSaving: boolean;
  gamepadConnected: boolean;
  onBack: () => void;
  onSave: () => void;
  onFullscreen: () => void;
  discStates?: DiscState[];
  currentDisc?: number;
  isSwitchingDisc?: boolean;
  onSwitchDisc?: (discNumber: number) => void;
  onRetryDisc?: (discNumber: number) => void;
}

export function PlayToolbar({
  game,
  emulatorStatus,
  isSaving,
  isExitSaving,
  gamepadConnected,
  onBack,
  onSave,
  onFullscreen,
  discStates,
  currentDisc,
  isSwitchingDisc,
  onSwitchDisc,
  onRetryDisc,
}: PlayToolbarProps) {
  return (
    <div className="flex items-center justify-between px-4 py-2 border-b border-surface-800 bg-surface-950/80">
      <div className="flex items-center gap-3">
        <BackButton
          onClick={onBack}
          data-testid="back-btn"
          disabled={isExitSaving}
        >
          {isExitSaving ? "Saving..." : "Back"}
        </BackButton>
        <span className="text-sm font-medium text-surface-200 truncate max-w-xs">
          {game.title}
        </span>
        {(isSaving || isExitSaving) && (
          <span className="flex items-center gap-1 text-xs text-brand-400">
            <Loader2 className="h-3 w-3 animate-spin" />
            Saving...
          </span>
        )}
        {discStates && currentDisc && onSwitchDisc && (
          <DiscSwitchButton
            game={game}
            discStates={discStates}
            currentDisc={currentDisc}
            isSwitching={isSwitchingDisc ?? false}
            onSwitchDisc={onSwitchDisc}
            onRetryDisc={onRetryDisc}
          />
        )}
      </div>

      <div className="flex items-center gap-2">
        {gamepadConnected && (
          <span
            className="flex items-center gap-1 text-xs text-success-400"
            title="Controller connected"
            data-testid="gamepad-indicator"
          >
            <Gamepad2 className="h-4 w-4" />
          </span>
        )}
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
