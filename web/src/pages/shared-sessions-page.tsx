import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Repeat, Plus, Mail } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
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
  useMySharedSessions,
  useSharedSessionInvitations,
  useAcceptSharedSessionInvitation,
  useRejectSharedSessionInvitation,
  useSharedSessionRealtime,
} from "@/hooks/use-shared-sessions";
import { SharedSessionCard } from "@/features/shared-sessions/components/shared-session-card";
import { SharedSessionCreateModal } from "@/features/shared-sessions/components/shared-session-create-modal";
import { InvitationCard } from "@/features/shared-sessions/components/invitation-card";
import { Pagination } from "@/components/pagination";

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

export function SharedSessionsPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<Tab>("mine");
  const [page, setPage] = useState(1);
  const pageSize = 24;
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);

  const { toast } = useToast();
  const { data: sharedSessionsData, isLoading: sharedSessionsLoading } = useMySharedSessions(
    page,
    pageSize,
  );
  const { data: invitationsData, isLoading: invitationsLoading } =
    useSharedSessionInvitations();
  const acceptInvitation = useAcceptSharedSessionInvitation();
  const rejectInvitation = useRejectSharedSessionInvitation();
  useSharedSessionRealtime();

  const sharedSessions = sharedSessionsData?.data ?? [];
  const invitations = invitationsData ?? [];
  const invitationCount = invitations.length;

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
    <PageLayout title="Shared Sessions" subtitle="Take turns playing games with friends using shared save states.">
      <SectionList>
      <div className="flex justify-end">
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="h-4 w-4" />
          Create Shared Session
        </Button>
      </div>

      {/* Tabs */}
      <StateTabNav>
        <StateTabItem
          active={activeTab === "mine"}
          onClick={() => handleTabChange("mine")}
        >
          My Shared Sessions
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

      {/* My Shared Sessions tab */}
      {activeTab === "mine" && (
        <>
          {sharedSessionsLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
              {Array.from({ length: 12 }, (_, i) => (
                <GameCardSkeleton key={i} />
              ))}
            </div>
          ) : sharedSessions.length === 0 ? (
            <EmptyState
              icon={Repeat}
              title="No shared sessions yet"
              description="Create a shared session to start taking turns with friends."
              action={
                <Button onClick={() => setShowCreateModal(true)}>
                  <Plus className="h-4 w-4" />
                  Create Shared Session
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
              {sharedSessions.map((sharedSession) => (
                <SharedSessionCard key={sharedSession.id} sharedSession={sharedSession} />
              ))}
            </div>
          )}

          {sharedSessionsData && (
            <Pagination
              total={sharedSessionsData.total}
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
              description="When someone invites you to a shared session, it will appear here."
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

      {/* Create Shared Session Modal */}
      <SharedSessionCreateModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={(sharedSessionId) => {
          setShowCreateModal(false);
          navigate(`/shared-sessions/${sharedSessionId}`);
        }}
      />
    </SectionList>
    </PageLayout>
  );
}

export default SharedSessionsPage;
