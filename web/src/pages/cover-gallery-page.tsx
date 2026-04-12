import { useState } from "react";
import { Link } from "react-router-dom";
import { Grid2x2, Grid3x3, LayoutGrid, ImageIcon } from "lucide-react";
import { Button, Skeleton, EmptyState } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { ConsoleBadge } from "@/components/console-badge";
import { CoverImage } from "@/components/cover-image";
import { cn } from "@/lib/cn";
import { useCoverGallery } from "@/hooks/use-explore";
import { useConsoles } from "@/hooks/use-consoles";
import type { CoverItem } from "@/types/api";

type ZoomLevel = "small" | "medium" | "large";

const zoomGridClasses: Record<ZoomLevel, string> = {
  small: "grid-cols-5 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 xl:grid-cols-12 gap-2",
  medium: "grid-cols-4 sm:grid-cols-5 md:grid-cols-6 lg:grid-cols-8 xl:grid-cols-10 gap-3",
  large: "grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-8 gap-3",
};

function CoverGallerySkeleton() {
  return (
    <div data-testid="cover-gallery-skeleton">
      <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-3">
        {Array.from({ length: 20 }, (_, i) => (
          <Skeleton
            key={i}
            className="w-full rounded-lg"
            style={{ aspectRatio: "3/4" }}
          />
        ))}
      </div>
    </div>
  );
}

function CoverCard({ cover }: { cover: CoverItem }) {
  return (
    <Link
      to={`/games/${cover.gameId}`}
      className="group relative block rounded-lg overflow-hidden"
      role="listitem"
      data-testid="cover-card"
    >
      <div
        className="w-full"
        style={{ aspectRatio: `${cover.coverAspectRatio || 0.75}` }}
      >
        <CoverImage src={cover.coverUrl} alt={cover.gameTitle} className="w-full h-full" />
      </div>
      {/* Hover overlay */}
      <div
        className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex flex-col justify-end p-2"
        data-testid="cover-overlay"
      >
        <p className="text-xs font-semibold text-white truncate">
          {cover.gameTitle}
        </p>
        <ConsoleBadge code={cover.consoleAbbreviation} />
      </div>
    </Link>
  );
}

export function CoverGalleryPage() {
  const [page, setPage] = useState(1);
  const [consoleFilter, setConsoleFilter] = useState<string | undefined>();
  const [zoom, setZoom] = useState<ZoomLevel>("medium");
  const { data: consolesData } = useConsoles();

  const [allCovers, setAllCovers] = useState<CoverItem[]>([]);

  const { data, isLoading } = useCoverGallery(page, consoleFilter);

  const covers =
    page === 1
      ? data?.covers ?? []
      : [...allCovers, ...(data?.covers ?? [])];

  const hasMore = data ? data.page < data.totalPages : false;

  const handleLoadMore = () => {
    setAllCovers(covers);
    setPage((p) => p + 1);
  };

  const handleConsoleFilter = (value: string) => {
    setConsoleFilter(value || undefined);
    setPage(1);
    setAllCovers([]);
  };

  const isInitialLoading = isLoading && page === 1;

  return (
    <PageLayout backButtonVariant="standard" data-testid="cover-gallery-page">
      <SectionList className="max-w-7xl">

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">
            Cover Art Wall
          </h1>
          <p className="mt-1 text-surface-400">
            Browse cover art from your library.
          </p>
        </div>

        {/* Zoom controls */}
        <div
          className="flex items-center gap-1 rounded-lg border border-surface-700 bg-surface-900 p-1"
          role="group"
          aria-label="Grid size"
          data-testid="zoom-controls"
        >
          <button
            onClick={() => setZoom("small")}
            className={cn(
              "p-1.5 rounded transition-colors",
              zoom === "small"
                ? "bg-brand-500/20 text-brand-400"
                : "text-surface-400 hover:text-surface-200",
            )}
            aria-label="Small grid"
            aria-pressed={zoom === "small"}
            data-testid="zoom-small"
          >
            <Grid3x3 className="h-4 w-4" />
          </button>
          <button
            onClick={() => setZoom("medium")}
            className={cn(
              "p-1.5 rounded transition-colors",
              zoom === "medium"
                ? "bg-brand-500/20 text-brand-400"
                : "text-surface-400 hover:text-surface-200",
            )}
            aria-label="Medium grid"
            aria-pressed={zoom === "medium"}
            data-testid="zoom-medium"
          >
            <Grid2x2 className="h-4 w-4" />
          </button>
          <button
            onClick={() => setZoom("large")}
            className={cn(
              "p-1.5 rounded transition-colors",
              zoom === "large"
                ? "bg-brand-500/20 text-brand-400"
                : "text-surface-400 hover:text-surface-200",
            )}
            aria-label="Large grid"
            aria-pressed={zoom === "large"}
            data-testid="zoom-large"
          >
            <LayoutGrid className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Console filter chips */}
      <div
        className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide"
        style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
        data-testid="console-chips"
      >
        <button
          onClick={() => handleConsoleFilter("")}
          className={cn(
            "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            !consoleFilter
              ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
              : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
          )}
        >
          All
        </button>
        {consolesData?.map((c) => (
          <button
            key={c.id}
            onClick={() => handleConsoleFilter(c.id)}
            className={cn(
              "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              consoleFilter === c.id
                ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
                : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
            )}
            style={
              consoleFilter === c.id && c.colorTheme
                ? {
                    backgroundColor: `${c.colorTheme}20`,
                    color: c.colorTheme,
                    borderColor: `${c.colorTheme}40`,
                  }
                : undefined
            }
          >
            {c.abbreviation.toUpperCase()}
          </button>
        ))}
      </div>

      {/* Cover grid */}
      {isInitialLoading ? (
        <CoverGallerySkeleton />
      ) : covers.length === 0 ? (
        <EmptyState
          icon={ImageIcon}
          title="No covers found"
          description="There are no cover art images matching your filters."
          data-testid="empty-state"
        />
      ) : (
        <>
          <div
            className={cn("grid", zoomGridClasses[zoom])}
            role="list"
            aria-label="Cover art gallery"
            data-testid="cover-grid"
          >
            {covers.map((cover, i) => (
              <CoverCard key={`${cover.gameId}-${i}`} cover={cover} />
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
