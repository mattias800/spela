import { useState } from "react";
import { useParams } from "react-router-dom";
import { Skeleton } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useFranchiseDetail } from "@/hooks/use-explore";
import { GameTimelineCard } from "@/features/explore/components/game-timeline-card";
import { cn } from "@/lib/cn";
import type { SeriesConsole } from "@/types/api";

function FranchisePageSkeleton() {
  return (
    <div className="space-y-8" data-testid="franchise-detail-skeleton">
      <Skeleton className="w-full h-64 sm:h-80 lg:h-96 rounded-2xl" />
      <Skeleton className="w-64 h-10" />
      <Skeleton className="w-full h-2 rounded-full" />
      <div className="flex gap-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="w-24 h-7 rounded-full" />
        ))}
      </div>
      <div className="space-y-4">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="w-full h-20 rounded-xl" />
        ))}
      </div>
    </div>
  );
}

export function ExploreFranchisePage() {
  const { id } = useParams<{ id: string }>();
  const { data: franchise, isLoading } = useFranchiseDetail(id);
  const [consoleFilter, setConsoleFilter] = useState<string | null>(null);

  if (isLoading) {
    return (
      <PageLayout backButtonVariant="standard" backTo="/explore" backLabel="Explore">
        <SectionList className="max-w-4xl">
          <FranchisePageSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (!franchise) {
    return (
      <PageLayout backButtonVariant="standard" backTo="/explore" backLabel="Explore">
        <SectionList className="max-w-4xl">
          <p className="text-surface-400 text-center py-20">
            Franchise not found
          </p>
        </SectionList>
      </PageLayout>
    );
  }

  const sortedGames = [...franchise.games].sort((a, b) => {
    if (!a.releaseDate && !b.releaseDate) return 0;
    if (!a.releaseDate) return 1;
    if (!b.releaseDate) return -1;
    return a.releaseDate.localeCompare(b.releaseDate);
  });

  const filteredGames = consoleFilter
    ? sortedGames.filter((g) => g.consoleAbbreviation === consoleFilter)
    : sortedGames;

  const progressPercent =
    franchise.totalGames > 0
      ? Math.round((franchise.libraryGames / franchise.totalGames) * 100)
      : 0;

  return (
    <PageLayout backButtonVariant="standard" backTo="/explore" backLabel="Explore" data-testid="franchise-detail-page">
      <SectionList>
      {/* Full-width hero banner */}
      {franchise.heroUrl ? (
        <div className="relative w-full h-64 sm:h-80 lg:h-96 -mx-6">
          <div className="relative w-full h-full rounded-2xl overflow-hidden">
            <img
              src={franchise.heroUrl}
              alt=""
              className="w-full h-full object-cover"
            />
            {/* Multi-layer gradient for depth */}
            <div className="absolute inset-0 bg-gradient-to-t from-surface-950 via-surface-950/60 to-transparent" />
            <div className="absolute inset-0 bg-gradient-to-r from-surface-950/40 via-transparent to-surface-950/40" />

            {/* Title + progress overlaid at bottom */}
            <div className="absolute bottom-0 left-0 right-0 p-6 sm:p-8">
              <h1 className="text-4xl sm:text-5xl font-bold text-white drop-shadow-lg mb-3">
                {franchise.name}
              </h1>
              <div className="flex items-center gap-4" data-testid="ownership-progress">
                <p className="text-sm text-white/80">
                  You own {franchise.libraryGames} of {franchise.totalGames} games
                </p>
                <div className="flex-1 max-w-xs h-2 bg-white/20 rounded-full overflow-hidden backdrop-blur-sm">
                  <div
                    className="h-full bg-brand-400 rounded-full transition-all duration-500"
                    style={{ width: `${progressPercent}%` }}
                    role="progressbar"
                    aria-valuenow={franchise.libraryGames}
                    aria-valuemin={0}
                    aria-valuemax={franchise.totalGames}
                    aria-label={`${franchise.libraryGames} of ${franchise.totalGames} games owned`}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <>
          <h1 className="text-4xl font-bold text-surface-100">{franchise.name}</h1>
          <div data-testid="ownership-progress">
            <p className="text-sm text-surface-400 mb-2">
              You own {franchise.libraryGames} of {franchise.totalGames} games
            </p>
            <div className="w-full h-2 bg-surface-800 rounded-full overflow-hidden">
              <div
                className="h-full bg-brand-500 rounded-full transition-all duration-500"
                style={{ width: `${progressPercent}%` }}
                role="progressbar"
                aria-valuenow={franchise.libraryGames}
                aria-valuemin={0}
                aria-valuemax={franchise.totalGames}
                aria-label={`${franchise.libraryGames} of ${franchise.totalGames} games owned`}
              />
            </div>
          </div>
        </>
      )}

      {franchise.consoles.length > 0 && (
        <div className="flex flex-wrap gap-2" data-testid="console-filters">
          <button
            onClick={() => setConsoleFilter(null)}
            className={cn(
              "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              consoleFilter === null
                ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
                : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
            )}
          >
            All ({franchise.games.length})
          </button>
          {franchise.consoles.map((console: SeriesConsole) => (
            <button
              key={console.abbreviation}
              onClick={() =>
                setConsoleFilter(
                  consoleFilter === console.abbreviation
                    ? null
                    : console.abbreviation,
                )
              }
              className={cn(
                "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                consoleFilter === console.abbreviation
                  ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
                  : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
              )}
              style={
                consoleFilter === console.abbreviation && console.color
                  ? {
                      backgroundColor: `${console.color}20`,
                      color: console.color,
                      borderColor: `${console.color}40`,
                    }
                  : undefined
              }
            >
              {console.name} ({console.gameCount})
            </button>
          ))}
        </div>
      )}

      <div className="max-w-4xl space-y-3" data-testid="franchise-timeline">
        {filteredGames.map((game) => (
          <GameTimelineCard key={game.igdbGameId} game={game} testIdPrefix="franchise" />
        ))}
        {filteredGames.length === 0 && (
          <p className="text-sm text-surface-500 text-center py-8">
            No games match the selected filter.
          </p>
        )}
      </div>
    </SectionList>
    </PageLayout>
  );
}

export default ExploreFranchisePage;
