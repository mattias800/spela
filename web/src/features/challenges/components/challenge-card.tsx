import { Link } from "react-router-dom";
import { Users } from "lucide-react";
import { Badge } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { ChallengeDifficultyBadge } from "@/features/challenges/components/challenge-difficulty-badge";
import { ChallengeTypeIcon } from "@/features/challenges/components/challenge-type-icon";
import { formatRelativeTime } from "@/lib/format";
import type { Challenge } from "@/types/api";

interface ChallengeCardProps {
  challenge: Challenge;
}

export function ChallengeCard({ challenge }: ChallengeCardProps) {
  return (
    <Link
      to={`/challenges/${challenge.id}`}
      className="group block space-y-3"
      data-testid={`challenge-card-${challenge.id}`}
      aria-label={`${challenge.name}, ${challenge.difficulty} ${challenge.type} challenge by ${challenge.creatorUsername}`}
    >
      {/* Screenshot */}
      <div className="relative aspect-[16/10] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1">
        {challenge.screenshotUrl ? (
          <img
            src={challenge.screenshotUrl}
            alt={challenge.name}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
            <ChallengeTypeIcon
              type={challenge.type}
              className="h-12 w-12 text-surface-700"
            />
          </div>
        )}

        {/* Hover overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

        {/* Top badges */}
        <div className="absolute top-2.5 right-2.5 flex items-center gap-1.5">
          <ChallengeDifficultyBadge difficulty={challenge.difficulty} />
        </div>

        {/* Bottom info on hover */}
        <div className="absolute bottom-2.5 left-2.5 right-2.5 flex items-center justify-between opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <Badge variant="brand">
            <Users className="h-3 w-3 mr-1" />
            {challenge.attemptCount}{" "}
            {challenge.attemptCount === 1 ? "attempt" : "attempts"}
          </Badge>
          <ChallengeTypeIcon type={challenge.type} showLabel />
        </div>
      </div>

      {/* Info */}
      <div className="px-1 space-y-1.5">
        <h3 className="text-sm font-semibold text-surface-100 truncate group-hover:text-brand-400 transition-colors">
          {challenge.name}
        </h3>
        <div className="flex items-center gap-2">
          <PlayerAvatar
            username={challenge.creatorUsername}
            avatarUrl={challenge.creatorAvatar}
            size="xs"
          />
          <span className="text-xs text-surface-400 truncate">
            {challenge.creatorUsername}
          </span>
          <span className="text-xs text-surface-500">
            &middot; {formatRelativeTime(challenge.createdAt)}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="brand" className="text-[10px] px-1.5 py-0">
            {challenge.consoleName}
          </Badge>
          <span className="text-xs text-surface-500 truncate">
            {challenge.gameTitle}
          </span>
        </div>
      </div>
    </Link>
  );
}
