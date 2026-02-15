import { Play, LogOut, Trash2, Repeat } from "lucide-react";
import { Button, Badge } from "@/components/ui";
import { MemberAvatars } from "@/components/relays/member-avatars";
import { relayStatusVariant } from "@/components/relays/relay-status";
import { formatRelativeTime } from "@/lib/format";
import type { RelayDetail } from "@/types/api";

interface RelayHeroProps {
  relay: RelayDetail;
  isOwner: boolean;
  canPlayInBrowser: boolean;
  onPlay: () => void;
  onLeave: () => void;
  onDelete: () => void;
}

export function RelayHero({
  relay,
  isOwner,
  canPlayInBrowser,
  onPlay,
  onLeave,
  onDelete,
}: RelayHeroProps) {
  return (
    <div className="flex flex-col items-center gap-6 md:flex-row md:items-start md:gap-8">
      {/* Cover art */}
      <div className="w-48 flex-shrink-0 md:w-64">
        <div className="aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl">
          {relay.gameCoverUrl ? (
            <img
              src={relay.gameCoverUrl}
              alt={relay.gameTitle}
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
              <Repeat className="h-16 w-16 text-surface-700" />
            </div>
          )}
        </div>
      </div>

      {/* Info */}
      <div className="w-full min-w-0 flex-1 space-y-5 pt-2">
        <div className="space-y-4">
          <div>
            <h1 className="text-2xl font-bold text-surface-100 md:text-3xl">
              {relay.name}
            </h1>
            <div className="flex items-center gap-3 mt-2">
              <Badge variant="brand">{relay.gameConsoleName}</Badge>
              <Badge
                variant={relayStatusVariant[relay.status]}
                className="capitalize"
              >
                {relay.status}
              </Badge>
            </div>
          </div>

          {relay.description && (
            <p className="text-sm text-surface-300 leading-relaxed">
              {relay.description}
            </p>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="primary"
              size="sm"
              onClick={onPlay}
              disabled={!canPlayInBrowser || relay.status === "completed"}
              title={
                !canPlayInBrowser
                  ? `${relay.gameConsoleName} is not supported for browser play`
                  : relay.status === "completed"
                    ? "This relay is completed"
                    : "Play in Browser"
              }
              data-testid="relay-play-btn"
            >
              <Play className="h-5 w-5" />
              Play
            </Button>
            {!isOwner && (
              <Button variant="secondary" size="sm" onClick={onLeave}>
                <LogOut className="h-5 w-5" />
                Leave Relay
              </Button>
            )}
            {isOwner && (
              <Button variant="danger" size="sm" onClick={onDelete}>
                <Trash2 className="h-5 w-5" />
                Delete Relay
              </Button>
            )}
          </div>
        </div>

        {/* Meta info */}
        <div className="flex flex-wrap items-center gap-6 text-sm text-surface-400">
          <div className="flex items-center gap-2">
            <span className="text-surface-500">Game:</span>
            <span className="text-surface-200">{relay.gameTitle}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-surface-500">Created by:</span>
            <span className="text-surface-200">{relay.ownerUsername}</span>
          </div>
          <div>
            <span className="text-surface-500">Last active:</span>{" "}
            {formatRelativeTime(relay.lastActivityAt)}
          </div>
        </div>

        {/* Member avatars */}
        <MemberAvatars members={relay.members} max={6} />
      </div>
    </div>
  );
}
