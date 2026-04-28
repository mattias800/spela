import { Mail } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { useNetplayInvites } from "@/hooks/use-netplay";
import { NetplayInviteCard } from "@/features/netplay/components/netplay-invite-card";

function InvitationsSkeleton() {
  return (
    <div data-comp="InvitationsSkeleton" className="space-y-3">
      {Array.from({ length: 2 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-4 rounded-xl bg-surface-900/50"
        >
          <Skeleton className="h-16 w-12 rounded-lg flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-3 w-32" />
          </div>
          <div className="flex gap-2">
            <Skeleton className="h-8 w-20 rounded-lg" />
            <Skeleton className="h-8 w-20 rounded-lg" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function NetplayInvitationsSection() {
  const { data: invitesData, isLoading } = useNetplayInvites();

  const pendingInvites = (invitesData?.data ?? []).filter(
    (inv) => inv.status === "pending",
  );

  if (isLoading) {
    return (
      <section>
        <div className="flex items-center gap-2.5 mb-4">
          <Mail className="h-5 w-5 text-brand-400" />
          <h2 className="text-xl font-bold text-surface-100">Invitations</h2>
        </div>
        <InvitationsSkeleton />
      </section>
    );
  }

  if (pendingInvites.length === 0) {
    return null;
  }

  return (
    <section data-comp="NetplayInvitationsSection">
      <div className="flex items-center gap-2.5 mb-4">
        <Mail className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">Invitations</h2>
        <span className="text-sm text-surface-500">
          ({pendingInvites.length})
        </span>
      </div>

      <div className="space-y-3">
        {pendingInvites.map((invite) => (
          <NetplayInviteCard key={invite.id} invite={invite} />
        ))}
      </div>
    </section>
  );
}
