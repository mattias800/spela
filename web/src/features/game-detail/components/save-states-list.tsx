import { useState } from "react";
import { Trash2, Download, Share2 } from "lucide-react";
import { Button, Badge, ConfirmDeleteModal } from "@/components/ui";
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
      <h2 className="text-lg font-semibold text-surface-100 mb-4">Save States</h2>
      {!saves || saves.length === 0 ? (
        <div className="py-8 text-center">
          <p className="text-surface-400">
            No save states yet. Play the game in the Spela player to create
            saves.
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {saves.map((save) => (
            <div key={save.id} className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors">
                {save.screenshotUrl && (
                  <img
                    src={`${save.screenshotUrl}${save.screenshotUrl.includes("?") ? "&" : "?"}token=${localStorage.getItem("accessToken")}`}
                    alt=""
                    className="w-16 h-12 rounded object-cover bg-surface-800 flex-shrink-0"
                  />
                )}
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
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setShareTarget(save)}
                    aria-label="Share save"
                    title="Share save"
                  >
                    <Share2 className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      window.open(
                        `/api/games/${gameId}/saves/${save.id}`,
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
                    onClick={() => setDeleteTarget(save.id)}
                    aria-label="Delete save"
                    title="Delete save"
                  >
                    <Trash2 className="h-4 w-4 text-danger-500" />
                  </Button>
                </div>
              </div>
          ))}
        </div>
      )}

      {/* Delete confirm modal */}
      <ConfirmDeleteModal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete Save State"
        message="Are you sure you want to delete this save state? This action cannot be undone."
        isPending={deleteSave.isPending}
        onConfirm={() => {
          if (deleteTarget !== null) {
            deleteSave.mutate(
              { gameId, saveId: deleteTarget },
              { onSuccess: () => setDeleteTarget(null) },
            );
          }
        }}
      />

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
