import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Repeat, Plus, Mail } from "lucide-react";
import {
  Button,
  Badge,
  GameCardSkeleton,
  EmptyState,
  Skeleton,
  StateTabNav,
  StateTabItem,
} from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useMyRelays,
  useRelayInvitations,
  useAcceptRelayInvitation,
  useRejectRelayInvitation,
  useRelayRealtime,
} from "@/hooks/use-relays";
import { RelayCard } from "@/components/relays/relay-card";
import { RelayCreateModal } from "@/components/relays/relay-create-modal";
import { InvitationCard } from "@/components/relays/invitation-card";
import { Pagination } from "@/components/games/pagination";

type Tab = "mine" | "invitations";

function InvitationsSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 3 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-4 rounded-xl bg-surface-900/50"
        >
          <Skeleton className="h-16 w-12 rounded-lg flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-56" />
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

export function RelaysPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<Tab>("mine");
  const [page, setPage] = useState(1);
  const pageSize = 24;
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);

  const { toast } = useToast();
  const { data: relaysData, isLoading: relaysLoading } = useMyRelays(
    page,
    pageSize,
  );
  const { data: invitationsData, isLoading: invitationsLoading } =
    useRelayInvitations();
  const acceptInvitation = useAcceptRelayInvitation();
  const rejectInvitation = useRejectRelayInvitation();
  useRelayRealtime();

  const relays = relaysData?.data ?? [];
  const invitations = invitationsData?.data ?? [];
  const invitationCount = invitationsData?.total ?? 0;

  function handleTabChange(tab: Tab) {
    setActiveTab(tab);
    setPage(1);
  }

  function handleAccept(invitationId: string) {
    setAcceptingId(invitationId);
    acceptInvitation.mutate(invitationId, {
      onSuccess: () => {
        toast("success", "Invitation accepted");
        setAcceptingId(null);
      },
      onError: () => {
        toast("error", "Failed to accept invitation");
        setAcceptingId(null);
      },
    });
  }

  function handleReject(invitationId: string) {
    setRejectingId(invitationId);
    rejectInvitation.mutate(invitationId, {
      onSuccess: () => {
        toast("success", "Invitation declined");
        setRejectingId(null);
      },
      onError: () => {
        toast("error", "Failed to decline invitation");
        setRejectingId(null);
      },
    });
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">Relays</h1>
          <p className="mt-1 text-surface-400">
            Take turns playing games with friends using shared save states.
          </p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="h-4 w-4" />
          Create Relay
        </Button>
      </div>

      {/* Tabs */}
      <StateTabNav>
        <StateTabItem
          active={activeTab === "mine"}
          onClick={() => handleTabChange("mine")}
        >
          My Relays
        </StateTabItem>
        <StateTabItem
          active={activeTab === "invitations"}
          onClick={() => handleTabChange("invitations")}
        >
          Invitations
          {invitationCount > 0 && (
            <Badge variant="brand">{invitationCount}</Badge>
          )}
        </StateTabItem>
      </StateTabNav>

      {/* My Relays tab */}
      {activeTab === "mine" && (
        <>
          {relaysLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
              {Array.from({ length: 12 }, (_, i) => (
                <GameCardSkeleton key={i} />
              ))}
            </div>
          ) : relays.length === 0 ? (
            <EmptyState
              icon={Repeat}
              title="No relays yet"
              description="Create a relay to start taking turns with friends."
              action={
                <Button onClick={() => setShowCreateModal(true)}>
                  <Plus className="h-4 w-4" />
                  Create Relay
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
              {relays.map((relay) => (
                <RelayCard key={relay.id} relay={relay} />
              ))}
            </div>
          )}

          {relaysData && (
            <Pagination
              total={relaysData.total}
              pageSize={pageSize}
              currentPage={page}
              onPageChange={setPage}
            />
          )}
        </>
      )}

      {/* Invitations tab */}
      {activeTab === "invitations" && (
        <>
          {invitationsLoading ? (
            <InvitationsSkeleton />
          ) : invitations.length === 0 ? (
            <EmptyState
              icon={Mail}
              title="No invitations"
              description="When someone invites you to a relay, it will appear here."
            />
          ) : (
            <div className="space-y-3">
              {invitations.map((invitation) => (
                <InvitationCard
                  key={invitation.id}
                  invitation={invitation}
                  onAccept={() => handleAccept(invitation.id)}
                  onReject={() => handleReject(invitation.id)}
                  isAccepting={acceptingId === invitation.id}
                  isRejecting={rejectingId === invitation.id}
                />
              ))}
            </div>
          )}
        </>
      )}

      {/* Create Relay Modal */}
      <RelayCreateModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={(relayId) => {
          setShowCreateModal(false);
          navigate(`/relays/${relayId}`);
        }}
      />
    </div>
  );
}
