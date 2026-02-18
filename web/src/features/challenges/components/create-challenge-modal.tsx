import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Input, Modal, Select, Textarea } from "@/components/ui";
import { useToast } from "@/components/ui";
import { useCreateChallenge } from "@/hooks/use-challenges";
import { formatRelativeTime, formatFileSize } from "@/lib/format";
import type { SaveState, ChallengeType, ChallengeDifficulty } from "@/types/api";

interface CreateChallengeModalProps {
  open: boolean;
  onClose: () => void;
  gameId: string;
  saves?: SaveState[];
}

const typeOptions = [
  { value: "completion", label: "Completion — Complete the goal" },
  { value: "speedrun", label: "Speedrun — Fastest time wins" },
  { value: "survival", label: "Survival — Survive as long as possible" },
];

const difficultyOptions = [
  { value: "easy", label: "Easy" },
  { value: "medium", label: "Medium" },
  { value: "hard", label: "Hard" },
];

export function CreateChallengeModal({
  open,
  onClose,
  gameId,
  saves,
}: CreateChallengeModalProps) {
  const [selectedSaveId, setSelectedSaveId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<ChallengeType>("completion");
  const [difficulty, setDifficulty] = useState<ChallengeDifficulty>("medium");

  const createChallenge = useCreateChallenge();
  const { toast } = useToast();
  const navigate = useNavigate();

  function handleClose() {
    setSelectedSaveId(null);
    setName("");
    setDescription("");
    setType("completion");
    setDifficulty("medium");
    onClose();
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedSaveId || !name.trim()) return;

    createChallenge.mutate(
      {
        gameId,
        saveId: selectedSaveId,
        name: name.trim(),
        description: description.trim() || undefined,
        type,
        difficulty,
      },
      {
        onSuccess: (challenge) => {
          toast("success", "Challenge created!");
          handleClose();
          navigate(`/challenges/${challenge.id}`);
        },
        onError: () => {
          toast("error", "Failed to create challenge");
        },
      },
    );
  }

  const saveOptions = (saves ?? []).map((s) => ({
    value: String(s.id),
    label: `${s.name}${s.isAuto ? " (Auto)" : ""} — ${formatFileSize(s.fileSize)} — ${formatRelativeTime(s.createdAt)}`,
  }));

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Create Challenge"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Select
          label="Save State"
          value={selectedSaveId ? String(selectedSaveId) : ""}
          onChange={(e) =>
            setSelectedSaveId(e.target.value ? Number(e.target.value) : null)
          }
          options={[
            { value: "", label: "Select a save state..." },
            ...saveOptions,
          ]}
        />

        <Input
          id="challenge-name"
          label="Title"
          placeholder="Beat the boss in under 2 minutes"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <Textarea
          id="challenge-description"
          label="Description"
          placeholder="Describe the goal of this challenge..."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
        />

        <div className="grid grid-cols-2 gap-4">
          <Select
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value as ChallengeType)}
            options={typeOptions}
          />
          <Select
            label="Difficulty"
            value={difficulty}
            onChange={(e) =>
              setDifficulty(e.target.value as ChallengeDifficulty)
            }
            options={difficultyOptions}
          />
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={createChallenge.isPending}
            disabled={!selectedSaveId || !name.trim()}
          >
            Create Challenge
          </Button>
        </div>
      </form>
    </Modal>
  );
}
