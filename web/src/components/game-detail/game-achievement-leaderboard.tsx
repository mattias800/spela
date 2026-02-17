import { Trophy } from "lucide-react";
import { Badge, Skeleton } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { useAchievementLeaderboard } from "@/hooks/use-retroachievements";
import { useAuth } from "@/hooks/use-auth";
import { cn } from "@/lib/cn";

interface GameAchievementLeaderboardProps {
  gameId: string;
}

function rankColor(rank: number): string {
  if (rank === 1) return "text-amber-400";
  if (rank === 2) return "text-surface-300";
  if (rank === 3) return "text-amber-700";
  return "text-surface-500";
}

function rankBg(rank: number): string {
  if (rank === 1) return "bg-amber-400/15 border border-amber-400/30";
  if (rank === 2) return "bg-surface-300/15 border border-surface-300/30";
  if (rank === 3) return "bg-amber-700/15 border border-amber-700/30";
  return "";
}

function RankBadge({ rank }: { rank: number }) {
  const isTop3 = rank <= 3;

  if (isTop3) {
    return (
      <span
        className={cn(
          "inline-flex items-center justify-center h-7 w-7 rounded-full text-xs font-bold",
          rankBg(rank),
          rankColor(rank),
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

function LeaderboardSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 5 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-3 rounded-xl px-3 py-2.5 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-8 w-8 rounded-full" />
          <Skeleton className="h-4 w-28 flex-shrink-0" />
          <Skeleton className="h-3 flex-1" />
          <Skeleton className="h-4 w-14" />
        </div>
      ))}
    </div>
  );
}

export function GameAchievementLeaderboard({
  gameId,
}: GameAchievementLeaderboardProps) {
  const { data: leaderboard, isLoading } = useAchievementLeaderboard(gameId);
  const { user } = useAuth();

  if (isLoading) {
    return (
      <section>
        <div className="flex items-center gap-2.5 mb-5">
          <Skeleton className="h-5 w-5" />
          <Skeleton className="h-6 w-48" />
        </div>
        <LeaderboardSkeleton />
      </section>
    );
  }

  if (
    !leaderboard ||
    !leaderboard.leaderboard ||
    leaderboard.leaderboard.length === 0 ||
    leaderboard.totalAchievements === 0
  ) {
    return null;
  }

  const maxUnlocked = Math.max(
    ...leaderboard.leaderboard.map((e) => e.unlockedCount),
  );

  return (
    <section data-testid="achievement-leaderboard">
      <h2 className="text-xl font-bold text-surface-100 flex items-center gap-2.5 mb-5">
        <Trophy className="h-5 w-5 text-brand-400" />
        Achievement Leaderboard
      </h2>

      <div className="space-y-2">
        {leaderboard.leaderboard.map((entry, index) => {
          const rank = index + 1;
          const isCurrentUser = entry.userId === user?.id;
          const barWidth =
            maxUnlocked > 0
              ? (entry.unlockedCount / leaderboard.totalAchievements) * 100
              : 0;

          return (
            <div
              key={entry.userId}
              data-testid={`leaderboard-entry-${entry.userId}`}
              className={cn(
                "flex items-center gap-3 rounded-xl px-3 py-2.5",
                isCurrentUser
                  ? "bg-brand-500/5 border-l-2 border-brand-400"
                  : "bg-surface-900/50",
              )}
            >
              <RankBadge rank={rank} />
              <PlayerAvatar
                username={entry.username}
                avatarUrl={entry.avatarUrl}
              />
              <span
                className={cn(
                  "text-sm font-medium flex-shrink-0 w-28 truncate",
                  isCurrentUser ? "text-brand-400" : "text-surface-200",
                )}
              >
                {entry.username}
              </span>
              <span className="text-xs text-surface-500 flex-shrink-0">
                {entry.unlockedCount} / {leaderboard.totalAchievements}
              </span>
              <span className="text-sm font-mono text-surface-400 flex-shrink-0">
                {entry.earnedPoints} pts
              </span>
              <div className="flex-1 h-3 rounded-full bg-brand-500/15 overflow-hidden">
                <div
                  className={cn(
                    "h-full rounded-full transition-all duration-700 ease-out",
                    entry.isComplete ? "bg-amber-400" : "bg-brand-500",
                  )}
                  style={{ width: `${barWidth}%` }}
                />
              </div>
              {entry.isComplete && (
                <Badge variant="warning" className="flex-shrink-0">
                  Complete!
                </Badge>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}
