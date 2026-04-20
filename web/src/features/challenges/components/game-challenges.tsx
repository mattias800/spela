import { Link } from "react-router-dom";
import { Flag, ChevronRight, Users } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { ChallengeDifficultyBadge } from "@/features/challenges/components/challenge-difficulty-badge";
import { ChallengeTypeIcon } from "@/features/challenges/components/challenge-type-icon";
import { useGameChallenges } from "@/hooks/use-challenges";
import { formatRelativeTime } from "@/lib/format";

interface GameChallengesProps {
  gameId: string;
}

function ChallengesSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 3 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30"
        >
          <Skeleton className="h-10 w-16 rounded-lg flex-shrink-0" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-56" />
          </div>
          <Skeleton className="h-5 w-16" />
        </div>
      ))}
    </div>
  );
}

export function GameChallenges({ gameId }: GameChallengesProps) {
  const { data, isLoading } = useGameChallenges(gameId);

  return (
    <section data-testid="game-challenges">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2.5">
          <Flag className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Challenges</h2>
          {data && data.total > 0 && (
            <span className="text-sm text-surface-500">({data.total})</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {data && data.total > 5 && (
            <Link
              to={`/challenges?gameId=${gameId}`}
              className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
            >
              View all
              <ChevronRight className="h-4 w-4" />
            </Link>
          )}
        </div>
      </div>

      {isLoading && <ChallengesSkeleton />}

      {!isLoading && (!data?.data || data.data.length === 0) && (
        <EmptyState
          icon={Flag}
          title="No challenges yet"
          description="Try a challenge in the Spela player app!"
          className="py-8"
        />
      )}

      {data?.data && data.data.length > 0 && (
        <div className="space-y-2">
          {data.data.map((challenge) => (
            <Link
              key={challenge.id}
              to={`/challenges/${challenge.id}`}
              className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors"
              data-testid={`game-challenge-${challenge.id}`}
            >
              {/* Screenshot thumbnail */}
              <div className="h-10 w-16 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
                {challenge.screenshotUrl ? (
                  <img
                    src={challenge.screenshotUrl}
                    alt={challenge.name}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <div className="h-full w-full flex items-center justify-center">
                    <ChallengeTypeIcon
                      type={challenge.type}
                      className="h-4 w-4 text-surface-600"
                    />
                  </div>
                )}
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-surface-200 truncate">
                    {challenge.name}
                  </p>
                  <ChallengeDifficultyBadge
                    difficulty={challenge.difficulty}
                  />
                </div>
                <div className="flex items-center gap-2 mt-0.5">
                  <PlayerAvatar
                    username={challenge.creatorUsername}
                    avatarUrl={challenge.creatorAvatar}
                    size="xs"
                  />
                  <span className="text-xs text-surface-500">
                    {challenge.creatorUsername}
                  </span>
                  <span className="text-xs text-surface-500">
                    &middot; {formatRelativeTime(challenge.createdAt)}
                  </span>
                </div>
              </div>

              {/* Attempt count */}
              <div className="flex items-center gap-1.5 text-xs text-surface-400 whitespace-nowrap">
                <Users className="h-3.5 w-3.5" />
                {challenge.attemptCount}
              </div>

              <ChallengeTypeIcon type={challenge.type} />
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
