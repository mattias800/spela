import { useState } from "react";
import { Button, Input, Modal, Textarea } from "@/components/ui";
import { useToast } from "@/components/ui";
import { GamePicker } from "@/components/game-picker";
import { useCreateSharedSession } from "@/hooks/use-shared-sessions";
import type { Game } from "@/types/api";

interface SharedSessionCreateModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (sharedSessionId: string) => void;
}

export function SharedSessionCreateModal({
  open,
  onClose,
  onCreated,
}: SharedSessionCreateModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedGame, setSelectedGame] = useState<Game | null>(null);

  const { toast } = useToast();
  const createSharedSession = useCreateSharedSession();

  function handleCreate() {
    if (!name.trim() || !selectedGame) return;
    createSharedSession.mutate(
      {
        name: name.trim(),
        gameId: selectedGame.id,
        description: description.trim() || undefined,
      },
      {
        onSuccess: (sharedSession) => {
          toast("success", "Shared session created");
          resetForm();
          onCreated(sharedSession.id);
        },
        onError: () => {
          toast("error", "Failed to create shared session");
        },
      },
    );
  }

  function resetForm() {
    setName("");
    setDescription("");
    setSelectedGame(null);
  }

  function handleClose() {
    resetForm();
    onClose();
  }

  return (
    <Modal open={open} onClose={handleClose} title="Create Shared Session">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleCreate();
        }}
        className="space-y-4"
      >
        <Input
          id="shared-session-name"
          label="Name"
          placeholder="Friday Night SNES Club"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <GamePicker
          selectedGame={selectedGame}
          onSelect={setSelectedGame}
          onClear={() => setSelectedGame(null)}
        />

        <Textarea
          id="shared-session-description"
          label="Description"
          placeholder="What's this shared session about?"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
        />

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={createSharedSession.isPending}
            disabled={!name.trim() || !selectedGame}
          >
            Create Shared Session
          </Button>
        </div>
      </form>
    </Modal>
  );
}
