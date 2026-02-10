import { Loader2, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui";
import { formatFileSize } from "@/lib/format";
import type { EmulatorStatus } from "@/hooks/use-emulator-iframe";

interface EmulatorOverlayProps {
  status: EmulatorStatus;
  error: string | null;
  romProgress: { loaded: number; total: number } | null;
  onRetry: () => void;
  onBack: () => void;
}

export function EmulatorOverlay({ status, error, romProgress, onRetry, onBack }: EmulatorOverlayProps) {
  if (status === "loading") {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center z-10 bg-surface-950 transition-opacity duration-300">
        <Loader2 className="h-8 w-8 animate-spin text-brand-500 mb-3" />
        <p className="text-sm text-surface-400">
          {romProgress
            ? `Loading ROM... ${formatFileSize(romProgress.loaded)} / ${formatFileSize(romProgress.total)}`
            : "Initializing emulator..."}
        </p>
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center z-10 bg-surface-950 transition-opacity duration-300">
        <AlertTriangle className="h-8 w-8 text-danger-500 mb-3" />
        <p className="text-sm text-surface-300 mb-4">
          {error ?? "An error occurred"}
        </p>
        <div className="flex items-center gap-3">
          <Button variant="primary" size="sm" onClick={onRetry}>
            Try Again
          </Button>
          <Button variant="secondary" size="sm" onClick={onBack}>
            Back to Game
          </Button>
        </div>
      </div>
    );
  }

  return null;
}
