import { useState, useEffect } from "react";
import { Button, Modal } from "@/components/ui";
import { useShareSave } from "@/hooks/use-shared-saves";
import type { SaveState } from "@/types/api";

interface ShareSaveModalProps {
  open: boolean;
  onClose: () => void;
  save: SaveState | null;
  gameId: string;
}

export function ShareSaveModal({
  open,
  onClose,
  save,
  gameId,
}: ShareSaveModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const shareSave = useShareSave();

  useEffect(() => {
    if (open && save) {
      setName(save.name);
      setDescription("");
    }
  }, [open, save]);

  const handleSubmit = () => {
    if (!save || !name.trim()) return;
    shareSave.mutate(
      {
        gameId,
        saveId: save.id,
        name: name.trim(),
        description: description.trim(),
      },
      {
        onSuccess: () => {
          onClose();
          setName("");
          setDescription("");
        },
      },
    );
  };

  return (
    <Modal open={open} onClose={onClose} title="Share Save State" size="sm">
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-surface-300 mb-1.5">
            Name
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-lg bg-surface-800 border border-surface-700 px-3 py-2 text-sm text-surface-200 placeholder:text-surface-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
            placeholder="Save name"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-surface-300 mb-1.5">
            Description
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full rounded-lg bg-surface-800 border border-surface-700 px-3 py-2 text-sm text-surface-200 placeholder:text-surface-500 focus:outline-none focus:ring-2 focus:ring-brand-500 resize-none"
            placeholder="Describe this save (optional)..."
            rows={3}
          />
        </div>

        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            loading={shareSave.isPending}
            disabled={!name.trim()}
          >
            Share
          </Button>
        </div>
      </div>
    </Modal>
  );
}
