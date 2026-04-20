import { Link } from "react-router-dom";
import { Users, Repeat } from "lucide-react";
import { Badge } from "@/components/ui";
import { MemberAvatars } from "@/features/shared-sessions/components/member-avatars";
import { sharedSessionStatusVariant } from "@/features/shared-sessions/components/shared-session-status";
import { formatRelativeTime } from "@/lib/format";
import type { SharedSession } from "@/types/api";

interface SharedSessionCardProps {
  sharedSession: SharedSession;
  members?: { username: string; avatarUrl: string }[];
}

export function SharedSessionCard({ sharedSession, members }: SharedSessionCardProps) {
  return (
    <Link
      to={`/shared-sessions/${sharedSession.id}`}
      className="group block space-y-3"
      data-testid={`shared-session-card-${sharedSession.id}`}
    >
      <div className="relative aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1">
        {sharedSession.gameCoverUrl ? (
          <img
            src={sharedSession.gameCoverUrl}
            alt={sharedSession.gameTitle}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
            <Repeat className="h-12 w-12 text-surface-700" />
          </div>
        )}

        {/* Hover overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

        {/* Status badge */}
        <div className="absolute top-2.5 right-2.5">
          <Badge variant={sharedSessionStatusVariant[sharedSession.status]} className="capitalize">
            {sharedSession.status}
          </Badge>
        </div>

        {/* Member count */}
        <div className="absolute bottom-2.5 left-2.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <Badge variant="brand">
            <Users className="h-3 w-3 mr-1" />
            {sharedSession.memberCount}{" "}
            {sharedSession.memberCount === 1 ? "player" : "players"}
          </Badge>
        </div>
      </div>

      <div className="px-1 space-y-2">
        <div className="space-y-1">
          <h3 className="text-sm font-semibold text-surface-100 truncate group-hover:text-brand-400 transition-colors">
            {sharedSession.name}
          </h3>
          <p className="text-xs text-surface-500 truncate">
            {sharedSession.gameTitle} &middot; {sharedSession.consoleName}
          </p>
        </div>
        <div className="flex items-center justify-between">
          {members && members.length > 0 && (
            <MemberAvatars members={members} max={3} />
          )}
          <span className="text-xs text-surface-500">
            {formatRelativeTime(sharedSession.updatedAt)}
          </span>
        </div>
      </div>
    </Link>
  );
}
