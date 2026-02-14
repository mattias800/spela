import { FolderOpen } from "lucide-react";
import { Modal, EmptyState } from "@/components/ui";
import { formatFileSize, formatRelativeTime } from "@/lib/format";
import type { SaveState } from "@/types/api";

interface LoadSaveModalProps {
  saves: SaveState[] | undefined;
  open: boolean;
  onClose: () => void;
  onLoad: (save: SaveState) => void;
}

export function LoadSaveModal({
  saves,
  open,
  onClose,
  onLoad,
}: LoadSaveModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="Load Save State" size="md">
      {!saves || saves.length === 0 ? (
        <EmptyState
          icon={FolderOpen}
          title="No save states available"
          description="Play the game and save your progress to see saves here."
        />
      ) : (
        <div className="space-y-2 max-h-80 overflow-y-auto">
          {saves.map((save) => (
            <button
              key={save.id}
              onClick={() => onLoad(save)}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-surface-800 transition-colors text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-surface-100 truncate">
                  {save.name}
                  {save.isAuto && (
                    <span className="ml-2 text-xs text-brand-400">Auto</span>
                  )}
                </p>
                <p className="text-xs text-surface-500">
                  {formatRelativeTime(save.createdAt)} &middot;{" "}
                  {formatFileSize(save.fileSize)}
                </p>
              </div>
            </button>
          ))}
        </div>
      )}
    </Modal>
  );
}
