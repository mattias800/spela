import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Repeat, Layers } from "lucide-react";
import { Button, BackButton, Modal, Skeleton, EmptyState, Card } from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useRelay,
  useRelaySaves,
  useDeleteRelay,
  useLeaveRelay,
  useRelayRealtime,
} from "@/hooks/use-relays";
import { useConsoles } from "@/hooks/use-consoles";
import { useAuth } from "@/hooks/use-auth";
import { RelayHero } from "@/features/relays/components/relay-hero";
import { RelayMembersList } from "@/features/relays/components/relay-members-list";
import { RelaySavesList } from "@/features/relays/components/relay-saves-list";

function RelayDetailSkeleton() {
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

export function RelayDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { user } = useAuth();

  const { data: relay, isLoading } = useRelay(id ?? "");
  const { data: saves } = useRelaySaves(id ?? "");
  const { data: consoles } = useConsoles();
  const deleteRelay = useDeleteRelay();
  const leaveRelay = useLeaveRelay();
  useRelayRealtime(id);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showLeaveModal, setShowLeaveModal] = useState(false);

  const isOwner = relay?.ownerId === user?.id;
  const consoleInfo = consoles?.find(
    (c) => c.name === relay?.gameConsoleName,
  );
  const canPlayInBrowser = !!consoleInfo?.emulatorJsCore;

  if (isLoading) {
    return <RelayDetailSkeleton />;
  }

  if (!relay) {
    return (
      <EmptyState
        icon={Repeat}
        title="Relay not found"
        description="This relay may have been deleted or you don't have access."
        action={
          <Button variant="ghost" onClick={() => navigate(-1)}>
            Go back
          </Button>
        }
      />
    );
  }

  function handlePlay() {
    const sid = relay!.sessionId ?? "new";
    navigate(`/games/${relay!.gameId}/play/${sid}?relayId=${relay!.id}`);
  }

  function handleDelete() {
    deleteRelay.mutate(relay!.id, {
      onSuccess: () => {
        toast("success", "Relay deleted");
        navigate("/relays");
      },
      onError: () => {
        toast("error", "Failed to delete relay");
      },
    });
  }

  function handleLeave() {
    leaveRelay.mutate(relay!.id, {
      onSuccess: () => {
        toast("success", "Left relay");
        navigate("/relays");
      },
      onError: () => {
        toast("error", "Failed to leave relay");
      },
    });
  }

  return (
    <div className="max-w-5xl space-y-8">
      <BackButton onClick={() => navigate(-1)} />

      <RelayHero
        relay={relay}
        isOwner={isOwner}
        canPlayInBrowser={canPlayInBrowser}
        onPlay={handlePlay}
        onLeave={() => setShowLeaveModal(true)}
        onDelete={() => setShowDeleteModal(true)}
      />

      <RelayMembersList
        relayId={relay.id}
        members={relay.members}
        isOwner={isOwner}
      />

      <RelaySavesList
        saves={saves}
        relayId={relay.id}
        isOwner={isOwner}
        currentUserId={user?.id}
      />

      {/* Linked session */}
      {relay.sessionId && (
        <Card className="p-6" data-testid="relay-session-link">
          <div className="flex items-center gap-2.5 mb-3">
            <Layers className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-semibold text-surface-100">
              Session
            </h2>
          </div>
          <p className="text-sm text-surface-400 mb-3">
            This relay is linked to a game session where saves and cheats are
            tracked.
          </p>
          <Link
            to={`/sessions/${relay.sessionId}`}
            className="text-sm text-brand-400 hover:text-brand-300 transition-colors"
          >
            View Session Details
          </Link>
        </Card>
      )}

      {/* Delete confirm modal */}
      <Modal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        title="Delete Relay"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to delete{" "}
          <span className="font-medium text-surface-100">{relay.name}</span>?
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
            loading={deleteRelay.isPending}
            onClick={handleDelete}
          >
            Delete Relay
          </Button>
        </div>
      </Modal>

      {/* Leave confirm modal */}
      <Modal
        open={showLeaveModal}
        onClose={() => setShowLeaveModal(false)}
        title="Leave Relay"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to leave{" "}
          <span className="font-medium text-surface-100">{relay.name}</span>?
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
            loading={leaveRelay.isPending}
            onClick={handleLeave}
          >
            Leave Relay
          </Button>
        </div>
      </Modal>
    </div>
  );
}
