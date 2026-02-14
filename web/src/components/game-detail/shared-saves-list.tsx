import { useState } from "react";
import { Share2, Download, Trash2 } from "lucide-react";
import { Button, Skeleton, EmptyState, Modal } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { formatFileSize, formatRelativeTime } from "@/lib/format";
import { useAuth } from "@/hooks/use-auth";
import { useSharedSaves, useDeleteSharedSave } from "@/hooks/use-shared-saves";

function SharedSavesSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50">
          <Skeleton className="h-8 w-8 rounded-full flex-shrink-0" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-56" />
          </div>
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}

interface SharedSavesListProps {
  gameId: string;
}

export function SharedSavesList({ gameId }: SharedSavesListProps) {
  const [page, setPage] = useState(1);
  const pageSize = 10;
  const { data, isLoading } = useSharedSaves(gameId, page, pageSize);
  const deleteSharedSave = useDeleteSharedSave();
  const { user } = useAuth();
  const isAdmin = user?.role === "admin" || user?.role === "owner";
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  const hasMore = data ? page * pageSize < data.total : false;

  return (
    <section data-testid="shared-saves-list">
      <div className="flex items-center gap-2.5 mb-4">
        <Share2 className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">Community Saves</h2>
        {data && data.total > 0 && (
          <span className="text-sm text-surface-500">({data.total})</span>
        )}
      </div>

      {isLoading && page === 1 && <SharedSavesSkeleton />}

      {!isLoading && (!data?.data || data.data.length === 0) && (
        <EmptyState
          icon={Share2}
          title="No shared saves"
          description="Share your save states so others can enjoy them too."
          className="py-8"
        />
      )}

      {data?.data && data.data.length > 0 && (
        <div className="space-y-2">
          {data.data.map((save) => {
            const canDelete = save.userId === user?.id || isAdmin;
            return (
              <div
                key={save.id}
                className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
                data-testid={`shared-save-${save.id}`}
              >
                <PlayerAvatar
                  username={save.username}
                  avatarUrl={save.avatarUrl}
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-200 truncate">
                    {save.name}
                  </p>
                  <div className="flex items-center gap-2 text-xs text-surface-500">
                    <span>{save.username}</span>
                    <span>&middot;</span>
                    <span>{formatRelativeTime(save.createdAt)}</span>
                    <span>&middot;</span>
                    <span>{formatFileSize(save.fileSize)}</span>
                  </div>
                  {save.description && (
                    <p className="text-xs text-surface-400 mt-1 truncate">
                      {save.description}
                    </p>
                  )}
                </div>

                <span className="text-xs text-surface-500 whitespace-nowrap">
                  {save.downloadCount} {save.downloadCount === 1 ? "download" : "downloads"}
                </span>

                <div className="flex items-center gap-1">
                  <a
                    href={`/api/games/${gameId}/shared-saves/${save.id}/download`}
                    download
                    className="inline-flex items-center justify-center p-2 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
                    title="Download save"
                  >
                    <Download className="h-4 w-4" />
                  </a>
                  {canDelete && (
                    <button
                      onClick={() => setDeleteTarget(save.id)}
                      className="p-2 rounded-lg text-surface-400 hover:text-danger-500 hover:bg-danger-500/10 transition-colors"
                      title="Delete shared save"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {data && data.data.length > 0 && (data.total > pageSize) && (
        <div className="flex items-center justify-center gap-3 mt-6">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            disabled={page === 1}
          >
            Previous
          </Button>
          <span className="text-sm text-surface-400">
            Page {page} of {Math.ceil(data.total / pageSize)}
          </span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setPage((p) => p + 1)}
            disabled={!hasMore}
          >
            Next
          </Button>
        </div>
      )}

      {/* Delete confirm modal */}
      <Modal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete Shared Save"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to delete this shared save? Others will no longer be able to download it.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deleteSharedSave.isPending}
            onClick={() => {
              if (deleteTarget !== null) {
                deleteSharedSave.mutate(
                  { gameId, saveId: deleteTarget },
                  { onSuccess: () => setDeleteTarget(null) },
                );
              }
            }}
          >
            Delete
          </Button>
        </div>
      </Modal>
    </section>
  );
}
