import { useState } from "react";
import { Button, Modal } from "@/components/ui";
import { useToast } from "@/components/ui";
import { GamePicker } from "@/components/game-picker";
import { useCreateNetplaySession } from "@/hooks/use-netplay";
import { NETPLAY_SUPPORTED_CONSOLES } from "@/features/netplay/components/netplay-consoles";
import type { Game } from "@/types/api";

interface NetplayCreateModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (sessionId: string) => void;
}

export function NetplayCreateModal({
  open,
  onClose,
  onCreated,
}: NetplayCreateModalProps) {
  const [selectedGame, setSelectedGame] = useState<Game | null>(null);

  const { toast } = useToast();
  const createSession = useCreateNetplaySession();

  const filterNetplayGames = (games: Game[]) =>
    games.filter((g) => NETPLAY_SUPPORTED_CONSOLES.includes(g.consoleName));

  function handleCreate() {
    if (!selectedGame) return;
    createSession.mutate(
      {
        gameId: selectedGame.id,
      },
      {
        onSuccess: (session) => {
          toast("success", "Netplay session created");
          resetForm();
          onCreated(session.id);
        },
        onError: () => {
          toast("error", "Failed to create netplay session");
        },
      },
    );
  }

  function resetForm() {
    setSelectedGame(null);
  }

  function handleClose() {
    resetForm();
    onClose();
  }

  return (
    <Modal open={open} onClose={handleClose} title="Create Netplay Session">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleCreate();
        }}
        className="space-y-4"
      >
        <div className="space-y-1.5">
          <GamePicker
            selectedGame={selectedGame}
            onSelect={setSelectedGame}
            onClear={() => setSelectedGame(null)}
            filterFn={filterNetplayGames}
            emptyMessage="No supported netplay games found. Supported consoles: NES, SNES, GB, GBC, GBA, Genesis."
          />
          <p className="text-xs text-surface-500">
            Netplay supports NES, SNES, Game Boy, Game Boy Color, Game Boy Advance, and Genesis.
          </p>
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={createSession.isPending}
            disabled={!selectedGame}
          >
            Create Session
          </Button>
        </div>
      </form>
    </Modal>
  );
}
