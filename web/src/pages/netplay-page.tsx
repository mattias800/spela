import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Gamepad2, Plus } from "lucide-react";
import {
  Button,
  Input,
  Skeleton,
  EmptyState,
} from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useNetplaySessions,
  useJoinNetplaySession,
} from "@/hooks/use-netplay";
import { NetplaySessionRow } from "@/features/netplay/components/netplay-session-row";
import { NetplayCreateModal } from "@/features/netplay/components/netplay-create-modal";
import { Pagination } from "@/components/pagination";

function SessionListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 4 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-4 rounded-xl bg-surface-900/50"
        >
          <Skeleton className="h-16 w-12 rounded-lg flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-3 w-32" />
          </div>
          <Skeleton className="h-6 w-16 rounded-full" />
        </div>
      ))}
    </div>
  );
}

export function NetplayPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const pageSize = 20;
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [inviteCode, setInviteCode] = useState("");

  const { toast } = useToast();
  const { data: sessionsData, isLoading } = useNetplaySessions(page, pageSize);
  const joinLookup = useJoinNetplaySession();

  const sessions = sessionsData?.data ?? [];

  function handleLookup() {
    const code = inviteCode.trim();
    if (!code) return;
    joinLookup.mutate(code, {
      onSuccess: (session) => {
        setInviteCode("");
        navigate(`/netplay/${session.id}`);
      },
      onError: () => {
        toast(
          "error",
          "Session not found. The code may have expired or the host may have cancelled.",
        );
      },
    });
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">Netplay</h1>
          <p className="mt-1 text-surface-400">
            Real-time multiplayer sessions.
          </p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="h-4 w-4" />
          Create Session
        </Button>
      </div>

      {/* Find session by invite code */}
      <div className="flex items-end gap-3">
        <div className="w-64">
          <Input
            id="invite-code"
            label="Find Session by Invite Code"
            placeholder="e.g. ABC123"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
            maxLength={6}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleLookup();
              }
            }}
          />
        </div>
        <Button
          variant="secondary"
          onClick={handleLookup}
          loading={joinLookup.isPending}
          disabled={!inviteCode.trim()}
        >
          View Session
        </Button>
      </div>

      {/* Session list */}
      {isLoading ? (
        <SessionListSkeleton />
      ) : sessions.length === 0 ? (
        <EmptyState
          icon={Gamepad2}
          title="No netplay sessions yet"
          description="Create one to play with a friend."
          action={
            <Button onClick={() => setShowCreateModal(true)}>
              <Plus className="h-4 w-4" />
              Create Session
            </Button>
          }
        />
      ) : (
        <div className="space-y-3">
          {sessions.map((session) => (
            <NetplaySessionRow key={session.id} session={session} />
          ))}
        </div>
      )}

      {sessionsData && (
        <Pagination
          total={sessionsData.total}
          pageSize={pageSize}
          currentPage={page}
          onPageChange={setPage}
        />
      )}

      {/* Create Session Modal */}
      <NetplayCreateModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={(sessionId) => {
          setShowCreateModal(false);
          navigate(`/netplay/${sessionId}`);
        }}
      />
    </div>
  );
}
