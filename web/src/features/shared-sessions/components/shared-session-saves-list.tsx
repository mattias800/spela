import { useState } from "react";
import { Save, Trash2, Download } from "lucide-react";
import { Button, Badge, Section, ConfirmDeleteModal, EmptyState } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { useDeleteSharedSessionSave } from "@/hooks/use-shared-sessions";
import { formatFileSize, formatRelativeTime } from "@/lib/format";
import type { SharedSessionSave } from "@/types/api";

interface SharedSessionSavesListProps {
  saves: SharedSessionSave[] | undefined;
  sharedSessionId: string;
  isOwner: boolean;
  currentUserId?: string;
}

export function SharedSessionSavesList({
  saves,
  sharedSessionId,
  isOwner,
  currentUserId,
}: SharedSessionSavesListProps) {
  const deleteSave = useDeleteSharedSessionSave();
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  return (
    <section>
      <div className="flex items-center gap-2.5 mb-4">
        <Save className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">Shared Session Saves</h2>
        {saves && saves.length > 0 && (
          <span className="text-sm text-surface-500">({saves.length})</span>
        )}
      </div>

      {!saves || saves.length === 0 ? (
        <EmptyState
          icon={Save}
          title="No saves yet"
          description="Play the game in this shared session to create shared save states."
          className="py-8"
        />
      ) : (
        <div className="space-y-2">
          {saves.map((save) => {
            const canDelete =
              isOwner || save.userId === currentUserId;
            return (
              <Section key={save.id} hover>
                <div className="px-5 pb-5 flex items-center gap-4 py-3">
                  <PlayerAvatar
                    username={save.username}
                    avatarUrl={save.avatarUrl}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-surface-100">
                      {save.name}
                      {save.isAuto && (
                        <Badge variant="brand" className="ml-2">
                          Auto
                        </Badge>
                      )}
                    </p>
                    <p className="text-xs text-surface-500">
                      by {save.username} &middot;{" "}
                      {formatRelativeTime(save.createdAt)} &middot;{" "}
                      {formatFileSize(save.fileSize)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <a
                      href={`/api/shared-sessions/${sharedSessionId}/saves/${save.id}`}
                      download
                      className="inline-flex items-center justify-center p-2 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800/50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
                      title="Download save"
                      aria-label="Download save"
                    >
                      <Download className="h-4 w-4" />
                    </a>
                    {canDelete && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setDeleteTarget(save.id)}
                        aria-label="Delete save"
                      >
                        <Trash2 className="h-4 w-4 text-danger-500" />
                      </Button>
                    )}
                  </div>
                </div>
              </Section>
            );
          })}
        </div>
      )}

      {/* Delete confirm modal */}
      <ConfirmDeleteModal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete Save State"
        message="Are you sure you want to delete this save state? All shared session members will lose access to it."
        isPending={deleteSave.isPending}
        onConfirm={() => {
          if (deleteTarget !== null) {
            deleteSave.mutate(
              { sharedSessionId, saveId: deleteTarget },
              { onSuccess: () => setDeleteTarget(null) },
            );
          }
        }}
      />
    </section>
  );
}
