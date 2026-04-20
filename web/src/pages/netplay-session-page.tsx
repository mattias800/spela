import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Gamepad2, Trash2, Monitor, Plus, UserPlus } from "lucide-react";
import {
  Button,
  Badge,
  ConfirmDeleteModal,
  Modal,
  Skeleton,
  EmptyState,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { ConsoleBadge } from "@/components/console-badge";
import { useToast } from "@/components/ui";
import {
  useNetplaySession,
  useDeleteNetplaySession,
  useNetplayInvitesRealtime,
} from "@/hooks/use-netplay";
import { useAuth } from "@/hooks/use-auth";
import {
  netplayStatusVariant,
  netplayStatusLabel,
  endReasonLabel,
} from "@/features/netplay/components/netplay-status";
import { SessionCode } from "@/features/netplay/components/session-code";
import { NetplayPlayerList } from "@/features/netplay/components/netplay-player-list";
import { NetplayInviteModal } from "@/features/netplay/components/netplay-invite-modal";
import { NetplaySessionInvites } from "@/features/netplay/components/netplay-session-invites";
import { formatDate } from "@/lib/format";

function NetplaySessionSkeleton() {
  return (
    <div className="max-w-5xl space-y-8">
      <Skeleton className="h-5 w-16" />
      <div className="flex flex-col gap-6 md:flex-row md:gap-8">
        <Skeleton className="w-48 md:w-64 aspect-[3/4] rounded-2xl" /> {/* Skeleton uses default 3:4, actual page uses session.coverAspectRatio */}
        <div className="flex-1 space-y-5 pt-2">
          <Skeleton className="h-9 w-64" />
          <div className="flex gap-2">
            <Skeleton className="h-6 w-16 rounded-full" />
            <Skeleton className="h-6 w-24 rounded-full" />
          </div>
          <Skeleton className="h-12 w-56 rounded-xl" />
          <Skeleton className="h-4 w-96" />
          <div className="space-y-2">
            <Skeleton className="h-14 w-full rounded-xl" />
            <Skeleton className="h-14 w-full rounded-xl" />
          </div>
        </div>
      </div>
    </div>
  );
}

export function NetplaySessionPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { user } = useAuth();

  const { data: session, isLoading } = useNetplaySession(id ?? "");
  const deleteSession = useDeleteNetplaySession();

  const [showCancelModal, setShowCancelModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showInviteModal, setShowInviteModal] = useState(false);
  useNetplayInvitesRealtime(id);

  const isHost = session?.hostId === user?.id;

  function handleCancel() {
    if (!session) return;
    deleteSession.mutate(session.id, {
      onSuccess: () => {
        toast("success", "Session cancelled");
        navigate("/netplay");
      },
      onError: () => {
        toast("error", "Failed to cancel session");
      },
    });
  }

  function handleDelete() {
    if (!session) return;
    const isInProgress = session.status === "in_progress";
    deleteSession.mutate(session.id, {
      onSuccess: () => {
        toast("success", isInProgress ? "Session ended and deleted" : "Session deleted");
        navigate("/netplay");
      },
      onError: () => {
        toast("error", "Failed to delete session");
      },
    });
  }

  if (isLoading) {
    return (
      <PageLayout backButtonVariant="standard">
        <SectionList className="max-w-5xl">
          <NetplaySessionSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (!session) {
    return (
      <EmptyState
        icon={Gamepad2}
        title="Session not found"
        description="This session may have ended or the invite code has expired."
        action={
          <Button variant="ghost" onClick={() => navigate(-1)}>
            Go back
          </Button>
        }
      />
    );
  }

  return (
    <PageLayout backButtonVariant="standard">
      <SectionList className="max-w-5xl">

      {/* Hero section */}
      <div className="flex flex-col items-center gap-6 md:flex-row md:items-start md:gap-8">
        {/* Cover art */}
        <div className="w-48 flex-shrink-0 md:w-64">
          <div style={{ aspectRatio: session.coverAspectRatio || 3 / 4 }} className="rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl">
            {session.gameCoverUrl ? (
              <img
                src={session.gameCoverUrl}
                alt={session.gameTitle}
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
                <Gamepad2 className="h-16 w-16 text-surface-700" />
              </div>
            )}
          </div>
        </div>

        {/* Info */}
        <div className="w-full min-w-0 flex-1 space-y-5 pt-2">
          <div className="space-y-4">
            <div>
              <h1 className="text-2xl font-bold text-surface-100 md:text-3xl">
                {session.gameTitle}
              </h1>
              <div className="flex items-center gap-3 mt-2">
                <ConsoleBadge code={session.consoleId ?? ""} label={session.consoleName ?? ""} />
                <Badge variant={netplayStatusVariant[session.status]}>
                  {netplayStatusLabel[session.status]}
                </Badge>
                {session.status === "ended" && session.endReason && (
                  <Badge variant="default">
                    {endReasonLabel[session.endReason]}
                  </Badge>
                )}
              </div>
            </div>

            {/* Invite player button — primary action for host (AC-1.1) */}
            {isHost && session.status === "waiting" && (
              <Button onClick={() => setShowInviteModal(true)}>
                <UserPlus className="h-4 w-4" />
                Invite Player
              </Button>
            )}

            {/* Invite code — secondary mechanism (AC-4.2) */}
            {session.status === "waiting" && (
              <div className="space-y-1.5">
                <p className="text-xs font-medium text-surface-500 uppercase tracking-wider">
                  Or share an invite code
                </p>
                <SessionCode code={session.inviteCode} />
              </div>
            )}

            {/* "Open in app" note per AC-4 */}
            {session.status !== "ended" && (
              <div className="flex items-center gap-2 px-3.5 py-2.5 rounded-lg bg-brand-500/10 border border-brand-500/20">
                <Monitor className="h-4 w-4 text-brand-400 flex-shrink-0" />
                <p className="text-sm text-brand-300">
                  Netplay requires the Spela player app. Set up your session here, then open it in the app to play.
                </p>
              </div>
            )}

            {/* Status-specific content */}
            {session.status === "in_progress" && (
              <p className="text-sm text-surface-300">
                Game in progress between{" "}
                <span className="font-medium text-surface-100">
                  {session.hostUsername}
                </span>
                {session.clientUsername && (
                  <>
                    {" "}
                    and{" "}
                    <span className="font-medium text-surface-100">
                      {session.clientUsername}
                    </span>
                  </>
                )}
                .
              </p>
            )}

            {session.status === "ended" && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate("/netplay")}
              >
                <Plus className="h-4 w-4" />
                Create New Session
              </Button>
            )}

            {/* Host-only cancel for waiting sessions */}
            {isHost && session.status === "waiting" && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => setShowCancelModal(true)}
              >
                <Trash2 className="h-4 w-4" />
                Cancel Session
              </Button>
            )}

            {/* Host-only delete for ended sessions */}
            {isHost && session.status === "ended" && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => setShowDeleteModal(true)}
              >
                <Trash2 className="h-4 w-4" />
                Delete Session
              </Button>
            )}

            {/* Host-only end & delete for in-progress sessions */}
            {isHost && session.status === "in_progress" && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => setShowDeleteModal(true)}
              >
                <Trash2 className="h-4 w-4" />
                End &amp; Delete Session
              </Button>
            )}
          </div>

          {/* Meta info */}
          <div className="flex flex-wrap items-center gap-6 text-sm text-surface-400">
            <div className="flex items-center gap-2">
              <span className="text-surface-500">Game:</span>
              <span className="text-surface-200">{session.gameTitle}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-surface-500">Host:</span>
              <span className="text-surface-200">{session.hostUsername}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-surface-500">Input Delay:</span>
              <span className="text-surface-200">
                {session.inputDelay}{" "}
                {session.inputDelay === 1 ? "frame" : "frames"}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-surface-500">Created:</span>
              <span className="text-surface-200">
                {formatDate(session.createdAt)}
              </span>
            </div>
            {session.startedAt && (
              <div className="flex items-center gap-2">
                <span className="text-surface-500">Started:</span>
                <span className="text-surface-200">
                  {formatDate(session.startedAt)}
                </span>
              </div>
            )}
            {session.endedAt && (
              <div className="flex items-center gap-2">
                <span className="text-surface-500">Ended:</span>
                <span className="text-surface-200">
                  {formatDate(session.endedAt)}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Player list */}
      <NetplayPlayerList session={session} />

      {/* Invited players — host sees all invites with status (AC-3.1) */}
      {isHost && <NetplaySessionInvites sessionId={session.id} />}

      {/* Invite player modal */}
      {isHost && (
        <NetplayInviteModal
          sessionId={session.id}
          open={showInviteModal}
          onClose={() => setShowInviteModal(false)}
        />
      )}

      {/* Cancel confirm modal */}
      <Modal
        open={showCancelModal}
        onClose={() => setShowCancelModal(false)}
        title="Cancel Session"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to cancel{" "}
          <span className="font-medium text-surface-100">{session.gameTitle}</span>?
          The invite code will no longer work.
        </p>
        <div className="flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={() => setShowCancelModal(false)}
          >
            Keep Session
          </Button>
          <Button
            variant="danger"
            loading={deleteSession.isPending}
            onClick={handleCancel}
          >
            Cancel Session
          </Button>
        </div>
      </Modal>

      {/* Delete confirm modal */}
      <ConfirmDeleteModal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        title={session.status === "in_progress" ? "End & Delete Session" : "Delete Session"}
        message={
          session.status === "in_progress"
            ? "This will end the session and disconnect any connected players. This action cannot be undone."
            : "Permanently delete this netplay session. This action cannot be undone."
        }
        onConfirm={handleDelete}
        isPending={deleteSession.isPending}
      />
    </SectionList>
    </PageLayout>
  );
}

export default NetplaySessionPage;
