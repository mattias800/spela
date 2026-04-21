import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Repeat, Layers } from "lucide-react";
import { Button, Modal, EmptyState, Section, Skeleton } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useToast } from "@/components/ui";
import {
  useSharedSession,
  useSharedSessionSaves,
  useDeleteSharedSession,
  useLeaveSharedSession,
  useSharedSessionRealtime,
} from "@/hooks/use-shared-sessions";
import { useCloneSession } from "@/hooks/use-sessions";
import { useConsoles } from "@/hooks/use-consoles";
import { useAuth } from "@/hooks/use-auth";
import { SharedSessionHero } from "@/features/shared-sessions/components/shared-session-hero";
import { SharedSessionMembersList } from "@/features/shared-sessions/components/shared-session-members-list";
import { SharedSessionSavesList } from "@/features/shared-sessions/components/shared-session-saves-list";
import { CloneSessionDialog } from "@/features/sessions/components/clone-session-dialog";

function SharedSessionDetailSkeleton() {
  return (
    <div className="max-w-5xl space-y-8">
      <Skeleton className="h-5 w-16" />
      <div className="flex flex-col gap-6 md:flex-row md:gap-8">
        <Skeleton className="w-48 md:w-64 aspect-[3/4] rounded-2xl" />
        <div className="flex-1 space-y-5 pt-2">
          <Skeleton className="h-9 w-64" />
          <div className="flex gap-2">
            <Skeleton className="h-6 w-16 rounded-full" />
            <Skeleton className="h-6 w-16 rounded-full" />
          </div>
          <div className="flex gap-2">
            <Skeleton className="h-9 w-24 rounded-lg" />
            <Skeleton className="h-9 w-32 rounded-lg" />
          </div>
          <Skeleton className="h-4 w-96" />
          <div className="flex -space-x-2">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-8 w-8 rounded-full" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export function SharedSessionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { user } = useAuth();

  const { data: sharedSession, isLoading } = useSharedSession(id ?? "");
  const { data: saves } = useSharedSessionSaves(id ?? "");
  const { data: consoles } = useConsoles();
  const deleteSharedSession = useDeleteSharedSession();
  const leaveSharedSession = useLeaveSharedSession();
  const cloneSession = useCloneSession();
  useSharedSessionRealtime(id);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showLeaveModal, setShowLeaveModal] = useState(false);
  const [showCloneModal, setShowCloneModal] = useState(false);

  const isOwner = sharedSession?.ownerId === user?.id;
  const consoleInfo = consoles?.find(
    (c) => c.name === sharedSession?.consoleName,
  );
  const canPlayInBrowser = !!consoleInfo?.emulatorJsCore;

  if (isLoading) {
    return (
      <PageLayout backButtonVariant="standard">
        <SectionList className="max-w-5xl">
          <SharedSessionDetailSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (!sharedSession) {
    return (
      <EmptyState
        icon={Repeat}
        title="Shared session not found"
        description="This shared session may have been deleted or you don't have access."
        action={
          <Button variant="ghost" onClick={() => navigate(-1)}>
            Go back
          </Button>
        }
      />
    );
  }

  function handlePlay() {
    const sid = sharedSession!.sessionId ?? "new";
    navigate(`/games/${sharedSession!.gameId}/play/${sid}?sharedSessionId=${sharedSession!.id}`);
  }

  function handleDelete() {
    deleteSharedSession.mutate(sharedSession!.id, {
      onSuccess: () => {
        toast("success", "Shared session deleted");
        navigate("/shared-sessions");
      },
      onError: () => {
        toast("error", "Failed to delete shared session");
      },
    });
  }

  function handleLeave() {
    leaveSharedSession.mutate(sharedSession!.id, {
      onSuccess: () => {
        toast("success", "Left shared session");
        navigate("/shared-sessions");
      },
      onError: () => {
        toast("error", "Failed to leave shared session");
      },
    });
  }

  function handleConfirmClone(name: string) {
    // Shared sessions are UI shells around a backing personal
    // session; the clone endpoint takes that backing session's ID.
    // We guard with `!sharedSession.sessionId` at the menu level so
    // this path is only reachable when a backing session exists.
    const backingId = sharedSession!.sessionId;
    if (!backingId) return;
    cloneSession.mutate(
      { id: backingId, name: name || undefined },
      {
        onSuccess: (created) => {
          setShowCloneModal(false);
          if (created?.id) {
            toast("success", "Cloned to your library");
            navigate(`/sessions/${created.id}`);
          }
        },
        onError: () => {
          toast("error", "Failed to clone to your library");
        },
      },
    );
  }

  return (
    <PageLayout backButtonVariant="standard">
      <SectionList className="max-w-5xl">

      <SharedSessionHero
        sharedSession={sharedSession}
        isOwner={isOwner}
        canPlayInBrowser={canPlayInBrowser}
        onPlay={handlePlay}
        onLeave={() => setShowLeaveModal(true)}
        onDelete={() => setShowDeleteModal(true)}
        onClone={() => setShowCloneModal(true)}
        isCloning={cloneSession.isPending}
      />

      <SharedSessionMembersList
        sharedSessionId={sharedSession.id}
        members={sharedSession.members}
        isOwner={isOwner}
      />

      <SharedSessionSavesList
        saves={saves}
        sharedSessionId={sharedSession.id}
        isOwner={isOwner}
        currentUserId={user?.id}
      />

      {/* Linked session */}
      {sharedSession.sessionId && (
        <Section className="p-6" data-testid="shared-session-session-link">
          <div className="flex items-center gap-2.5 mb-3">
            <Layers className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-semibold text-surface-100">
              Session
            </h2>
          </div>
          <p className="text-sm text-surface-400 mb-3">
            This shared session is linked to a game session where saves and cheats are
            tracked.
          </p>
          <Link
            to={`/sessions/${sharedSession.sessionId}`}
            className="text-sm text-brand-400 hover:text-brand-300 transition-colors"
          >
            View Session Details
          </Link>
        </Section>
      )}

      {/* Delete confirm modal */}
      <Modal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        title="Delete Shared Session"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to delete{" "}
          <span className="font-medium text-surface-100">{sharedSession.name}</span>?
          All shared save states will be permanently deleted.
        </p>
        <div className="flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={() => setShowDeleteModal(false)}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deleteSharedSession.isPending}
            onClick={handleDelete}
          >
            Delete Shared Session
          </Button>
        </div>
      </Modal>

      {/* Leave confirm modal */}
      <Modal
        open={showLeaveModal}
        onClose={() => setShowLeaveModal(false)}
        title="Leave Shared Session"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to leave{" "}
          <span className="font-medium text-surface-100">{sharedSession.name}</span>?
          You will need a new invitation to rejoin.
        </p>
        <div className="flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={() => setShowLeaveModal(false)}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={leaveSharedSession.isPending}
            onClick={handleLeave}
          >
            Leave Shared Session
          </Button>
        </div>
      </Modal>

      {/* Clone to my library — US-1: any member can copy a shared
          session's playthrough into a personal session they own. */}
      <CloneSessionDialog
        open={showCloneModal}
        onClose={() => {
          if (!cloneSession.isPending) setShowCloneModal(false);
        }}
        sourceName={sharedSession.name}
        title="Clone to My Library"
        confirmLabel="Clone to my library"
        description="A new personal session will be created in your library. It inherits this shared session's total play time and is seeded with the most recent save. The shared session is untouched."
        isPending={cloneSession.isPending}
        onConfirm={handleConfirmClone}
      />
    </SectionList>
    </PageLayout>
  );
}

export default SharedSessionDetailPage;
