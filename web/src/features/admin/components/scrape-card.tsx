import { useState } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle,
  CheckCircle,
  Loader2,
  RefreshCw,
  RotateCcw,
  ScanSearch,
  Square,
  Trophy,
} from "lucide-react";
import { Button, Section, Select, useToast } from "@/components/ui";
import { useCancelScrape, useScrapeMetadata } from "@/hooks/use-admin";
import { useConsoles } from "@/hooks/use-consoles";
import { useScrapeProgress } from "@/hooks/use-scrape-progress";
import { ProgressBar } from "./progress-bar";

export function ScrapeCard() {
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
            <p className="text-sm text-surface-400 truncate">
              {scrape.gameName ? (
                <>
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
                </>
              ) : (
                "\u00A0"
              )}
            </p>
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
                    const n = data?.total ?? 0;
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
                    const n = data?.total ?? 0;
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
                    const n = data?.total ?? 0;
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
          <Button
            onClick={() =>
              scrapeMetadata.mutate(
                { mode: "ra", console: consoleParam },
                {
                  onSuccess: (data) => {
                    const n = data?.total ?? 0;
                    toast(
                      n === 0 ? "info" : "success",
                      n === 0
                        ? "All games already have achievement data"
                        : `Fetching achievements for ${n} game${n === 1 ? "" : "s"}...`,
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
            icon={<Trophy className="h-4 w-4" />}
            className="w-full"
          >
            Scrape Missing Achievements
          </Button>
        </div>
      </div>
    </Section>
  );
}
