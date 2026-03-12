import { Link } from "react-router-dom";
import { Badge } from "@/components/ui";
import { formatFileSize } from "@/lib/format";
import type { Game } from "@/types/api";

interface GameListRowProps {
  game: Game;
}

export function GameListRow({ game }: GameListRowProps) {
  const consoleName = game.consoleName ?? "";

  return (
    <Link
      to={`/games/${game.id}`}
      className="flex items-center gap-4 px-4 py-3 rounded-xl hover:bg-surface-900/50 transition-colors group"
    >
      <div className="h-12 w-9 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
        {game.coverUrl ? (
          <img
            src={game.coverUrl}
            alt={game.title}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center text-xs font-bold text-surface-600">
            {game.title.charAt(0)}
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-surface-100 truncate group-hover:text-brand-400 transition-colors">
          {game.title}
        </p>
        {consoleName && (
          <p className="text-xs text-surface-500">{consoleName}</p>
        )}
      </div>
      {consoleName && <Badge>{consoleName}</Badge>}
      {game.variantCount != null && game.variantCount > 1 && (
        <span className="text-xs text-surface-400 whitespace-nowrap">
          +{game.variantCount - 1} {game.variantCount === 2 ? "version" : "versions"}
        </span>
      )}
      <span className="text-xs text-surface-500 w-16 text-right">
        {formatFileSize(game.fileSize)}
      </span>
    </Link>
  );
}
