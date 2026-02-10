import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, Library } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useConsoles, useConsoleGames } from "@/hooks/use-consoles";
import { useToggleFavorite } from "@/hooks/use-games";
import { getConsoleStyle } from "@/lib/console-metadata";
import { cn } from "@/lib/cn";
import type { Game } from "@/types/api";

export function ConsoleDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: games, isLoading } = useConsoleGames(id ?? "");
  const { data: consoles } = useConsoles();
  const toggleFavorite = useToggleFavorite();

  // Find console info from the consoles list
  const console = consoles?.find((c) => c.id === id);
  function handleToggleFavorite(game: Game) {
    toggleFavorite.mutate({ gameId: game.id, isFavorite: game.isFavorite });
  }

  const consoleName = console?.name ?? "Console";
  const consoleAbbr = console?.abbreviation ?? id ?? "";
  const gameList = games ?? [];
  const style = getConsoleStyle(consoleAbbr);
  const Icon = style.icon;

  return (
    <div className="space-y-6">
      {/* Back button */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-sm text-surface-400 hover:text-surface-100 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Consoles
      </button>

      {/* Console header */}
      <div className="flex items-center gap-4">
        <div
          className={cn(
            "h-14 w-14 rounded-2xl bg-gradient-to-br flex items-center justify-center",
            style.gradient,
          )}
        >
          <Icon className="h-7 w-7 text-white" />
        </div>
        <div>
          <h1 className="text-3xl font-bold text-surface-100">{consoleName}</h1>
          <p className="text-surface-400">
            {gameList.length} {gameList.length === 1 ? "game" : "games"}
          </p>
        </div>
      </div>

      {/* Games grid */}
      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </div>
      ) : gameList.length === 0 ? (
        <EmptyState
          icon={Library}
          title="No games found"
          description="No games have been detected for this console yet."
        />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
          {gameList.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              onToggleFavorite={handleToggleFavorite}
            />
          ))}
        </div>
      )}
    </div>
  );
}
