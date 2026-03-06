import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Layers, Plus } from "lucide-react";
import { Button, Card, Skeleton, EmptyState, Input } from "@/components/ui";
import {
  useGameSessions,
  useCreateSession,
  useRenameSession,
  useDeleteSession,
  useDuplicateSession,
} from "@/hooks/use-sessions";
import { useAuth } from "@/hooks/use-auth";
import { SessionCard } from "./session-card";
import type { GameSession } from "@/types/api";

function SessionsSkeleton() {
  return (
    <div className="space-y-2">
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
}

export function GameSessions({ gameId }: GameSessionsProps) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { data: sessions, isLoading } = useGameSessions(gameId);
  const createSession = useCreateSession();
  const renameSession = useRenameSession();
  const deleteSession = useDeleteSession();
  const duplicateSession = useDuplicateSession();
  const [showNewInput, setShowNewInput] = useState(false);
  const [newName, setNewName] = useState("");

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

  function handleDuplicate(session: GameSession) {
    duplicateSession.mutate({ id: session.id, gameId });
  }

  function handleContinue(session: GameSession) {
    navigate(`/games/${gameId}/play/${session.id}`);
  }

  if (isLoading) {
    return (
      <Card className="p-6">
        <div className="flex items-center gap-2.5 mb-4">
          <Layers className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Sessions</h2>
        </div>
        <SessionsSkeleton />
      </Card>
    );
  }

  return (
    <Card className="p-6">
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
              onDuplicate={handleDuplicate}
              isDeleting={
                deleteSession.isPending &&
                deleteSession.variables?.id === session.id
              }
              isDuplicating={
                duplicateSession.isPending &&
                duplicateSession.variables?.id === session.id
              }
            />
          ))}
        </div>
      )}
    </Card>
  );
}
