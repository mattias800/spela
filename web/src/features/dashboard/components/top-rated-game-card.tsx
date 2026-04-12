import { Star } from "lucide-react";
import { CoverCard } from "@/components/cover-card";
import type { TopRatedGame } from "@/types/api";

/**
 * ROLE component — a top-rated game card with library availability.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Maps TopRatedGame domain data to CoverCard. Dimmed when the game
 * is not available in the local library.
 */
export function TopRatedGameCard({ game }: { game: TopRatedGame }) {
  const isAvailable = game.localGameId != null;

  return (
    <CoverCard
      imageUrl={game.coverUrl}
      title={game.name}
      subtitle={game.consoleName}
      linkTo={isAvailable ? `/games/${game.localGameId}` : undefined}
    >
      <span className="flex items-center gap-0.5 text-xs text-amber-400">
        <Star className="h-3 w-3 fill-amber-400" />
        {game.igdbCriticsRating.toFixed(0)}
      </span>
      {!isAvailable && (
        <span className="text-xs text-surface-500">Not in library</span>
      )}
    </CoverCard>
  );
}
