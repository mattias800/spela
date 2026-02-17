import { Trophy } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { RankBadge } from "@/components/challenges/rank-badge";
import {
  useChallengeLeaderboard,
  useChallengeLeaderboardRealtime,
} from "@/hooks/use-challenges";
import { useAuth } from "@/hooks/use-auth";
import { formatChallengeDuration, formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/cn";

interface ChallengeLeaderboardProps {
  challengeId: string;
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
          <Skeleton className="h-4 w-28" />
          <div className="flex-1" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}

export function ChallengeLeaderboard({
  challengeId,
}: ChallengeLeaderboardProps) {
  const { data, isLoading } = useChallengeLeaderboard(challengeId);
  const { user } = useAuth();
  useChallengeLeaderboardRealtime(challengeId);

  if (isLoading) {
    return (
      <section>
        <div className="flex items-center gap-2.5 mb-5">
          <Skeleton className="h-5 w-5" />
          <Skeleton className="h-6 w-36" />
        </div>
        <LeaderboardSkeleton />
      </section>
    );
  }

  return (
    <section data-testid="challenge-leaderboard">
      <h2 className="text-xl font-bold text-surface-100 flex items-center gap-2.5 mb-5">
        <Trophy className="h-5 w-5 text-brand-400" />
        Leaderboard
        {data && data.total > 0 && (
          <span className="text-sm font-normal text-surface-500">
            ({data.total})
          </span>
        )}
      </h2>

      {!data?.data || data.data.length === 0 ? (
        <EmptyState
          icon={Trophy}
          title="No attempts yet. Be the first!"
          description="Complete this challenge to claim the top spot on the leaderboard."
          className="py-8"
        />
      ) : (
        <div className="space-y-2">
          {data.data.map((entry) => {
            const isCurrentUser = entry.userId === user?.id;
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
                <RankBadge rank={entry.rank} />
                <PlayerAvatar
                  username={entry.username}
                  avatarUrl={entry.avatarUrl}
                />
                <span
                  className={cn(
                    "text-sm font-medium truncate flex-1 min-w-0",
                    isCurrentUser ? "text-brand-400" : "text-surface-200",
                  )}
                >
                  {entry.username}
                </span>
                <span className="text-sm font-mono text-surface-300 whitespace-nowrap">
                  {formatChallengeDuration(entry.durationMs)}
                </span>
                <span className="text-xs text-surface-500 whitespace-nowrap">
                  {formatRelativeTime(entry.completedAt)}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
