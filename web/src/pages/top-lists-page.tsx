import { useState } from "react";
import { Link } from "react-router-dom";
import { Trophy, Star, Clock, Gamepad2 } from "lucide-react";
import { Badge, Skeleton, EmptyState } from "@/components/ui";
import { useTopRated, useLongestGames } from "@/hooks/use-top-lists";
import { PlayInfo } from "@/components/play-info";
import { cn } from "@/lib/cn";
import type { TopListGame, LongestGame } from "@/types/api";

type TabId = "top-rated" | "longest";

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

function ListSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 10 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-16 w-14 rounded-lg" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-4 w-20" />
          </div>
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}

function GameCover({ coverUrl, name }: { coverUrl?: string; name: string }) {
  return (
    <div className="w-14 h-16 flex items-center justify-center flex-shrink-0">
      {coverUrl ? (
        <img
          src={coverUrl}
          alt={name}
          className="max-h-16 max-w-14 rounded-lg object-contain"
        />
      ) : (
        <div className="h-16 w-12 rounded-lg bg-surface-800 flex items-center justify-center text-sm font-bold text-surface-600">
          {name.charAt(0)}
        </div>
      )}
    </div>
  );
}

function TopRatedList({ games }: { games: TopListGame[] }) {
  if (games.length === 0) {
    return (
      <EmptyState
        icon={Star}
        title="No top rated games yet"
        description="Top rated games from your library will appear here."
      />
    );
  }

  return (
    <div className="space-y-2">
      {games.map((game) => (
        <Link
          key={game.gameId}
          to={`/games/${game.gameId}`}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
        >
          <RankBadge rank={game.rank} />
          <GameCover coverUrl={game.coverUrl} name={game.name} />
          <div className="flex-1 min-w-0">
            <p className="text-base font-semibold text-surface-200 truncate">
              {game.name}
            </p>
            <div className="mt-1.5 flex items-center gap-2">
              {game.consoleName && (
                <Badge variant="brand">{game.consoleName}</Badge>
              )}
            </div>
            <PlayInfo gameId={game.gameId} className="mt-1.5" />
          </div>
          <span className="flex items-center gap-1 text-sm font-mono text-amber-400 whitespace-nowrap">
            <Star className="h-3.5 w-3.5 fill-amber-400" />
            {game.rating.toFixed(1)}
          </span>
        </Link>
      ))}
    </div>
  );
}

function formatTimeToBeat(hours: number): string {
  if (hours < 1) return "<1 hr";
  if (hours === 1) return "1 hr";
  if (hours === Math.floor(hours)) return `${hours} hrs`;
  return `${hours.toFixed(1)} hrs`;
}

function LongestGamesList({ games }: { games: LongestGame[] }) {
  if (games.length === 0) {
    return (
      <EmptyState
        icon={Clock}
        title="No longest games yet"
        description="Games with time-to-beat data from your library will appear here."
      />
    );
  }

  return (
    <div className="space-y-2">
      {games.map((game) => (
        <Link
          key={game.gameId}
          to={`/games/${game.gameId}`}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
        >
          <RankBadge rank={game.rank} />
          <GameCover coverUrl={game.coverUrl} name={game.name} />
          <div className="flex-1 min-w-0">
            <p className="text-base font-semibold text-surface-200 truncate">
              {game.name}
            </p>
            <div className="mt-1.5 flex items-center gap-2">
              {game.consoleName && (
                <Badge variant="brand">{game.consoleName}</Badge>
              )}
            </div>
            <PlayInfo gameId={game.gameId} className="mt-1.5" />
          </div>
          <div className="text-right flex-shrink-0">
            <span className="flex items-center gap-1 text-sm font-mono text-brand-300 whitespace-nowrap">
              <Gamepad2 className="h-3.5 w-3.5" />
              {formatTimeToBeat(game.timeToBeatNormally)}
            </span>
            <div className="mt-0.5 text-xs text-surface-500 whitespace-nowrap">
              {game.timeToBeatHastily > 0 && (
                <span>Main {formatTimeToBeat(game.timeToBeatHastily)}</span>
              )}
              {game.timeToBeatHastily > 0 && game.timeToBeatCompletely > 0 && (
                <span> · </span>
              )}
              {game.timeToBeatCompletely > 0 && (
                <span>100% {formatTimeToBeat(game.timeToBeatCompletely)}</span>
              )}
            </div>
          </div>
        </Link>
      ))}
    </div>
  );
}

export function TopListsPage() {
  const [activeTab, setActiveTab] = useState<TabId>("top-rated");
  const { data: topRated, isLoading: isLoadingTopRated } = useTopRated();
  const { data: longestGames, isLoading: isLoadingLongest } = useLongestGames();

  const topRatedGames = topRated ?? [];
  const longestGamesList = longestGames ?? [];

  const tabs: { id: TabId; label: string; icon: typeof Star }[] = [
    { id: "top-rated", label: "Top Rated", icon: Star },
    { id: "longest", label: "Longest Games", icon: Clock },
  ];

  return (
    <div className="max-w-5xl space-y-10">
      <div>
        <h1 className="text-3xl font-bold text-surface-100 flex items-center gap-3">
          <Trophy className="h-8 w-8 text-brand-400" />
          Top Lists
        </h1>
        <p className="mt-1 text-surface-400">
          Discover the best and biggest games in your library.
        </p>
      </div>

      <div
        className="flex gap-1 p-1 rounded-xl bg-surface-900/60 border border-surface-800/50 w-fit"
        role="tablist"
      >
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActive}
              onClick={() => setActiveTab(tab.id)}
              className={cn(
                "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer",
                isActive
                  ? "bg-surface-800 text-surface-100 shadow-sm"
                  : "text-surface-400 hover:text-surface-200 hover:bg-surface-800/50",
              )}
            >
              <Icon className="h-4 w-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === "top-rated" && (
        <section>
          <div className="flex items-center gap-2.5 mb-5">
            <Star className="h-5 w-5 text-brand-400" />
            <h2 className="text-xl font-bold text-surface-100">
              Top Rated Games
            </h2>
          </div>
          {isLoadingTopRated ? (
            <ListSkeleton />
          ) : (
            <TopRatedList games={topRatedGames} />
          )}
        </section>
      )}

      {activeTab === "longest" && (
        <section>
          <div className="flex items-center gap-2.5 mb-5">
            <Clock className="h-5 w-5 text-brand-400" />
            <h2 className="text-xl font-bold text-surface-100">
              Longest Games
            </h2>
          </div>
          <p className="text-sm text-surface-400 mb-5">
            The biggest time investments in your library, ranked by main + extras completion time.
          </p>
          {isLoadingLongest ? (
            <ListSkeleton />
          ) : (
            <LongestGamesList games={longestGamesList} />
          )}
        </section>
      )}
    </div>
  );
}
