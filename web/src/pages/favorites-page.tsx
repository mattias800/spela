import { Heart } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useFavoriteGames, useToggleFavorite } from "@/hooks/use-games";

export function FavoritesPage() {
  const { data: games, isLoading } = useFavoriteGames();
  const { toggle: handleToggleFavorite } = useToggleFavorite();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Favorites</h1>
        <p className="mt-1 text-surface-400">
          Your favorite games, all in one place.
        </p>
      </div>

      {isLoading ? (
        <GameGrid>
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </GameGrid>
      ) : !games || games.length === 0 ? (
        <EmptyState
          icon={Heart}
          title="No favorites yet"
          description="Click the heart icon on any game to add it to your favorites."
        />
      ) : (
        <GameGrid>
          {games.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              onToggleFavorite={handleToggleFavorite}
            />
          ))}
        </GameGrid>
      )}
    </div>
  );
}
