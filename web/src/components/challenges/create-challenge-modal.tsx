import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Input, Modal, Select } from "@/components/ui";
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

        <div className="space-y-1.5">
          <label
            htmlFor="challenge-description"
            className="block text-sm font-medium text-surface-300"
          >
            Description
          </label>
          <textarea
            id="challenge-description"
            placeholder="Describe the goal of this challenge..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="w-full rounded-lg bg-surface-900 border border-surface-700 hover:border-surface-600 px-3.5 py-2.5 text-sm text-surface-100 placeholder:text-surface-500 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500 resize-none"
          />
        </div>

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
