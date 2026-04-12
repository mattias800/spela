import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import {
  Pencil,
  Trash2,
  Download,
  Clock,
  HardDrive,
  Zap,
  Layers,
  Save,
  AlertTriangle,
} from "lucide-react";
import {
  Button,
  BackButton,
  Section,
  Badge,
  Skeleton,
  Switch,
  ConfirmDeleteModal,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import {
  useSession,
  useSessionSaves,
  useSessionCheats,
  useUpdateSessionCheats,
  useDeleteSessionSave,
  useDeleteSession,
  useRenameSession,
} from "@/hooks/use-sessions";
import { useGame } from "@/hooks/use-games";
import { useGameCheats } from "@/hooks/use-cheats";
import { formatFileSize, formatRelativeTime, formatPlayTime } from "@/lib/format";
import type { SessionSave } from "@/types/api";
import { SessionCheatSelector } from "@/features/sessions/components/session-cheat-selector";

function SessionDetailSkeleton() {
  return (
    <div className="max-w-4xl space-y-6">
      <Skeleton className="h-6 w-32" />
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-48 w-full rounded-xl" />
      <Skeleton className="h-48 w-full rounded-xl" />
    </div>
  );
}

export function SessionDetailPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { data: session, isLoading } = useSession(sessionId ?? "");
  const { data: saves, isLoading: savesLoading } = useSessionSaves(
    sessionId ?? "",
  );
  const { data: cheats } = useSessionCheats(sessionId ?? "");
  const updateCheats = useUpdateSessionCheats();
  const deleteSave = useDeleteSessionSave();
  const deleteSession = useDeleteSession();
  const renameSession = useRenameSession();

  const { data: game } = useGame(session?.gameId ?? "");
  const { data: gameCheats, isLoading: gameCheatsLoading } = useGameCheats(session?.gameId ?? "");

  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [showDeleteSession, setShowDeleteSession] = useState(false);
  const [deleteSaveTarget, setDeleteSaveTarget] = useState<string | null>(null);

  function handleRename() {
    if (!session || !sessionId) return;
    const trimmed = editName.trim();
    if (trimmed && trimmed !== session.name) {
      renameSession.mutate({
        id: sessionId,
        gameId: session.gameId,
        name: trimmed,
      });
    }
    setIsEditing(false);
  }

  function handleToggleCheats(enabled: boolean) {
    if (!sessionId || !cheats) return;
    updateCheats.mutate({
      sessionId,
      cheatsEnabled: enabled,
      enabledIndices: cheats.enabledIndices,
    });
  }

  function handleToggleCheat(index: number, enabled: boolean) {
    if (!sessionId || !cheats) return;
    const current = new Set(cheats.enabledIndices);
    if (enabled) current.add(index);
    else current.delete(index);
    updateCheats.mutate({
      sessionId,
      cheatsEnabled: cheats.cheatsEnabled,
      enabledIndices: Array.from(current),
    });
  }

  function handleSelectAllCheats() {
    if (!sessionId || !cheats || !gameCheats) return;
    updateCheats.mutate({
      sessionId,
      cheatsEnabled: cheats.cheatsEnabled,
      enabledIndices: gameCheats.map((c) => c.index),
    });
  }

  function handleDeselectAllCheats() {
    if (!sessionId || !cheats) return;
    updateCheats.mutate({
      sessionId,
      cheatsEnabled: cheats.cheatsEnabled,
      enabledIndices: [],
    });
  }

  function handleDeleteSession() {
    if (!session || !sessionId) return;
    deleteSession.mutate(
      { id: sessionId, gameId: session.gameId },
      {
        onSuccess: () => {
          navigate(`/games/${session.gameId}`);
        },
      },
    );
  }

  if (isLoading) {
    return (
      <PageLayout>
        <SectionList className="max-w-4xl">
          <SessionDetailSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (!session) {
    return (
      <div className="text-center py-20">
        <p className="text-surface-400">Session not found</p>
        <Button
          variant="ghost"
          onClick={() => navigate(-1)}
          className="mt-4"
        >
          Go back
        </Button>
      </div>
    );
  }

  return (
    <PageLayout>
      <SectionList className="max-w-4xl">

      <BackButton onClick={() => game ? navigate(`/games/${game.id}`) : navigate(-1)}>
        {game ? game.title : "Back"}
      </BackButton>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          {isEditing ? (
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              onBlur={handleRename}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleRename();
                if (e.key === "Escape") setIsEditing(false);
              }}
              className="bg-surface-800 text-surface-100 text-2xl font-bold rounded px-2 py-1 w-full outline-none ring-1 ring-brand-500"
              data-testid="session-name-input"
              autoFocus
            />
          ) : (
            <h1 className="text-2xl font-bold text-surface-100 flex items-center gap-3">
              <Layers className="h-6 w-6 text-brand-400 flex-shrink-0" />
              {session.name}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setEditName(session.name);
                  setIsEditing(true);
                }}
                aria-label="Rename session"
              >
                <Pencil className="h-4 w-4" />
              </Button>
            </h1>
          )}
          <div className="flex items-center gap-3 mt-2 text-sm text-surface-400">
            {game && (
              <Link
                to={`/games/${game.id}`}
                className="hover:text-surface-200 transition-colors"
              >
                {game.title}
              </Link>
            )}
            {session.lastPlayedAt && (
              <span className="flex items-center gap-1">
                <Clock className="h-3.5 w-3.5" />
                {formatRelativeTime(session.lastPlayedAt)}
              </span>
            )}
            <span>{formatPlayTime(session.totalPlayTime)}</span>
            {session.cheatsEnabled && (
              <span className="flex items-center gap-1 text-amber-400">
                <Zap className="h-3.5 w-3.5" />
                Cheats
              </span>
            )}
          </div>
        </div>

        <Button
          variant="ghost"
          size="sm"
          onClick={() => setShowDeleteSession(true)}
          className="text-red-400 hover:text-red-300"
        >
          <Trash2 className="h-4 w-4 mr-1" />
          Delete
        </Button>
      </div>

      {/* Save States */}
      <Section className="p-6">
        <div className="flex items-center gap-2.5 mb-4">
          <Save className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">
            Save States
          </h2>
          {saves && saves.length > 0 && (
            <span className="text-sm text-surface-500">
              ({saves.length})
            </span>
          )}
        </div>

        {savesLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 2 }, (_, i) => (
              <div
                key={i}
                className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30"
              >
                <Skeleton className="w-16 h-12 rounded" />
                <div className="flex-1 space-y-1.5">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-3 w-28" />
                </div>
              </div>
            ))}
          </div>
        ) : !saves || saves.length === 0 ? (
          <div className="py-8 text-center">
            <p className="text-surface-400">
              No save states yet. Play this session to create saves.
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {saves.map((save: SessionSave) => (
              <div
                key={save.id}
                className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors"
                data-testid={`save-${save.id}`}
              >
                {save.screenshotUrl ? (
                  <img
                    src={`${save.screenshotUrl}${save.screenshotUrl.includes("?") ? "&" : "?"}token=${localStorage?.getItem("accessToken") ?? ""}`}
                    alt=""
                    className="w-16 h-12 rounded object-cover bg-surface-800 flex-shrink-0"
                  />
                ) : (
                  <div className="w-16 h-12 rounded bg-surface-800 flex-shrink-0 flex items-center justify-center">
                    <HardDrive className="h-5 w-5 text-surface-600" />
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-100">
                    {save.name}
                    {save.isAuto && (
                      <Badge variant="brand" className="ml-2">
                        Auto
                      </Badge>
                    )}
                    {save.isCurrent && (
                      <Badge variant="success" className="ml-2">
                        Current
                      </Badge>
                    )}
                    {save.coreMatch === false && (
                      <span
                        className="inline-flex items-center ml-2"
                        title="This save state was created with a different emulator core and may not load correctly"
                        aria-label="Core mismatch warning"
                      >
                        <AlertTriangle className="h-3.5 w-3.5 text-warning-500" />
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-surface-500">
                    {formatRelativeTime(save.createdAt)} &middot;{" "}
                    {formatFileSize(save.fileSize)}
                    {save.coreName && ` \u00b7 ${save.coreName}`}
                  </p>
                  {save.notes && (
                    <p className="text-xs text-surface-400 mt-0.5 truncate">
                      {save.notes}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      window.open(
                        `/api/sessions/${sessionId}/saves/${save.id}/download`,
                        "_blank",
                      )
                    }
                    aria-label="Download save"
                    title="Download save"
                  >
                    <Download className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setDeleteSaveTarget(save.id)}
                    aria-label="Delete save"
                    title="Delete save"
                  >
                    <Trash2 className="h-4 w-4 text-red-400" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* Cheats */}
      <Section className="p-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Zap className="h-5 w-5 text-amber-400" />
            <h2 className="text-lg font-semibold text-surface-100">Cheats</h2>
          </div>
          <div className="flex items-center gap-3">
            {cheats && cheats.enabledIndices.length > 0 && (
              <span className="text-sm text-surface-400">
                {cheats.enabledIndices.length} enabled
              </span>
            )}
            <Switch
              checked={cheats?.cheatsEnabled ?? false}
              onChange={handleToggleCheats}
              disabled={!gameCheatsLoading && (!gameCheats || gameCheats.length === 0)}
              aria-label="Enable cheats"
            />
          </div>
        </div>
        {!gameCheatsLoading && (!gameCheats || gameCheats.length === 0) && (
          <p className="text-sm text-surface-500 mt-2">
            Cheats are not available for this game. Cheats require a verified ROM (checksum match).
          </p>
        )}
        {gameCheats && gameCheats.length > 0 && !cheats?.cheatsEnabled && (
          <p className="text-sm text-surface-500 mt-2">
            Cheats are disabled for this session. {gameCheats.length} cheats available.
          </p>
        )}
        {cheats?.cheatsEnabled && session && (
          <SessionCheatSelector
            gameId={session.gameId}
            enabledIndices={cheats.enabledIndices}
            onToggleCheat={handleToggleCheat}
            onSelectAll={handleSelectAllCheats}
            onDeselectAll={handleDeselectAllCheats}
          />
        )}
      </Section>

      {/* Delete session modal */}
      <ConfirmDeleteModal
        open={showDeleteSession}
        onClose={() => setShowDeleteSession(false)}
        title="Delete Session"
        message={`Delete "${session.name}"? All save data for this session will be permanently removed.`}
        onConfirm={handleDeleteSession}
        isPending={deleteSession.isPending}
      />

      {/* Delete save modal */}
      <ConfirmDeleteModal
        open={!!deleteSaveTarget}
        onClose={() => setDeleteSaveTarget(null)}
        title="Delete Save State"
        message="Are you sure you want to delete this save state? This action cannot be undone."
        onConfirm={() => {
          if (deleteSaveTarget && sessionId) {
            deleteSave.mutate(
              { sessionId, saveId: deleteSaveTarget },
              { onSuccess: () => setDeleteSaveTarget(null) },
            );
          }
        }}
        isPending={deleteSave.isPending}
      />
    </SectionList>
    </PageLayout>
  );
}
