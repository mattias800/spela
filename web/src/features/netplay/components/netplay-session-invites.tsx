import { Mail, Clock, Check, X, AlertCircle } from "lucide-react";
import { Badge, Skeleton } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { useSessionNetplayInvites } from "@/hooks/use-netplay";
import { formatRelativeTime } from "@/lib/format";
import type { NetplayInvite } from "@/types/api";

const statusConfig: Record<
  NetplayInvite["status"],
  { icon: typeof Clock; label: string; variant: "warning" | "success" | "danger" | "default" }
> = {
  pending: { icon: Clock, label: "Pending", variant: "warning" },
  accepted: { icon: Check, label: "Accepted", variant: "success" },
  declined: { icon: X, label: "Declined", variant: "danger" },
  expired: { icon: AlertCircle, label: "Expired", variant: "default" },
};

interface NetplaySessionInvitesProps {
  sessionId: string;
}

function InvitesSkeleton() {
  return (
    <div data-comp="InvitesSkeleton" className="space-y-2">
      {Array.from({ length: 2 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50"
        >
          <Skeleton className="h-8 w-8 rounded-full" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-3 w-24" />
          </div>
          <Skeleton className="h-6 w-16 rounded-full" />
        </div>
      ))}
    </div>
  );
}

export function NetplaySessionInvites({ sessionId }: NetplaySessionInvitesProps) {
  const { data: invites, isLoading } = useSessionNetplayInvites(sessionId);

  if (isLoading) {
    return (
      <section>
        <div className="flex items-center gap-2.5 mb-4">
          <Mail className="h-5 w-5 text-brand-400" />
          <h2 className="text-xl font-bold text-surface-100">
            Invited Players
          </h2>
        </div>
        <InvitesSkeleton />
      </section>
    );
  }

  if (!invites || invites.length === 0) {
    return null;
  }

  return (
    <section data-comp="NetplaySessionInvites">
      <div className="flex items-center gap-2.5 mb-4">
        <Mail className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">
          Invited Players
        </h2>
        <span className="text-sm text-surface-500">({invites.length})</span>
      </div>

      <div className="space-y-2">
        {invites.map((invite) => {
          const config = statusConfig[invite.status];
          const StatusIcon = config.icon;
          return (
            <div
              key={invite.id}
              className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50"
              data-testid={`session-invite-${invite.inviteeUsername}`}
            >
              <PlayerAvatar
                username={invite.inviteeUsername}
                avatarUrl={invite.inviteeAvatarUrl ?? undefined}
              />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-surface-200 truncate">
                  {invite.inviteeUsername}
                </p>
                <p className="text-xs text-surface-500">
                  Invited {formatRelativeTime(invite.createdAt)}
                </p>
              </div>
              <Badge variant={config.variant}>
                <StatusIcon className="h-3 w-3 mr-1" />
                {config.label}
              </Badge>
            </div>
          );
        })}
      </div>
    </section>
  );
}
