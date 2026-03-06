import { useState } from "react";
import { Users, UserPlus, Crown, X } from "lucide-react";
import { Button, Badge, ConfirmDeleteModal } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { useAuth } from "@/hooks/use-auth";
import { useRemoveSharedSessionMember } from "@/hooks/use-shared-sessions";
import { formatRelativeTime } from "@/lib/format";
import { SharedSessionInviteModal } from "@/features/shared-sessions/components/shared-session-invite-modal";
import type { SharedSessionMember } from "@/types/api";

interface SharedSessionMembersListProps {
  sharedSessionId: string;
  members: SharedSessionMember[];
  isOwner: boolean;
}

export function SharedSessionMembersList({
  sharedSessionId,
  members,
  isOwner,
}: SharedSessionMembersListProps) {
  const [showInvite, setShowInvite] = useState(false);
  const [removeTarget, setRemoveTarget] = useState<SharedSessionMember | null>(null);
  const removeMember = useRemoveSharedSessionMember();
  const { user } = useAuth();

  return (
    <section>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2.5">
          <Users className="h-5 w-5 text-brand-400" />
          <h2 className="text-xl font-bold text-surface-100">Members</h2>
          <span className="text-sm text-surface-500">({members.length})</span>
        </div>
        {isOwner && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setShowInvite(true)}
          >
            <UserPlus className="h-4 w-4" />
            Invite
          </Button>
        )}
      </div>

      <div className="space-y-2">
        {members.map((member) => (
          <div
            key={member.userId}
            className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50"
            data-testid={`shared-session-member-${member.userId}`}
          >
            <div className="relative">
              <PlayerAvatar
                username={member.username}
                avatarUrl={member.avatarUrl}
              />
              {member.isOnline && (
                <div className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full bg-green-500 ring-2 ring-surface-950" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <p className="text-sm font-medium text-surface-200 truncate">
                  {member.username}
                </p>
                {member.role === "owner" && (
                  <Badge variant="warning">
                    <Crown className="h-3 w-3 mr-1" />
                    Owner
                  </Badge>
                )}
              </div>
              <div className="flex items-center gap-2 text-xs text-surface-500">
                <span>Joined {formatRelativeTime(member.joinedAt)}</span>
                {member.lastPlayedAt && (
                  <>
                    <span>&middot;</span>
                    <span>
                      Last played {formatRelativeTime(member.lastPlayedAt)}
                    </span>
                  </>
                )}
              </div>
            </div>
            {isOwner &&
              member.userId !== user?.id &&
              member.role !== "owner" && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setRemoveTarget(member)}
                  className="text-surface-400 hover:text-danger-500 hover:bg-danger-500/10"
                  title="Remove member"
                >
                  <X className="h-4 w-4" />
                </Button>
              )}
          </div>
        ))}
      </div>

      {/* Invite modal */}
      <SharedSessionInviteModal
        sharedSessionId={sharedSessionId}
        open={showInvite}
        onClose={() => setShowInvite(false)}
      />

      {/* Remove member confirm modal */}
      <ConfirmDeleteModal
        open={!!removeTarget}
        onClose={() => setRemoveTarget(null)}
        title="Remove Member"
        message={`Are you sure you want to remove ${removeTarget?.username ?? ""} from this shared session?`}
        isPending={removeMember.isPending}
        actionLabel="Remove"
        onConfirm={() => {
          if (removeTarget) {
            removeMember.mutate(
              { sharedSessionId, userId: removeTarget.userId },
              { onSuccess: () => setRemoveTarget(null) },
            );
          }
        }}
      />
    </section>
  );
}
