import { useState } from "react";
import { Trash2, Download, Share2 } from "lucide-react";
import { Button, Badge, Card, CardContent, Modal } from "@/components/ui";
import { useDeleteSave } from "@/hooks/use-games";
import { ShareSaveModal } from "@/features/game-detail/components/share-save-modal";
import { formatFileSize, formatRelativeTime } from "@/lib/format";
import type { SaveState } from "@/types/api";

interface SaveStatesListProps {
  saves: SaveState[] | undefined;
  gameId: string;
}

export function SaveStatesList({ saves, gameId }: SaveStatesListProps) {
  const deleteSave = useDeleteSave();
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);
  const [shareTarget, setShareTarget] = useState<SaveState | null>(null);

  return (
    <section>
      <h2 className="text-xl font-bold text-surface-100 mb-4">Save States</h2>
      {!saves || saves.length === 0 ? (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-surface-400">
              No save states yet. Play the game in the Spela player to create
              saves.
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-2">
          {saves.map((save) => (
            <Card key={save.id} hover>
              <CardContent className="flex items-center gap-4 py-3">
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
                    {formatRelativeTime(save.createdAt)} &middot;{" "}
                    {formatFileSize(save.fileSize)}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setShareTarget(save)}
                    className="inline-flex items-center justify-center p-2 rounded-lg text-surface-300 hover:text-brand-400 hover:bg-surface-800/50 transition-colors"
                    title="Share save"
                  >
                    <Share2 className="h-4 w-4" />
                  </button>
                  <a
                    href={`/api/games/${gameId}/saves/${save.id}`}
                    download
                    className="inline-flex items-center justify-center p-2 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
                  >
                    <Download className="h-4 w-4" />
                  </a>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setDeleteTarget(save.id)}
                  >
                    <Trash2 className="h-4 w-4 text-danger-500" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Delete confirm modal */}
      <Modal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete Save State"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to delete this save state? This action cannot be
          undone.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deleteSave.isPending}
            onClick={() => {
              if (deleteTarget !== null) {
                deleteSave.mutate(
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

      {/* Share save modal */}
      <ShareSaveModal
        open={!!shareTarget}
        onClose={() => setShareTarget(null)}
        save={shareTarget}
        gameId={gameId}
      />
    </section>
  );
}
