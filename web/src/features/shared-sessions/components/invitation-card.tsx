import { Repeat, Check, X } from "lucide-react";
import { Button, Section } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { formatRelativeTime } from "@/lib/format";
import type { SharedSessionInvitation } from "@/types/api";

interface InvitationCardProps {
  invitation: SharedSessionInvitation;
  onAccept: () => void;
  onReject: () => void;
  isAccepting: boolean;
  isRejecting: boolean;
}

export function InvitationCard({
  invitation,
  onAccept,
  onReject,
  isAccepting,
  isRejecting,
}: InvitationCardProps) {
  return (
    <Section
      className="flex items-center gap-4 px-4 py-4"
      data-testid={`shared-session-invitation-${invitation.id}`}
    >
      {/* Game cover */}
      <div className="h-16 w-12 rounded-lg overflow-hidden flex-shrink-0 bg-surface-800">
        {invitation.gameCoverUrl ? (
          <img
            src={invitation.gameCoverUrl}
            alt={invitation.gameTitle}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <Repeat className="h-5 w-5 text-surface-600" />
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-surface-100 truncate">
          {invitation.sharedSessionName}
        </p>
        <p className="text-xs text-surface-500 truncate">
          {invitation.gameTitle} &middot; {invitation.consoleName}
        </p>
        <div className="flex items-center gap-2 mt-1.5">
          <PlayerAvatar
            username={invitation.inviterUsername}
            avatarUrl={invitation.inviterAvatarUrl}
            size="sm"
          />
          <span className="text-xs text-surface-400">
            Invited by{" "}
            <span className="text-surface-200">
              {invitation.inviterUsername}
            </span>{" "}
            {formatRelativeTime(invitation.createdAt)}
          </span>
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 flex-shrink-0">
        <Button
          variant="primary"
          size="sm"
          onClick={onAccept}
          loading={isAccepting}
          disabled={isRejecting}
        >
          <Check className="h-4 w-4" />
          Accept
        </Button>
        <Button
          variant="secondary"
          size="sm"
          onClick={onReject}
          loading={isRejecting}
          disabled={isAccepting}
        >
          <X className="h-4 w-4" />
          Decline
        </Button>
      </div>
    </Section>
  );
}
