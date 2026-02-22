import { Loader2, AlertTriangle } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui";
import { formatFileSize } from "@/lib/format";
import type { EmulatorStatus } from "@/hooks/use-emulator-iframe";

interface EmulatorOverlayProps {
  status: EmulatorStatus;
  error: string | null;
  romProgress: { loaded: number; total: number } | null;
  onRetry: () => void;
  onBack: () => void;
  biosMissing?: boolean;
  missingBiosFiles?: string[];
  isAdmin?: boolean;
}

export function EmulatorOverlay({
  status,
  error,
  romProgress,
  onRetry,
  onBack,
  biosMissing,
  missingBiosFiles,
  isAdmin,
}: EmulatorOverlayProps) {
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
    const showBiosError = biosMissing && missingBiosFiles && missingBiosFiles.length > 0;

    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center z-10 bg-surface-950 transition-opacity duration-300">
        <AlertTriangle className="h-8 w-8 text-danger-500 mb-3" />
        {showBiosError ? (
          <>
            <p className="text-sm text-surface-300 mb-2">
              This game requires BIOS files that are not available on the server.
            </p>
            <p className="text-xs text-surface-500 mb-1">Missing files:</p>
            <ul className="text-xs text-surface-400 font-mono mb-4 space-y-0.5">
              {missingBiosFiles.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
            <div className="flex items-center gap-3">
              {isAdmin ? (
                <Link to="/admin/bios">
                  <Button variant="primary" size="sm">
                    Upload BIOS Files
                  </Button>
                </Link>
              ) : (
                <p className="text-xs text-surface-500">
                  Contact your server admin.
                </p>
              )}
              <Button variant="secondary" size="sm" onClick={onBack}>
                Back to Game
              </Button>
            </div>
          </>
        ) : (
          <>
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
          </>
        )}
      </div>
    );
  }

  return null;
}
