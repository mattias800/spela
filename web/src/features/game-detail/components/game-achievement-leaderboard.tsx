import { Trophy } from "lucide-react";
import { Badge, Card, Skeleton, LeaderboardSkeleton, LeaderboardRow } from "@/components/ui";
import { useAchievementLeaderboard } from "@/hooks/use-retroachievements";
import { useAuth } from "@/hooks/use-auth";
import { cn } from "@/lib/cn";
import { rankColor } from "@/lib/rank-utils";

interface GameAchievementLeaderboardProps {
  gameId: string;
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

export function GameAchievementLeaderboard({
  gameId,
}: GameAchievementLeaderboardProps) {
  const { data: leaderboard, isLoading } = useAchievementLeaderboard(gameId);
  const { user } = useAuth();

  if (isLoading) {
    return (
      <Card className="p-6">
        <div className="flex items-center gap-2.5 mb-5">
          <Skeleton className="h-5 w-5" />
          <Skeleton className="h-6 w-48" />
        </div>
        <LeaderboardSkeleton />
      </Card>
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
    <Card className="p-6" data-testid="achievement-leaderboard">
      <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2.5 mb-5">
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
            <LeaderboardRow
              key={entry.userId}
              data-testid={`leaderboard-entry-${entry.userId}`}
              isCurrentUser={isCurrentUser}
              username={entry.username}
              avatarUrl={entry.avatarUrl}
              rank={<RankBadge rank={rank} />}
            >
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
            </LeaderboardRow>
          );
        })}
      </div>
    </Card>
  );
}
