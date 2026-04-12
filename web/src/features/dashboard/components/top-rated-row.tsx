import { Star } from "lucide-react";
import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { useTopRatedGlobal } from "@/hooks/use-top-lists";
import { ScrollShelf } from "@/components/scroll-shelf";
import { GameCardSkeleton } from "@/components/ui";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import { TopRatedGameCard } from "./top-rated-game-card";

function TopRatedSkeletonContent() {
  return (
    <div className="flex gap-4 overflow-hidden">
      {Array.from({ length: 6 }, (_, i) => (
        <GameCardSkeleton key={i} coverHeight={CAROUSEL_CARD_HEIGHT} />
      ))}
    </div>
  );
}

export function TopRatedRow() {
  const { data: games, isLoading } = useTopRatedGlobal();

  if (!isLoading && (!games || games.length === 0)) return null;

  return (
    <ScrollShelf
      title="Top Rated"
      icon={Star}
      testId="top-rated-row"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
      loadingSkeleton={<TopRatedSkeletonContent />}
      headerRight={
        <Link
          to="/top-lists"
          className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
        >
          View all
          <ChevronRight className="h-4 w-4" />
        </Link>
      }
    >
      {games?.map((game) => (
        <div key={`${game.rank}-${game.name}`} className="flex-shrink-0" role="listitem">
          <TopRatedGameCard game={game} coverHeight={CAROUSEL_CARD_HEIGHT} />
        </div>
      ))}
    </ScrollShelf>
  );
}
