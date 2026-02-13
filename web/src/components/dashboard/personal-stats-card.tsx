import { Link } from "react-router-dom";
import { Zap, Trophy } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { useUserStats } from "@/hooks/use-stats";
import { formatPlayTime } from "@/lib/format";
import { cn } from "@/lib/cn";

function PersonalStatsSkeleton() {
  return (
    <div className="bg-surface-800/50 rounded-2xl p-5">
      <div className="flex items-center gap-2.5 mb-4">
        <Skeleton className="h-5 w-5" />
        <Skeleton className="h-5 w-32" />
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="bg-surface-800/30 rounded-xl p-4">
            <Skeleton className="h-7 w-16 mb-1" />
            <Skeleton className="h-3 w-20" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function PersonalStatsCard() {
  const { data: stats, isLoading } = useUserStats();

  if (isLoading) {
    return <PersonalStatsSkeleton />;
  }

  if (!stats || stats.gamesPlayed === 0) {
    return null;
  }

  const streakHighlight = stats.currentStreak >= 3;

  return (
    <div className="bg-surface-800/50 rounded-2xl p-5">
      <div className="flex items-center gap-2.5 mb-4">
        <Trophy className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-bold text-surface-100">Your Stats</h2>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {/* Total play time */}
        <div className="bg-surface-800/30 rounded-xl p-4">
          <p className="text-2xl font-mono font-bold text-brand-400">
            {formatPlayTime(stats.totalPlayTime)}
          </p>
          <p className="text-xs text-surface-500 mt-0.5">Total play time</p>
        </div>

        {/* Games played */}
        <div className="bg-surface-800/30 rounded-xl p-4">
          <p className="text-2xl font-mono font-bold text-surface-100">
            {stats.gamesPlayed}
          </p>
          <p className="text-xs text-surface-500 mt-0.5">Games played</p>
        </div>

        {/* Current streak */}
        <div className="bg-surface-800/30 rounded-xl p-4">
          <p
            className={cn(
              "text-2xl font-mono font-bold flex items-center gap-1.5",
              streakHighlight ? "text-amber-400" : "text-surface-100",
            )}
          >
            {stats.currentStreak}d
            {streakHighlight && <Zap className="h-5 w-5 text-amber-400" />}
          </p>
          <p className="text-xs text-surface-500 mt-0.5">Current streak</p>
        </div>

        {/* Most played game */}
        {stats.mostPlayedGame ? (
          <Link
            to={`/games/${stats.mostPlayedGame.id}`}
            className="bg-surface-800/30 rounded-xl p-4 hover:bg-surface-800/50 transition-colors"
          >
            <p className="text-sm font-bold text-surface-100 truncate">
              {stats.mostPlayedGame.title}
            </p>
            <p className="text-xs font-mono text-surface-400 mt-0.5">
              {formatPlayTime(stats.mostPlayedGameTime)}
            </p>
            <p className="text-xs text-surface-500 mt-0.5">Most played</p>
          </Link>
        ) : (
          <div className="bg-surface-800/30 rounded-xl p-4">
            <p className="text-2xl font-mono font-bold text-surface-100">--</p>
            <p className="text-xs text-surface-500 mt-0.5">Most played</p>
          </div>
        )}
      </div>
    </div>
  );
}
