import { Link } from "react-router-dom";
import { Users, Repeat } from "lucide-react";
import { Badge } from "@/components/ui";
import { MemberAvatars } from "@/features/relays/components/member-avatars";
import { relayStatusVariant } from "@/features/relays/components/relay-status";
import { formatRelativeTime } from "@/lib/format";
import type { Relay } from "@/types/api";

interface RelayCardProps {
  relay: Relay;
  members?: { username: string; avatarUrl?: string }[];
}

export function RelayCard({ relay, members }: RelayCardProps) {
  return (
    <Link
      to={`/relays/${relay.id}`}
      className="group block space-y-3"
      data-testid={`relay-card-${relay.id}`}
    >
      <div className="relative aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1">
        {relay.gameCoverUrl ? (
          <img
            src={relay.gameCoverUrl}
            alt={relay.gameTitle}
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
          <Badge variant={relayStatusVariant[relay.status]} className="capitalize">
            {relay.status}
          </Badge>
        </div>

        {/* Member count */}
        <div className="absolute bottom-2.5 left-2.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <Badge variant="brand">
            <Users className="h-3 w-3 mr-1" />
            {relay.memberCount}{" "}
            {relay.memberCount === 1 ? "player" : "players"}
          </Badge>
        </div>
      </div>

      <div className="px-1 space-y-2">
        <div className="space-y-1">
          <h3 className="text-sm font-semibold text-surface-100 truncate group-hover:text-brand-400 transition-colors">
            {relay.name}
          </h3>
          <p className="text-xs text-surface-500 truncate">
            {relay.gameTitle} &middot; {relay.gameConsoleName}
          </p>
        </div>
        <div className="flex items-center justify-between">
          {members && members.length > 0 && (
            <MemberAvatars members={members} max={3} />
          )}
          <span className="text-xs text-surface-500">
            {formatRelativeTime(relay.lastActivityAt)}
          </span>
        </div>
      </div>
    </Link>
  );
}
