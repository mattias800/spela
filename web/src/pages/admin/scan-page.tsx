import {
  ScanSearch,
  FolderSearch,
  CheckCircle,
  AlertCircle,
  Loader2,
  RefreshCw,
} from "lucide-react";
import { Button, Card, CardHeader, CardContent } from "@/components/ui";
import { useScanLibrary, useScrapeMetadata } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import { useScrapeProgress } from "@/hooks/use-scrape-progress";

interface ScanResult {
  newGames?: number;
  updatedGames?: number;
  removedGames?: number;
  totalGames?: number;
}

function ProgressBar({ value, max }: { value: number; max: number }) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  return (
    <div className="h-2 w-full rounded-full bg-surface-700">
      <div
        className="h-2 rounded-full bg-brand-500 transition-all duration-300 ease-out"
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}

function ScrapeCard() {
  const scrapeMetadata = useScrapeMetadata();
  const scrape = useScrapeProgress();
  const { toast } = useToast();

  const isActive = scrape.phase === "active";
  const isComplete = scrape.phase === "complete";
  const isError = scrape.phase === "error";

  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
          <ScanSearch className="h-5 w-5 text-brand-400" />
          Scrape Metadata
        </h2>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-surface-400">
          Fetch cover art, descriptions, and other metadata for games that are
          missing information.
        </p>

        {isActive && (
          <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
            <div className="flex items-center gap-2 text-sm">
              <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
              <span className="text-surface-200">
                Scraping game {scrape.current} of {scrape.total}...
              </span>
            </div>
            {scrape.gameName && (
              <p className="text-sm text-surface-400 truncate">
                {scrape.gameName}
              </p>
            )}
            <ProgressBar value={scrape.current} max={scrape.total} />
            <div className="flex gap-4 text-xs text-surface-400">
              <span className="text-success-500">
                {scrape.successes} succeeded
              </span>
              {scrape.failures > 0 && (
                <span className="text-error-500">
                  {scrape.failures} failed
                </span>
              )}
            </div>
          </div>
        )}

        {isComplete && (
          <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
            <div className="flex items-center gap-2 text-sm">
              <CheckCircle className="h-4 w-4 text-success-500" />
              <span className="text-surface-200">Scraping complete</span>
            </div>
            <div className="flex gap-4 text-sm">
              {scrape.successes === 0 && scrape.failures === 0 ? (
                <span className="text-surface-400">
                  No unscraped games found
                </span>
              ) : (
                <>
                  <span className="text-surface-300">
                    {scrape.successes} scraped
                  </span>
                  {scrape.failures > 0 && (
                    <span className="text-error-400">
                      {scrape.failures} failed
                    </span>
                  )}
                </>
              )}
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={scrape.dismiss}
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
              <span className="text-error-300">Scraping failed</span>
            </div>
            <p className="text-sm text-error-400">{scrape.error}</p>
            <Button
              variant="secondary"
              size="sm"
              onClick={scrape.dismiss}
              className="w-full"
            >
              Dismiss
            </Button>
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          <Button
            onClick={() =>
              scrapeMetadata.mutate(false, {
                onSuccess: (data) => {
                  const n = data.total;
                  toast(
                    n === 0 ? "info" : "success",
                    n === 0
                      ? "No unscraped games found"
                      : `Scraping ${n} game${n === 1 ? "" : "s"}...`,
                  );
                },
                onError: (err) =>
                  toast(
                    "error",
                    err instanceof Error ? err.message : "Scrape failed",
                  ),
              })
            }
            loading={scrapeMetadata.isPending}
            disabled={isActive}
            variant="secondary"
            className="flex-1 min-w-[10rem]"
          >
            <ScanSearch className="h-4 w-4" />
            Scrape New Games
          </Button>
          <Button
            onClick={() =>
              scrapeMetadata.mutate(true, {
                onSuccess: (data) => {
                  const n = data.total;
                  toast(
                    n === 0 ? "info" : "success",
                    n === 0
                      ? "No games found to scrape"
                      : `Re-scraping ${n} game${n === 1 ? "" : "s"}...`,
                  );
                },
                onError: (err) =>
                  toast(
                    "error",
                    err instanceof Error ? err.message : "Scrape failed",
                  ),
              })
            }
            loading={scrapeMetadata.isPending}
            disabled={isActive}
            variant="secondary"
            className="flex-1 min-w-[10rem]"
          >
            <RefreshCw className="h-4 w-4" />
            Rescrape All Games
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

export function AdminScanPage() {
  const scanLibrary = useScanLibrary();
  const { toast } = useToast();

  const scanResult = scanLibrary.data as ScanResult | undefined;

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Library Scan</h1>
        <p className="mt-1 text-surface-400">
          Scan game directories and update metadata.
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
              <FolderSearch className="h-5 w-5 text-brand-400" />
              Scan for Games
            </h2>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-surface-400">
              Scan configured game directories for new ROMs. Previously detected
              games will not be duplicated.
            </p>

            {scanLibrary.isPending && (
              <div className="rounded-xl bg-surface-800/50 p-4">
                <div className="flex items-center gap-2 text-sm">
                  <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
                  <span className="text-surface-200">
                    Scanning game directories...
                  </span>
                </div>
              </div>
            )}

            {scanResult && (
              <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success-500" />
                  <span className="text-surface-200">Scan complete</span>
                </div>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  {scanResult.totalGames !== undefined && (
                    <div>
                      <p className="text-surface-500">Total games</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.totalGames}
                      </p>
                    </div>
                  )}
                  {scanResult.newGames !== undefined && (
                    <div>
                      <p className="text-surface-500">New games</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.newGames}
                      </p>
                    </div>
                  )}
                  {scanResult.updatedGames !== undefined && (
                    <div>
                      <p className="text-surface-500">Updated</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.updatedGames}
                      </p>
                    </div>
                  )}
                  {scanResult.removedGames !== undefined && (
                    <div>
                      <p className="text-surface-500">Removed</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.removedGames}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}

            <Button
              onClick={() =>
                scanLibrary.mutate(undefined, {
                  onError: (err) =>
                    toast(
                      "error",
                      err instanceof Error ? err.message : "Scan failed",
                    ),
                })
              }
              loading={scanLibrary.isPending}
              className="w-full"
            >
              <FolderSearch className="h-4 w-4" />
              Start Scan
            </Button>
          </CardContent>
        </Card>

        <ScrapeCard />
      </div>
    </div>
  );
}
