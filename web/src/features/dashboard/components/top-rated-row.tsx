import { Star } from "lucide-react";
import { Skeleton, TitledSection } from "@/components/ui";
import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { useTopRatedGlobal } from "@/hooks/use-top-lists";
import { TopRatedGameCard } from "./top-rated-game-card";

function TopRatedSkeleton() {
  return (
    <div className="flex gap-4 overflow-x-auto pb-2">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i} className="flex-shrink-0 w-36 space-y-2">
          <Skeleton className="aspect-[3/4] w-full rounded-xl" />
          <Skeleton className="h-4 w-28" />
          <Skeleton className="h-3 w-20" />
        </div>
      ))}
    </div>
  );
}

export function TopRatedRow() {
  const { data: games, isLoading } = useTopRatedGlobal();

  if (!isLoading && (!games || games.length === 0)) return null;

  return (
    <TitledSection
      title="Top Rated"
      icon={Star}
      renderRight={
        <Link
          to="/top-lists"
          className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
        >
          View all
          <ChevronRight className="h-4 w-4" />
        </Link>
      }
    >
      {isLoading ? (
        <TopRatedSkeleton />
      ) : (
        <div className="flex gap-4 overflow-x-auto pb-2">
          {games?.map((game) => (
            <div key={`${game.rank}-${game.name}`} className="flex-shrink-0">
              <TopRatedGameCard game={game} />
            </div>
          ))}
        </div>
      )}
    </TitledSection>
  );
}
