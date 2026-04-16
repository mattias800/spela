import { BarChart2 } from "lucide-react";
import {
  Section,
  Button,
  Skeleton,
  Divider,
  useToast,
} from "@/components/ui";
import {
  useScrapeStatusCounts,
  useScrapeMetadata,
  useScrapeStatus,
  type ScrapeSourceCounts,
} from "@/hooks/use-admin";
import { useScrapeProgress } from "@/hooks/use-scrape-progress";

const SOURCE_LABELS: Record<string, string> = {
  igdb: "IGDB",
  libretro: "LibRetro Thumbnails",
  steamgriddb: "SteamGridDB",
};

function sourceLabel(source: string): string {
  return SOURCE_LABELS[source] ?? source;
}

interface SourceSectionProps {
  counts: ScrapeSourceCounts;
  scrapeActive: boolean;
  onRetryNotFound: () => void;
  onRetryErrors: () => void;
  onScrapeNotAttempted: () => void;
  isPending: boolean;
}

function SourceSection({
  counts,
  scrapeActive,
  onRetryNotFound,
  onRetryErrors,
  onScrapeNotAttempted,
  isPending,
}: SourceSectionProps) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-semibold text-surface-200">
        {sourceLabel(counts.source)}
      </h3>
      <div className="space-y-1.5 text-sm">
        <div className="flex items-center justify-between">
          <span className="text-success-400">{counts.matched} matched</span>
        </div>

        <div className="flex items-center justify-between gap-2">
          <span className="text-amber-400">
            {counts.notFound} not found ({counts.notFoundEligible} eligible)
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={scrapeActive || counts.notFoundEligible === 0 || isPending}
            onClick={onRetryNotFound}
            className="shrink-0 text-xs px-2 py-1 h-auto"
          >
            Retry now
          </Button>
        </div>

        <div className="flex items-center justify-between gap-2">
          <span className="text-error-400">
            {counts.error} errors ({counts.errorEligible} eligible)
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={scrapeActive || counts.errorEligible === 0 || isPending}
            onClick={onRetryErrors}
            className="shrink-0 text-xs px-2 py-1 h-auto"
          >
            Retry now
          </Button>
        </div>

        <div className="flex items-center justify-between gap-2">
          <span className="text-surface-400">
            {counts.notAttempted} not attempted
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={scrapeActive || counts.notAttempted === 0 || isPending}
            onClick={onScrapeNotAttempted}
            className="shrink-0 text-xs px-2 py-1 h-auto"
          >
            Scrape now
          </Button>
        </div>
      </div>
    </div>
  );
}

export function ScrapeStatusCard() {
  const { data, isLoading } = useScrapeStatusCounts();
  const { data: scrapeStatusData } = useScrapeStatus();
  const scrape = useScrapeProgress();
  const scrapeMetadata = useScrapeMetadata();
  const { toast } = useToast();

  const scrapeActive =
    scrapeStatusData?.active === true || scrape.phase === "active";

  function handleRetry(source: string, status: string) {
    scrapeMetadata.mutate(
      { source, status },
      {
        onSuccess: (data) => {
          const n = data.total;
          toast(
            n === 0 ? "info" : "success",
            n === 0
              ? "No eligible games found"
              : `Scraping ${n} game${n === 1 ? "" : "s"}...`,
          );
        },
        onError: (err) =>
          toast(
            "error",
            err instanceof Error ? err.message : "Scrape failed",
          ),
      },
    );
  }

  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
          <BarChart2 className="h-5 w-5 text-brand-400" />
          Scrape Status
        </h2>
      </div>
      <div className="px-5 pb-5 space-y-5">
        <p className="text-sm text-surface-400">
          Per-source breakdown of metadata scrape results across your library.
        </p>

        {isLoading && (
          <div className="space-y-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="space-y-2">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-full" />
              </div>
            ))}
          </div>
        )}

        {!isLoading && (!data || data.sources.length === 0) && (
          <p className="text-sm text-surface-500">No scrape data available.</p>
        )}

        {!isLoading &&
          data &&
          data.sources.map((counts, i) => (
            <div key={counts.source}>
              {i > 0 && (
                <Divider className="mb-5" />
              )}
              <SourceSection
                counts={counts}
                scrapeActive={scrapeActive}
                isPending={scrapeMetadata.isPending}
                onRetryNotFound={() => handleRetry(counts.source, "not_found")}
                onRetryErrors={() => handleRetry(counts.source, "error")}
                onScrapeNotAttempted={() =>
                  handleRetry(counts.source, "not_attempted")
                }
              />
            </div>
          ))}
      </div>
    </Section>
  );
}
