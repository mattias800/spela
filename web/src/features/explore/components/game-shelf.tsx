import type { LucideIcon } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type { Game } from "@/types/api";

interface GameShelfProps {
  title: string;
  icon?: LucideIcon;
  games: Game[] | undefined;
  isLoading: boolean;
  hideConsoleName?: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function GameShelf({
  title,
  icon,
  games,
  isLoading,
  hideConsoleName,
  onToggleFavorite,
  onTogglePlayLater,
}: GameShelfProps) {
  return (
    <ScrollShelf
      title={title}
      icon={icon}
      testId={`shelf-${title}`}
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((game) => (
        <div key={game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge={!hideConsoleName}
            hideConsoleName={hideConsoleName}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}
