import { Link } from "react-router-dom";
import { Trophy, Star } from "lucide-react";
import { Badge, Skeleton, EmptyState } from "@/components/ui";
import { useTopRated } from "@/hooks/use-top-lists";
import { cn } from "@/lib/cn";

function rankStyle(rank: number): { text: string; bg: string } {
  if (rank === 1)
    return {
      text: "text-amber-400",
      bg: "bg-amber-400/15 border border-amber-400/30",
    };
  if (rank === 2)
    return {
      text: "text-surface-300",
      bg: "bg-surface-300/15 border border-surface-300/30",
    };
  if (rank === 3)
    return {
      text: "text-amber-700",
      bg: "bg-amber-700/15 border border-amber-700/30",
    };
  return { text: "text-surface-500", bg: "" };
}

function RankBadge({ rank }: { rank: number }) {
  const style = rankStyle(rank);
  const isTop3 = rank <= 3;

  if (isTop3) {
    return (
      <span
        className={cn(
          "inline-flex items-center justify-center h-7 w-7 rounded-full text-xs font-bold",
          style.bg,
          style.text,
        )}
      >
        {rank}
      </span>
    );
  }

  return (
    <span className="inline-flex items-center justify-center h-7 w-7 text-sm font-medium text-surface-500">
      {rank}
    </span>
  );
}

function TopRatedSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 10 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-10 w-8 rounded-lg" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-20" />
          </div>
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}

export function TopListsPage() {
  const { data: topRated, isLoading } = useTopRated();

  const games = topRated ?? [];

  return (
    <div className="max-w-5xl space-y-10">
      <div>
        <h1 className="text-3xl font-bold text-surface-100 flex items-center gap-3">
          <Trophy className="h-8 w-8 text-brand-400" />
          Top Lists
        </h1>
        <p className="mt-1 text-surface-400">
          The highest rated games across your library.
        </p>
      </div>

      <section>
        <div className="flex items-center gap-2.5 mb-5">
          <Star className="h-5 w-5 text-brand-400" />
          <h2 className="text-xl font-bold text-surface-100">
            Top Rated Games
          </h2>
        </div>

        {isLoading ? (
          <TopRatedSkeleton />
        ) : games.length === 0 ? (
          <EmptyState
            icon={Star}
            title="No top rated games yet"
            description="Top rated games from your library will appear here."
          />
        ) : (
          <div className="space-y-2">
            {games.map((game) => (
              <Link
                key={game.gameId}
                to={`/games/${game.gameId}`}
                className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
              >
                <RankBadge rank={game.rank} />
                <div className="h-10 w-8 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
                  {game.coverUrl ? (
                    <img
                      src={game.coverUrl}
                      alt={game.name}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="h-full w-full flex items-center justify-center text-xs font-bold text-surface-600">
                      {game.name.charAt(0)}
                    </div>
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-200 truncate">
                    {game.name}
                  </p>
                  {game.consoleName && (
                    <Badge variant="brand" className="mt-0.5">
                      {game.consoleName}
                    </Badge>
                  )}
                </div>
                <span className="flex items-center gap-1 text-sm font-mono text-amber-400 whitespace-nowrap">
                  <Star className="h-3.5 w-3.5 fill-amber-400" />
                  {game.rating.toFixed(1)}
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
