import { useState } from "react";
import { Button, Input, Modal, Textarea } from "@/components/ui";
import { useToast } from "@/components/ui";
import { GamePicker } from "@/components/game-picker";
import { useCreateRelay } from "@/hooks/use-relays";
import type { Game } from "@/types/api";

interface RelayCreateModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (relayId: string) => void;
}

export function RelayCreateModal({
  open,
  onClose,
  onCreated,
}: RelayCreateModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedGame, setSelectedGame] = useState<Game | null>(null);

  const { toast } = useToast();
  const createRelay = useCreateRelay();

  function handleCreate() {
    if (!name.trim() || !selectedGame) return;
    createRelay.mutate(
      {
        name: name.trim(),
        gameId: selectedGame.id,
        description: description.trim() || undefined,
      },
      {
        onSuccess: (relay) => {
          toast("success", "Relay created");
          resetForm();
          onCreated(relay.id);
        },
        onError: () => {
          toast("error", "Failed to create relay");
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
    <Modal open={open} onClose={handleClose} title="Create Relay">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleCreate();
        }}
        className="space-y-4"
      >
        <Input
          id="relay-name"
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
          id="relay-description"
          label="Description"
          placeholder="What's this relay about?"
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
            loading={createRelay.isPending}
            disabled={!name.trim() || !selectedGame}
          >
            Create Relay
          </Button>
        </div>
      </form>
    </Modal>
  );
}
