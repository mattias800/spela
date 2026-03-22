import { Star } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { SectionHeader } from "@/components/section-header";
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
    <section>
      <SectionHeader title="Top Rated" icon={Star} linkTo="/top-lists" />
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
    </section>
  );
}
