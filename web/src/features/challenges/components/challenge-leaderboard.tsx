import { Trophy } from "lucide-react";
import { Skeleton, EmptyState, LeaderboardSkeleton, LeaderboardRow } from "@/components/ui";
import { RankBadge } from "@/features/challenges/components/rank-badge";
import {
  useChallengeLeaderboard,
  useChallengeLeaderboardRealtime,
} from "@/hooks/use-challenges";
import { useAuth } from "@/hooks/use-auth";
import { formatChallengeDuration, formatRelativeTime } from "@/lib/format";

interface ChallengeLeaderboardProps {
  challengeId: string;
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
              <LeaderboardRow
                key={entry.userId}
                data-testid={`leaderboard-entry-${entry.userId}`}
                isCurrentUser={isCurrentUser}
                username={entry.username}
                avatarUrl={entry.avatarUrl}
                rank={<RankBadge rank={entry.rank} />}
                usernameClassName="flex-1 min-w-0"
              >
                <span className="text-sm font-mono text-surface-300 whitespace-nowrap">
                  {formatChallengeDuration(entry.durationMs)}
                </span>
                <span className="text-xs text-surface-500 whitespace-nowrap">
                  {formatRelativeTime(entry.completedAt)}
                </span>
              </LeaderboardRow>
            );
          })}
        </div>
      )}
    </section>
  );
}
