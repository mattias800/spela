import { Heart } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useFavoriteGames, useToggleFavorite } from "@/hooks/use-games";
import type { Game } from "@/types/api";

export function FavoritesPage() {
  const { data: games, isLoading } = useFavoriteGames();
  const toggleFavorite = useToggleFavorite();

  function handleToggleFavorite(game: Game) {
    toggleFavorite.mutate({ gameId: game.id, isFavorite: true });
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Favorites</h1>
        <p className="mt-1 text-surface-400">
          Your favorite games, all in one place.
        </p>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </div>
      ) : !games || games.length === 0 ? (
        <EmptyState
          icon={Heart}
          title="No favorites yet"
          description="Click the heart icon on any game to add it to your favorites."
        />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
          {games.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              isFavorite
              onToggleFavorite={handleToggleFavorite}
            />
          ))}
        </div>
      )}
    </div>
  );
}
