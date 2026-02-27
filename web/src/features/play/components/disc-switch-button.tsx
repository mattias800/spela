import { Disc, Check, Loader2, Download, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/cn";
import { Button } from "@/components/ui";
import { DropdownMenu } from "@/components/ui/dropdown-menu";
import type { DiscState } from "@/hooks/use-disc-manager";
import type { Game } from "@/types/api";

interface DiscSwitchButtonProps {
  game: Game;
  discStates: DiscState[];
  currentDisc: number; // 1-indexed
  isSwitching: boolean;
  onSwitchDisc: (discNumber: number) => void;
  onRetryDisc?: (discNumber: number) => void;
}

export function DiscSwitchButton({
  game,
  discStates,
  currentDisc,
  isSwitching,
  onSwitchDisc,
  onRetryDisc,
}: DiscSwitchButtonProps) {
  if (game.discCount <= 1) return null;

  return (
    <DropdownMenu
      align="left"
      className="w-48"
      trigger={
        <Button
          variant="ghost"
          size="sm"
          disabled={isSwitching}
          title="Switch Disc"
          aria-label={`Current: Disc ${currentDisc}. Click to switch disc.`}
          data-testid="disc-switch-btn"
        >
          <Disc className="h-4 w-4 mr-1" />
          <span className="text-xs">Disc {currentDisc}</span>
        </Button>
      }
    >
      {discStates.map((disc) => {
        const isCurrent = disc.discNumber === currentDisc;
        const isReady = disc.status === "ready";
        const isDownloading = disc.status === "downloading";
        const isError = disc.status === "error";
        const canSwitch = isReady && !isCurrent && !isSwitching;
        const canRetry = isError && !isSwitching;

        return (
          <button
            key={disc.discNumber}
            role="menuitem"
            onClick={() => {
              if (canSwitch) onSwitchDisc(disc.discNumber);
              else if (canRetry) onRetryDisc?.(disc.discNumber);
            }}
            disabled={!canSwitch && !canRetry}
            data-testid={`disc-option-${disc.discNumber}`}
            className={cn(
              "w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors",
              isCurrent
                ? "text-brand-400 bg-brand-500/10"
                : isError
                  ? "text-danger-400 hover:bg-danger-500/10 cursor-pointer"
                  : canSwitch
                    ? "text-surface-200 hover:bg-surface-800 hover:text-surface-100 cursor-pointer"
                    : "text-surface-500 cursor-default",
            )}
          >
            <span className="flex items-center justify-center w-4 h-4 shrink-0">
              {isCurrent ? (
                <Check className="h-4 w-4" />
              ) : isDownloading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : isError ? (
                <AlertTriangle className="h-3.5 w-3.5" />
              ) : isReady ? (
                <Disc className="h-3.5 w-3.5" />
              ) : (
                <Download className="h-3.5 w-3.5 opacity-40" />
              )}
            </span>
            <span className="flex-1">Disc {disc.discNumber}</span>
            {isDownloading && (
              <span className="text-xs text-surface-500">
                {Math.round(disc.progress * 100)}%
              </span>
            )}
            {isError && (
              <span className="text-xs text-danger-400">Retry</span>
            )}
          </button>
        );
      })}
    </DropdownMenu>
  );
}
