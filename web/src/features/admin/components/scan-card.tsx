import { useEffect, useState } from "react";
import {
  AlertCircle,
  CheckCircle,
  FolderSearch,
  Loader2,
} from "lucide-react";
import { Button, Section, useToast } from "@/components/ui";
import { useScanLibrary } from "@/hooks/use-admin";
import { useScanProgress } from "@/hooks/use-scan-progress";
import { ProgressBar } from "./progress-bar";

export function ScanCard() {
  const scanLibrary = useScanLibrary();
  const scan = useScanProgress();
  const { toast } = useToast();
  const [scanRequested, setScanRequested] = useState(false);

  const isActive = scan.phase === "active";
  const isComplete = scan.phase === "complete";
  const isError = scan.phase === "error";

  // Clear the scanRequested flag once the WebSocket phase catches up
  useEffect(() => {
    if (scanRequested && (isActive || isComplete || isError)) {
      setScanRequested(false);
    }
  }, [scanRequested, isActive, isComplete, isError]);

  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
          <FolderSearch className="h-5 w-5 text-brand-400" />
          Scan for Games
        </h2>
      </div>
      <div className="px-5 pb-5 space-y-4">
        <p className="text-sm text-surface-400">
          Scan configured game directories for new ROMs. Previously detected
          games will not be duplicated.
        </p>

        {isActive && (
          <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
            <div className="flex items-center gap-2 text-sm">
              <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
              <span className="text-surface-200">{scan.message}</span>
            </div>
            {scan.total > 0 && (
              <ProgressBar value={scan.current} max={scan.total} />
            )}
          </div>
        )}

        {isComplete && scan.result && (
          <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
            <div className="flex items-center gap-2 text-sm">
              <CheckCircle className="h-4 w-4 text-success-500" />
              <span className="text-surface-200">Scan complete</span>
            </div>
            <div className="grid grid-cols-2 gap-2 text-sm">
              {scan.result.totalGames !== undefined && (
                <div>
                  <p className="text-surface-500">Total games</p>
                  <p className="text-surface-100 font-semibold">
                    {scan.result.totalGames}
                  </p>
                </div>
              )}
              {scan.result.newGames !== undefined && (
                <div>
                  <p className="text-surface-500">New games</p>
                  <p className="text-surface-100 font-semibold">
                    {scan.result.newGames}
                  </p>
                </div>
              )}
              {scan.result.updatedGames !== undefined && (
                <div>
                  <p className="text-surface-500">Updated</p>
                  <p className="text-surface-100 font-semibold">
                    {scan.result.updatedGames}
                  </p>
                </div>
              )}
              {scan.result.removedGames !== undefined && (
                <div>
                  <p className="text-surface-500">Removed</p>
                  <p className="text-surface-100 font-semibold">
                    {scan.result.removedGames}
                  </p>
                </div>
              )}
            </div>
            {scan.result.newGamesList && scan.result.newGamesList.length > 0 && (
              <div className="mt-2">
                <p className="text-xs font-medium text-surface-400 mb-1.5">
                  New games found:
                </p>
                <ul className="space-y-1 max-h-48 overflow-y-auto text-sm">
                  {scan.result.newGamesList.map((g) => (
                    <li
                      key={g.id}
                      className="flex items-center justify-between text-surface-200"
                    >
                      <span className="truncate">{g.title}</span>
                      <span className="text-xs text-surface-500 ml-2 shrink-0">
                        {g.consoleName}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <Button
              variant="secondary"
              size="sm"
              onClick={scan.dismiss}
              className="w-full"
            >
              Dismiss
            </Button>
          </div>
        )}

        {isError && (
          <div className="rounded-xl bg-error-900/30 border border-error-700/50 p-4 space-y-3">
            <div className="flex items-center gap-2 text-sm">
              <AlertCircle className="h-4 w-4 text-error-400" />
              <span className="text-error-300">Scan failed</span>
            </div>
            <p className="text-sm text-error-400">{scan.error}</p>
            <Button
              variant="secondary"
              size="sm"
              onClick={scan.dismiss}
              className="w-full"
            >
              Dismiss
            </Button>
          </div>
        )}

        <Button
          onClick={() => {
            setScanRequested(true);
            scanLibrary.mutate(undefined, {
              onError: (err) => {
                setScanRequested(false);
                toast(
                  "error",
                  err instanceof Error ? err.message : "Scan failed",
                );
              },
            });
          }}
          loading={scanLibrary.isPending || scanRequested}
          disabled={isActive || scanLibrary.isPending || scanRequested}
          icon={<FolderSearch className="h-4 w-4" />}
          className="w-full"
        >
          Start Scan
        </Button>
      </div>
    </Section>
  );
}
