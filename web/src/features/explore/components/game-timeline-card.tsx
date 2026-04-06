import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";
import { RatingDisplay } from "@/components/rating-display";
import { ConsoleBadge } from "@/components/console-badge";
import { getReleaseYear } from "@/lib/date-utils";
import { CoverImage } from "@/components/cover-image";
import type { SeriesGame } from "@/types/api";

interface GameTimelineCardProps {
  game: SeriesGame;
  testIdPrefix: string;
}

export function GameTimelineCard({ game, testIdPrefix }: GameTimelineCardProps) {
  const year = getReleaseYear(game.releaseDate);

  const content = (
    <div
      className={cn(
        "flex items-center gap-4 p-4 rounded-xl border transition-all duration-200",
        game.inLibrary
          ? "bg-surface-900/60 border-surface-800/50 hover:border-surface-700/50 hover:bg-surface-800/60 cursor-pointer"
          : "bg-surface-950/40 border-surface-800/30 opacity-50 cursor-default",
      )}
      data-testid={`${testIdPrefix}-game-${game.igdbGameId}`}
    >
      {/* Cover art */}
      <CoverImage src={game.coverUrl} alt={game.name} className="w-12 h-16 rounded-lg" />

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
          {game.consoleName && (
            <ConsoleBadge code={game.consoleAbbreviation} label={game.consoleName} />
          )}
          {year && (
            <span className="text-xs text-surface-500">{year}</span>
          )}
          {game.igdbCriticsRating > 0 && (
            <RatingDisplay value={game.igdbCriticsRating / 10} />
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
      <Link to={`/games/${game.localGameId}`} className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950">
        {content}
      </Link>
    );
  }

  return content;
}
