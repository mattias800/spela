import { Link } from "react-router-dom";
import { Heart } from "lucide-react";
import { cn } from "@/lib/cn";
import { Badge } from "@/components/ui";
import type { Game } from "@/types/api";

interface GameCardProps {
  game: Game;
  onToggleFavorite?: (game: Game) => void;
}

export function GameCard({ game, onToggleFavorite }: GameCardProps) {
  return (
    <Link
      to={`/games/${game.id}`}
      className="group block space-y-3"
    >
      <div className="relative aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1">
        {game.coverUrl ? (
          <img
            src={game.coverUrl}
            alt={game.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
            <span className="text-3xl font-bold text-surface-700">
              {game.title.charAt(0)}
            </span>
          </div>
        )}

        {/* Hover overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

        {/* Favorite button */}
        {onToggleFavorite && (
          <button
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onToggleFavorite(game);
            }}
            className={cn(
              "absolute top-2.5 right-2.5 p-2 rounded-full transition-all duration-200",
              "opacity-0 group-hover:opacity-100",
              game.isFavorite
                ? "bg-danger-500/20 text-danger-500 opacity-100"
                : "bg-black/40 text-white/70 hover:text-white hover:bg-black/60",
            )}
          >
            <Heart
              className={cn("h-4 w-4", game.isFavorite && "fill-current")}
            />
          </button>
        )}

        {/* Console badge */}
        {game.consoleName && (
          <div className="absolute bottom-2.5 left-2.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <Badge variant="brand">{game.consoleName}</Badge>
          </div>
        )}
      </div>

      <div className="px-1 space-y-1">
        <h3 className="text-sm font-semibold text-surface-100 truncate group-hover:text-brand-400 transition-colors">
          {game.title}
        </h3>
        {game.consoleName && (
          <p className="text-xs text-surface-500">{game.consoleName}</p>
        )}
      </div>
    </Link>
  );
}
