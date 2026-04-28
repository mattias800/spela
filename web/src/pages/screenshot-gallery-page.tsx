import { useState } from "react";
import { Link } from "react-router-dom";
import { ImageIcon } from "lucide-react";
import { Button, Skeleton, EmptyState } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { ConsoleBadge } from "@/components/console-badge";
import { useScreenshotGallery } from "@/hooks/use-explore";
import { useConsoles } from "@/hooks/use-consoles";
import type { ScreenshotItem } from "@/types/api";

function GallerySkeleton() {
  return (
    <div data-comp="GallerySkeleton" data-testid="screenshot-gallery-skeleton">
      <div className="columns-2 md:columns-3 xl:columns-4 gap-4">
        {Array.from({ length: 12 }, (_, i) => (
          <div key={i} className="break-inside-avoid mb-4">
            <Skeleton
              className="w-full rounded-xl"
              style={{ height: `${160 + (i % 3) * 60}px` }}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

function ScreenshotCard({ screenshot }: { screenshot: ScreenshotItem }) {
  return (
    <div data-comp="ScreenshotCard" className="break-inside-avoid mb-4" role="listitem" data-testid="screenshot-card">
      <Link
        to={`/games/${screenshot.gameId}`}
        className="group relative block rounded-xl overflow-hidden"
      >
        <img
          src={screenshot.url}
          alt={`Screenshot from ${screenshot.gameTitle}`}
          className="w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
          loading="lazy"
        />
        {/* Hover overlay */}
        <div
          className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex flex-col justify-end p-3"
          data-testid="screenshot-overlay"
        >
          <p className="text-sm font-semibold text-white truncate">
            {screenshot.gameTitle}
          </p>
          <ConsoleBadge code={screenshot.consoleAbbreviation} />
        </div>
      </Link>
    </div>
  );
}

export function ScreenshotGalleryPage() {
  const [page, setPage] = useState(1);
  const [consoleFilter, setConsoleFilter] = useState<string | undefined>();
  const { data: consolesData } = useConsoles();

  // Collect all pages of screenshots
  const [allScreenshots, setAllScreenshots] = useState<ScreenshotItem[]>([]);

  const { data, isLoading } = useScreenshotGallery(page, {
    console: consoleFilter,
  });

  // When data changes, merge into accumulated screenshots
  // Reset when filters change
  const screenshots =
    page === 1
      ? data?.screenshots ?? []
      : [...allScreenshots, ...(data?.screenshots ?? [])];

  const hasMore = data ? data.page < data.totalPages : false;

  const handleLoadMore = () => {
    setAllScreenshots(screenshots);
    setPage((p) => p + 1);
  };

  const handleFilterChange = (newConsole?: string) => {
    setConsoleFilter(newConsole);
    setPage(1);
    setAllScreenshots([]);
  };

  const isInitialLoading = isLoading && page === 1;

  return (
    <PageLayout backButtonVariant="standard" data-testid="screenshot-gallery-page" title="Screenshot Gallery" subtitle="Browse screenshots from your library.">
      <SectionList className="max-w-6xl">

      {/* Filter bar */}
      <div className="flex flex-wrap gap-3" data-testid="gallery-filters">
        <select
          value={consoleFilter ?? ""}
          onChange={(e) =>
            handleFilterChange(e.target.value || undefined)
          }
          className="rounded-lg border border-surface-700 bg-surface-900 px-3 py-2 text-sm text-surface-200 focus:outline-none focus:ring-2 focus:ring-brand-500"
          data-testid="console-filter"
        >
          <option value="">All Consoles</option>
          {consolesData?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {/* Gallery */}
      {isInitialLoading ? (
        <GallerySkeleton />
      ) : screenshots.length === 0 ? (
        <EmptyState
          icon={ImageIcon}
          title="No screenshots found"
          description="There are no screenshots matching your filters."
          data-testid="empty-state"
        />
      ) : (
        <>
          <div
            className="columns-2 md:columns-3 xl:columns-4 gap-4"
            role="list"
            aria-label="Screenshot gallery"
            data-testid="screenshot-grid"
          >
            {screenshots.map((ss, i) => (
              <ScreenshotCard key={`${ss.gameId}-${ss.url}-${i}`} screenshot={ss} />
            ))}
          </div>

          {/* Load more */}
          {hasMore && (
            <div className="flex justify-center pt-4">
              <Button
                variant="secondary"
                onClick={handleLoadMore}
                disabled={isLoading}
                data-testid="load-more-button"
              >
                {isLoading ? "Loading..." : "Load More"}
              </Button>
            </div>
          )}
        </>
      )}
    </SectionList>
    </PageLayout>
  );
}

export default ScreenshotGalleryPage;
