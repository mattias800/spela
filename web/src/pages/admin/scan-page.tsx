import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import {
  ScanSearch,
  FolderSearch,
  CheckCircle,
  AlertCircle,
  Loader2,
  RefreshCw,
  RotateCcw,
  Square,
} from "lucide-react";
import { Button, Section, Select } from "@/components/ui";
import { useScanLibrary, useScrapeMetadata, useCancelScrape } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import { useScrapeProgress } from "@/hooks/use-scrape-progress";
import { useScanProgress } from "@/hooks/use-scan-progress";
import { useConsoles } from "@/hooks/use-consoles";
import { ScrapeStatusCard } from "@/features/admin/components/scrape-status-card";

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

function ScanCard() {
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

function ScrapeCard() {
  const scrapeMetadata = useScrapeMetadata();
  const cancelScrape = useCancelScrape();
  const scrape = useScrapeProgress();
  const { toast } = useToast();
  const { data: consoles } = useConsoles();
  const [selectedConsole, setSelectedConsole] = useState("");

  const isActive = scrape.phase === "active";
  const isComplete = scrape.phase === "complete";
  const isError = scrape.phase === "error";

  const consoleParam = selectedConsole || undefined;

  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
          <ScanSearch className="h-5 w-5 text-brand-400" />
          Scrape Metadata
        </h2>
      </div>
      <div className="px-5 pb-5 space-y-4">
        <p className="text-sm text-surface-400">
          Fetch cover art, descriptions, and other metadata for games that are
          missing information.
        </p>

        <Select
          id="console-filter"
          label="Console filter"
          value={selectedConsole}
          onChange={(e) => setSelectedConsole(e.target.value)}
          disabled={isActive}
          options={[
            { value: "", label: "All consoles" },
            ...(consoles
              ?.slice()
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((c) => ({ value: c.abbreviation, label: c.name })) ?? []),
          ]}
        />

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
                {scrape.gameId ? (
                  <Link
                    to={`/games/${scrape.gameId}`}
                    className="text-brand-400 hover:text-brand-300 transition-colors"
                  >
                    {scrape.gameName}
                  </Link>
                ) : (
                  scrape.gameName
                )}
                {scrape.consoleName && (
                  <span className="text-surface-500 ml-2">
                    ({scrape.consoleName})
                  </span>
                )}
              </p>
            )}
            <ProgressBar value={scrape.current} max={scrape.total} />
            <div className="flex gap-4 text-xs text-surface-400">
              <span className="text-success-500">
                {scrape.successes} succeeded
              </span>
              {scrape.verified > 0 && (
                <span className="text-brand-400">
                  {scrape.verified} verified
                </span>
              )}
              {scrape.failures > 0 && (
                <span className="text-error-500">
                  {scrape.failures} failed
                </span>
              )}
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                cancelScrape.mutate(undefined, {
                  onSuccess: () => toast("info", "Cancelling scrape..."),
                  onError: (err) =>
                    toast(
                      "error",
                      err instanceof Error ? err.message : "Cancel failed",
                    ),
                })
              }
              loading={cancelScrape.isPending}
              icon={<Square className="h-3 w-3" />}
              className="w-full"
            >
              Cancel Scrape
            </Button>
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
                  {scrape.verified > 0 && (
                    <span className="text-brand-400">
                      {scrape.verified} verified
                    </span>
                  )}
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

        <div className="flex flex-col gap-2">
          <Button
            onClick={() =>
              scrapeMetadata.mutate(
                { mode: "new", console: consoleParam },
                {
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
                },
              )
            }
            loading={scrapeMetadata.isPending}
            disabled={isActive}
            variant="secondary"
            icon={<ScanSearch className="h-4 w-4" />}
            className="w-full"
          >
            Scrape New Games
          </Button>
          <Button
            onClick={() =>
              scrapeMetadata.mutate(
                { mode: "fallback", console: consoleParam },
                {
                  onSuccess: (data) => {
                    const n = data.total;
                    toast(
                      n === 0 ? "info" : "success",
                      n === 0
                        ? "No fallback-only games found"
                        : `Re-scraping ${n} game${n === 1 ? "" : "s"}...`,
                    );
                  },
                  onError: (err) =>
                    toast(
                      "error",
                      err instanceof Error ? err.message : "Scrape failed",
                    ),
                },
              )
            }
            loading={scrapeMetadata.isPending}
            disabled={isActive}
            variant="secondary"
            icon={<RotateCcw className="h-4 w-4" />}
            className="w-full"
          >
            Rescrape Fallback Only
          </Button>
          <Button
            onClick={() =>
              scrapeMetadata.mutate(
                { mode: "all", console: consoleParam },
                {
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
                },
              )
            }
            loading={scrapeMetadata.isPending}
            disabled={isActive}
            variant="secondary"
            icon={<RefreshCw className="h-4 w-4" />}
            className="w-full"
          >
            Rescrape All Games
          </Button>
        </div>
      </div>
    </Section>
  );
}

export function AdminScanPage() {
  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Library Scan</h1>
        <p className="mt-1 text-surface-400">
          Scan game directories and update metadata.
        </p>
      </div>

      <ScrapeStatusCard />

      <div className="grid gap-5 md:grid-cols-2">
        <ScanCard />
        <ScrapeCard />
      </div>
    </div>
  );
}
