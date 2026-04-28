import { useEffect, useState } from "react";
import { FolderSearch, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui";
import { useScanLibrary } from "@/hooks/use-admin";
import { useSetupDiagnostics } from "@/hooks/use-setup-diagnostics";
import { useScanProgress } from "@/hooks/use-scan-progress";

interface GameScanStepProps {
  onSkip: () => void;
  onComplete: () => void;
}

export function GameScanStep({ onSkip, onComplete }: GameScanStepProps) {
  const { data: diagnostics } = useSetupDiagnostics();
  const scanMutation = useScanLibrary();
  const scan = useScanProgress();
  const [scanError, setScanError] = useState("");
  const [scanKicked, setScanKicked] = useState(false);

  const gameDirsCheck = diagnostics?.checks.find((c) => c.id === "game_dirs");

  // The scan runs asynchronously on the server — the POST /api/admin/games/scan
  // endpoint returns immediately with { message: "scan started" } and the
  // result arrives via the scan_complete WebSocket event. useScanProgress
  // captures that payload in `scan.result`.
  const scanResult = scan.result;
  const phase = scan.phase;
  const scanInProgress =
    scanKicked && (scanMutation.isPending || phase === "active");

  // Surface scan errors coming from the WebSocket too.
  useEffect(() => {
    if (scan.error) setScanError(scan.error);
  }, [scan.error]);

  function handleScan() {
    setScanError("");
    setScanKicked(true);
    scanMutation.mutate(undefined, {
      onError: (err) => {
        setScanError(err instanceof Error ? err.message : "Scan failed");
        setScanKicked(false);
      },
    });
    // Note: `scanKicked` stays true on the success path — no observable
    // effect because the button row flips to "Continue" as soon as
    // `scanResult` is non-null.
  }

  return (
    <div data-comp="GameScanStep" className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-surface-100">
          Scan for Games
        </h2>
        <p className="mt-1 text-sm text-surface-400">
          Scan your game directories to populate your library.
        </p>
      </div>

      {gameDirsCheck && (
        <div className="rounded-xl bg-surface-800/30 border border-surface-800/50 px-4 py-3">
          <div className="flex items-center gap-2">
            <FolderSearch className="h-4 w-4 text-surface-400" />
            <span className="text-sm text-surface-300">
              {gameDirsCheck.detail}
            </span>
          </div>
          {gameDirsCheck.fix && (
            <p className="text-xs text-warning-400 mt-1 ml-6">
              {gameDirsCheck.fix}
            </p>
          )}
        </div>
      )}

      {scanError && (
        <div className="rounded-xl bg-danger-500/10 border border-danger-500/30 px-4 py-3 text-sm text-danger-500">
          {scanError}
        </div>
      )}

      {scanInProgress && !scanResult && (
        <div className="rounded-xl bg-surface-800/30 border border-surface-800/50 px-4 py-3 text-sm text-surface-300">
          {scan.message || "Starting scan..."}
          {scan.total > 0 && (
            <span className="text-surface-500">
              {" "}
              ({scan.current}/{scan.total})
            </span>
          )}
        </div>
      )}

      {scanResult && (
        <div className="rounded-xl bg-success-500/10 border border-success-500/30 px-4 py-3">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-success-500" />
            <span className="text-sm font-medium text-success-500">
              Scan complete — {scanResult.totalGames}{" "}
              {scanResult.totalGames === 1 ? "game" : "games"} found
            </span>
          </div>
          <div className="mt-2 ml-6 space-y-0.5">
            {scanResult.newGames > 0 && (
              <p className="text-xs text-surface-400">
                New: {scanResult.newGames}
              </p>
            )}
            {scanResult.updatedGames > 0 && (
              <p className="text-xs text-surface-400">
                Updated: {scanResult.updatedGames}
              </p>
            )}
            {scanResult.removedGames > 0 && (
              <p className="text-xs text-surface-400">
                Removed: {scanResult.removedGames}
              </p>
            )}
          </div>
        </div>
      )}

      <div className="flex items-center justify-between pt-2">
        {!scanResult ? (
          <>
            <Button variant="secondary" onClick={onSkip} disabled={scanInProgress}>
              Skip
            </Button>
            <Button
              onClick={handleScan}
              loading={scanInProgress}
              disabled={scanInProgress}
            >
              Scan for Games
            </Button>
          </>
        ) : (
          <div className="ml-auto">
            <Button onClick={onComplete}>Continue</Button>
          </div>
        )}
      </div>
    </div>
  );
}
