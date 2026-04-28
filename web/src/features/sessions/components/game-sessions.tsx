import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Layers, Plus } from "lucide-react";
import { Button, Section, Skeleton, EmptyState, Input, useToast } from "@/components/ui";
import {
  useGameSessions,
  useCreateSession,
  useRenameSession,
  useDeleteSession,
  useCloneSession,
} from "@/hooks/use-sessions";
import { useAuth } from "@/hooks/use-auth";
import { SessionCard } from "./session-card";
import { CloneSessionDialog } from "./clone-session-dialog";
import type { GameSession } from "@/types/api";

function SessionsSkeleton() {
  return (
    <div data-comp="SessionsSkeleton" className="space-y-2">
      {Array.from({ length: 2 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30"
        >
          <Skeleton className="w-12 h-12 rounded-lg" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-28" />
          </div>
          <Skeleton className="h-8 w-20 rounded-lg" />
        </div>
      ))}
    </div>
  );
}

interface GameSessionsProps {
  gameId: string;
  /** Game title — passed to the clone-confirmation dialog so the user
   *  has context about what they're cloning. */
  gameTitle?: string;
}

export function GameSessions({ gameId, gameTitle }: GameSessionsProps) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const { data: sessions, isLoading } = useGameSessions(gameId);
  const createSession = useCreateSession();
  const renameSession = useRenameSession();
  const deleteSession = useDeleteSession();
  const cloneSession = useCloneSession();
  const [showNewInput, setShowNewInput] = useState(false);
  const [newName, setNewName] = useState("");
  const [cloneTarget, setCloneTarget] = useState<GameSession | null>(null);

  function handleCreate() {
    const name = newName.trim() || `Session ${(sessions?.length ?? 0) + 1}`;
    createSession.mutate(
      { gameId, name },
      {
        onSuccess: () => {
          setNewName("");
          setShowNewInput(false);
        },
      },
    );
  }

  function handleRename(session: GameSession, name: string) {
    renameSession.mutate({ id: session.id, gameId, name });
  }

  function handleDelete(session: GameSession) {
    deleteSession.mutate({ id: session.id, gameId });
  }

  function handleRequestClone(session: GameSession) {
    setCloneTarget(session);
  }

  function handleConfirmClone(name: string) {
    if (!cloneTarget) return;
    cloneSession.mutate(
      { id: cloneTarget.id, gameId, name: name || undefined },
      {
        onSuccess: (created) => {
          setCloneTarget(null);
          // Cloning is a branching action — drop the user on the new
          // session's detail page so they can decide whether to rename
          // it further or jump straight to playing.
          if (created?.id) navigate(`/sessions/${created.id}`);
        },
        onError: () => {
          toast("error", "Failed to clone session. Please try again.");
        },
      },
    );
  }

  function handleContinue(session: GameSession) {
    navigate(`/games/${gameId}/play/${session.id}`);
  }

  if (isLoading) {
    return (
      <Section className="p-6">
        <div className="flex items-center gap-2.5 mb-4">
          <Layers className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Sessions</h2>
        </div>
        <SessionsSkeleton />
      </Section>
    );
  }

  return (
    <Section className="p-6">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2.5">
          <Layers className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Sessions</h2>
          {sessions && sessions.length > 0 && (
            <span className="text-sm text-surface-500">
              ({sessions.length})
            </span>
          )}
        </div>
        {!showNewInput && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setShowNewInput(true)}
          >
            <Plus className="h-4 w-4 mr-1" />
            New Session
          </Button>
        )}
      </div>

      {showNewInput && (
        <div
          className="flex items-center gap-2 mb-4"
          data-testid="new-session-form"
        >
          <Input
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="Session name (optional)"
            onKeyDown={(e) => {
              if (e.key === "Enter") handleCreate();
              if (e.key === "Escape") {
                setShowNewInput(false);
                setNewName("");
              }
            }}
            autoFocus
          />
          <Button
            variant="primary"
            size="sm"
            onClick={handleCreate}
            loading={createSession.isPending}
          >
            Create
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setShowNewInput(false);
              setNewName("");
            }}
          >
            Cancel
          </Button>
        </div>
      )}

      {!sessions || sessions.length === 0 ? (
        <EmptyState
          icon={Layers}
          title="No sessions yet"
          description="Start a new session to track your progress separately."
        />
      ) : (
        <div className="space-y-2">
          {sessions.map((session, index) => (
            <SessionCard
              key={session.id}
              session={session}
              currentUsername={user?.username}
              isCurrent={index === 0}
              onContinue={handleContinue}
              onRename={handleRename}
              onDelete={handleDelete}
              onClone={handleRequestClone}
              isDeleting={
                deleteSession.isPending &&
                deleteSession.variables?.id === session.id
              }
              isCloning={
                cloneSession.isPending &&
                cloneSession.variables?.id === session.id
              }
            />
          ))}
        </div>
      )}

      <CloneSessionDialog
        open={!!cloneTarget}
        onClose={() => {
          if (!cloneSession.isPending) setCloneTarget(null);
        }}
        sourceName={cloneTarget?.name ?? ""}
        description={
          gameTitle
            ? `Cloning a session of ${gameTitle}.`
            : undefined
        }
        isPending={cloneSession.isPending}
        onConfirm={handleConfirmClone}
      />
    </Section>
  );
}
