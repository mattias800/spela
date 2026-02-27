import { useState } from "react";
import { FolderSearch, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui";
import { useScanLibrary } from "@/hooks/use-admin";
import { useSetupDiagnostics } from "@/hooks/use-setup-diagnostics";

interface GameScanStepProps {
  onSkip: () => void;
  onComplete: () => void;
}

export function GameScanStep({ onSkip, onComplete }: GameScanStepProps) {
  const { data: diagnostics } = useSetupDiagnostics();
  const scanMutation = useScanLibrary();
  const [scanResult, setScanResult] = useState<Record<string, unknown> | null>(
    null,
  );
  const [scanError, setScanError] = useState("");

  const gameDirsCheck = diagnostics?.checks.find((c) => c.id === "game_dirs");

  function handleScan() {
    setScanError("");
    scanMutation.mutate(undefined, {
      onSuccess: (data) => {
        setScanResult(data);
      },
      onError: (err) => {
        setScanError(err instanceof Error ? err.message : "Scan failed");
      },
    });
  }

  const gamesFound = scanResult
    ? Object.entries(scanResult)
        .filter(([key]) => key !== "message")
        .reduce(
          (sum, [, val]) => sum + (typeof val === "number" ? val : 0),
          0,
        )
    : null;

  return (
    <div className="space-y-6">
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

      {scanResult && (
        <div className="rounded-xl bg-success-500/10 border border-success-500/30 px-4 py-3">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-success-500" />
            <span className="text-sm font-medium text-success-500">
              Scan complete — {gamesFound}{" "}
              {gamesFound === 1 ? "game" : "games"} found
            </span>
          </div>
          {scanResult && gamesFound !== null && gamesFound > 0 && (
            <div className="mt-2 ml-6 space-y-0.5">
              {Object.entries(scanResult)
                .filter(
                  ([key, val]) =>
                    key !== "message" && typeof val === "number" && val > 0,
                )
                .map(([console_, count]) => (
                  <p key={console_} className="text-xs text-surface-400">
                    {console_}: {count as number}{" "}
                    {(count as number) === 1 ? "game" : "games"}
                  </p>
                ))}
            </div>
          )}
        </div>
      )}

      <div className="flex items-center justify-between pt-2">
        {!scanResult ? (
          <>
            <Button variant="secondary" onClick={onSkip}>
              Skip
            </Button>
            <Button onClick={handleScan} loading={scanMutation.isPending}>
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
