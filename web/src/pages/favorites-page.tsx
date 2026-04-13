import { Heart } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useFavoriteGames, useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { PageLayout, SectionList } from "@/components/layout";

export function FavoritesPage() {
  const { data: games, isLoading } = useFavoriteGames();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  return (
    <PageLayout title="Favorites" subtitle="Your favorite games, all in one place.">
      <SectionList>
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
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </GameGrid>
      )}
    </SectionList>
    </PageLayout>
  );
}
