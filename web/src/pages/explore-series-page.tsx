import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { BackButton, Badge, Skeleton } from "@/components/ui";
import { useSeriesDetail } from "@/hooks/use-explore";
import { cn } from "@/lib/cn";
import type { SeriesGame, SeriesConsole } from "@/types/api";

function SeriesPageSkeleton() {
  return (
    <div className="space-y-8" data-testid="series-detail-skeleton">
      {/* Hero skeleton */}
      <Skeleton className="w-full h-48 rounded-2xl" />
      {/* Title */}
      <Skeleton className="w-64 h-8" />
      {/* Progress bar */}
      <Skeleton className="w-full h-2 rounded-full" />
      {/* Console badges */}
      <div className="flex gap-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="w-24 h-7 rounded-full" />
        ))}
      </div>
      {/* Timeline */}
      <div className="space-y-4">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="w-full h-20 rounded-xl" />
        ))}
      </div>
    </div>
  );
}

function GameTimelineCard({ game }: { game: SeriesGame }) {
  const year = game.releaseDate
    ? new Date(game.releaseDate).getFullYear()
    : null;

  const content = (
    <div
      className={cn(
        "flex items-center gap-4 p-4 rounded-xl border transition-all duration-200",
        game.inLibrary
          ? "bg-surface-900/60 border-surface-800/50 hover:border-surface-700/50 hover:bg-surface-800/60 cursor-pointer"
          : "bg-surface-950/40 border-surface-800/30 opacity-50 cursor-default",
      )}
      data-testid={`series-game-${game.igdbGameId}`}
    >
      {/* Cover art */}
      <div className="w-12 h-16 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
        {game.coverUrl ? (
          <img
            src={game.coverUrl}
            alt={game.name}
            className="w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-surface-600 text-lg font-bold">
            {game.name.charAt(0)}
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <h3
          className={cn(
            "text-sm font-semibold truncate",
            game.inLibrary ? "text-surface-100" : "text-surface-500",
          )}
        >
          {game.name}
        </h3>
        <div className="flex items-center gap-2 mt-1">
          <Badge
            variant="default"
            style={
              game.inLibrary && game.consoleColor
                ? {
                    backgroundColor: `${game.consoleColor}20`,
                    color: game.consoleColor,
                    borderColor: `${game.consoleColor}40`,
                  }
                : undefined
            }
          >
            {game.consoleName}
          </Badge>
          {year && (
            <span className="text-xs text-surface-500">{year}</span>
          )}
          {game.rating > 0 && (
            <span className="text-xs text-surface-400">
              {game.rating.toFixed(0)}%
            </span>
          )}
        </div>
      </div>

      {/* In-library indicator */}
      {!game.inLibrary && (
        <span className="text-xs text-surface-600 flex-shrink-0">
          Not in library
        </span>
      )}
    </div>
  );

  if (game.inLibrary && game.localGameId) {
    return (
      <Link to={`/games/${game.localGameId}`} className="block">
        {content}
      </Link>
    );
  }

  return content;
}

export function ExploreSeriesPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: series, isLoading } = useSeriesDetail(id);
  const [consoleFilter, setConsoleFilter] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="max-w-4xl space-y-6">
        <BackButton onClick={() => navigate("/explore")}>
          Back to Explore
        </BackButton>
        <SeriesPageSkeleton />
      </div>
    );
  }

  if (!series) {
    return (
      <div className="max-w-4xl space-y-6">
        <BackButton onClick={() => navigate("/explore")}>
          Back to Explore
        </BackButton>
        <p className="text-surface-400 text-center py-20">
          Series not found
        </p>
      </div>
    );
  }

  // Sort games by release date, games without dates go to end
  const sortedGames = [...series.games].sort((a, b) => {
    if (!a.releaseDate && !b.releaseDate) return 0;
    if (!a.releaseDate) return 1;
    if (!b.releaseDate) return -1;
    return a.releaseDate.localeCompare(b.releaseDate);
  });

  // Filter by console if selected
  const filteredGames = consoleFilter
    ? sortedGames.filter((g) => g.consoleAbbreviation === consoleFilter)
    : sortedGames;

  const progressPercent =
    series.totalGames > 0
      ? Math.round((series.libraryGames / series.totalGames) * 100)
      : 0;

  return (
    <div className="max-w-4xl space-y-8" data-testid="series-detail-page">
      <BackButton onClick={() => navigate("/explore")}>
        Back to Explore
      </BackButton>

      {/* Hero banner */}
      {series.heroUrl && (
        <div className="relative w-full h-48 sm:h-56 rounded-2xl overflow-hidden">
          <img
            src={series.heroUrl}
            alt=""
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-surface-950 via-surface-950/50 to-transparent" />
          <div className="absolute bottom-0 left-0 right-0 p-6">
            <h1 className="text-3xl font-bold text-white">{series.name}</h1>
          </div>
        </div>
      )}

      {!series.heroUrl && (
        <h1 className="text-3xl font-bold text-surface-100">{series.name}</h1>
      )}

      {/* Ownership progress */}
      <div data-testid="ownership-progress">
        <p className="text-sm text-surface-400 mb-2">
          You own {series.libraryGames} of {series.totalGames} games
        </p>
        <div className="w-full h-2 bg-surface-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-brand-500 rounded-full transition-all duration-500"
            style={{ width: `${progressPercent}%` }}
            role="progressbar"
            aria-valuenow={series.libraryGames}
            aria-valuemin={0}
            aria-valuemax={series.totalGames}
            aria-label={`${series.libraryGames} of ${series.totalGames} games owned`}
          />
        </div>
      </div>

      {/* Console badges */}
      {series.consoles.length > 0 && (
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
            All ({series.games.length})
          </button>
          {series.consoles.map((console: SeriesConsole) => (
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

      {/* Timeline */}
      <div className="space-y-3" data-testid="series-timeline">
        {filteredGames.map((game) => (
          <GameTimelineCard key={game.igdbGameId} game={game} />
        ))}
        {filteredGames.length === 0 && (
          <p className="text-sm text-surface-500 text-center py-8">
            No games match the selected filter.
          </p>
        )}
      </div>
    </div>
  );
}
