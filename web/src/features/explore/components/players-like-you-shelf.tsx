import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type { Game, GameSummary } from "@/types/api";

interface PlayersLikeYouShelfProps {
  games: GameSummary[] | undefined;
  isLoading: boolean;
  similarUsersCount: number;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function PlayersLikeYouShelf({
  games,
  isLoading,
  similarUsersCount,
  onToggleFavorite,
  onTogglePlayLater,
}: PlayersLikeYouShelfProps) {
  const subtitle =
    similarUsersCount > 0
      ? `Based on ${similarUsersCount} player${similarUsersCount !== 1 ? "s" : ""} with similar taste`
      : undefined;

  return (
    <ScrollShelf
      title="Players like you also enjoyed"
      subtitle={subtitle}
      testId="players-like-you-shelf"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((game) => (
        <div key={game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}
