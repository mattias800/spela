import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { BackButton, Skeleton } from "@/components/ui";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import { usePublisherDetail } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { cn } from "@/lib/cn";
import type { Game } from "@/types/api";

function PublisherPageSkeleton() {
  return (
    <div className="space-y-8" data-testid="publisher-detail-skeleton">
      {/* Title */}
      <Skeleton className="w-64 h-10" />
      {/* Stats row */}
      <Skeleton className="w-96 h-5" />
      {/* Console chips */}
      <div className="flex gap-2">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="w-20 h-8 rounded-full" />
        ))}
      </div>
      {/* Game grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
        {Array.from({ length: 12 }, (_, i) => (
          <GameCardSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

export function PublisherDetailPage() {
  const { name: rawName } = useParams<{ name: string }>();
  const name = rawName ? decodeURIComponent(rawName) : "";
  const navigate = useNavigate();
  const { data: publisher, isLoading } = usePublisherDetail(name);
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();
  const [consoleFilter, setConsoleFilter] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div
        className="max-w-6xl space-y-6"
        data-testid="publisher-detail-page"
      >
        <BackButton onClick={() => navigate(-1)}>
          Back to Explore
        </BackButton>
        <PublisherPageSkeleton />
      </div>
    );
  }

  if (!publisher) {
    return (
      <div
        className="max-w-6xl space-y-6"
        data-testid="publisher-detail-page"
      >
        <BackButton onClick={() => navigate(-1)}>
          Back to Explore
        </BackButton>
        <p className="text-surface-400 text-center py-20">
          No games found for this publisher
        </p>
      </div>
    );
  }

  const filteredGames = consoleFilter
    ? publisher.games.filter((g: Game) => g.consoleName === consoleFilter)
    : publisher.games;

  return (
    <div
      className="max-w-6xl space-y-8"
      data-testid="publisher-detail-page"
    >
      <BackButton onClick={() => navigate(-1)}>
        Back to Explore
      </BackButton>

      {/* Page heading */}
      <h1 className="text-3xl font-bold text-surface-100">{publisher.name}</h1>

      {/* Stats row */}
      <p className="text-sm text-surface-400" data-testid="publisher-stats">
        {publisher.gameCount} {publisher.gameCount === 1 ? "game" : "games"}
        {publisher.avgRating > 0 && (
          <> | Avg rating: {publisher.avgRating.toFixed(1)}</>
        )}
        {publisher.consoles.length > 0 && (
          <> | Consoles: {publisher.consoles.join(", ")}</>
        )}
      </p>

      {/* Console filter chips */}
      {publisher.consoles.length > 1 && (
        <div
          className="flex flex-wrap gap-2"
          data-testid="publisher-console-filters"
        >
          <button
            onClick={() => setConsoleFilter(null)}
            className={cn(
              "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              consoleFilter === null
                ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
                : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
            )}
          >
            All ({publisher.games.length})
          </button>
          {publisher.consoles.map((consoleName: string) => {
            const count = publisher.games.filter(
              (g: Game) => g.consoleName === consoleName,
            ).length;
            return (
              <button
                key={consoleName}
                onClick={() =>
                  setConsoleFilter(
                    consoleFilter === consoleName ? null : consoleName,
                  )
                }
                className={cn(
                  "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                  consoleFilter === consoleName
                    ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
                    : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
                )}
              >
                {consoleName} ({count})
              </button>
            );
          })}
        </div>
      )}

      {/* Game grid */}
      {filteredGames.length > 0 ? (
        <div
          className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5"
          data-testid="publisher-game-grid"
        >
          {filteredGames.map((game: Game) => (
            <GameCard
              key={game.id}
              game={game}
              showConsoleBadge
              onToggleFavorite={handleToggleFavorite}
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </div>
      ) : (
        <p className="text-surface-400 text-center py-12">
          No games match the selected filter.
        </p>
      )}
    </div>
  );
}
