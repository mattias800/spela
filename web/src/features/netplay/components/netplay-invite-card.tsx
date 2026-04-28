import { useNavigate } from "react-router-dom";
import { Gamepad2, Check, X } from "lucide-react";
import { Button, Badge } from "@/components/ui";
import { useToast } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import {
  useAcceptNetplayInvite,
  useDeclineNetplayInvite,
} from "@/hooks/use-netplay";
import { formatRelativeTime } from "@/lib/format";
import type { NetplayInvite } from "@/types/api";

interface NetplayInviteCardProps {
  invite: NetplayInvite;
}

export function NetplayInviteCard({ invite }: NetplayInviteCardProps) {
  const navigate = useNavigate();
  const { toast } = useToast();
  const acceptInvite = useAcceptNetplayInvite();
  const declineInvite = useDeclineNetplayInvite();

  function handleAccept() {
    acceptInvite.mutate(invite.id, {
      onSuccess: () => {
        toast("success", "Invite accepted");
        navigate(`/netplay/${invite.netplaySessionId}`);
      },
      onError: () => {
        toast("error", "Failed to accept invite. The session may have ended.");
      },
    });
  }

  function handleDecline() {
    declineInvite.mutate(invite.id, {
      onSuccess: () => {
        toast("success", "Invite declined");
      },
      onError: () => {
        toast("error", "Failed to decline invite");
      },
    });
  }

  return (
    <div data-comp="NetplayInviteCard"
      className="flex items-center gap-4 px-4 py-4 rounded-xl bg-surface-900/50"
      data-testid={`netplay-invite-${invite.id}`}
    >
      {/* Cover art thumbnail */}
      <div className="h-16 w-12 flex-shrink-0 rounded-lg overflow-hidden bg-surface-800">
        {invite.gameCoverUrl ? (
          <img
            src={invite.gameCoverUrl}
            alt={invite.gameTitle}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <Gamepad2 className="h-5 w-5 text-surface-600" />
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-surface-100 truncate">
            {invite.gameTitle}
          </p>
          <Badge variant="brand" className="flex-shrink-0">
            {invite.consoleName}
          </Badge>
        </div>
        <div className="flex items-center gap-2 text-xs text-surface-500">
          <PlayerAvatar
            username={invite.inviterUsername}
            avatarUrl={invite.inviterAvatarUrl ?? undefined}
            size="xs"
          />
          <span>{invite.inviterUsername}</span>
          <span>&middot;</span>
          <span>{formatRelativeTime(invite.createdAt)}</span>
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 flex-shrink-0">
        <Button
          variant="secondary"
          size="sm"
          onClick={handleDecline}
          loading={declineInvite.isPending}
        >
          <X className="h-4 w-4" />
          Decline
        </Button>
        <Button
          size="sm"
          onClick={handleAccept}
          loading={acceptInvite.isPending}
        >
          <Check className="h-4 w-4" />
          Accept
        </Button>
      </div>
    </div>
  );
}
