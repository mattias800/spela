import { useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { PageLayout, SectionList } from "@/components/layout";
import { Loader2, Puzzle } from "lucide-react";
import { Button, Input } from "@/components/ui";
import { useToast } from "@/components/ui";
import { GamePicker } from "@/components/game-picker";
import { useCreateRomHack } from "@/hooks/use-rom-hacks";
import type { Game } from "@/types/api";

const ACCEPTED_EXTENSIONS = ".ips,.bps,.ups,.xdelta";

export function RomHacksPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const createRomHack = useCreateRomHack();

  const [selectedGame, setSelectedGame] = useState<Game | null>(null);
  const [patchFile, setPatchFile] = useState<File | null>(null);
  const [mode, setMode] = useState<"variant" | "standalone">("variant");
  const [label, setLabel] = useState("");
  const [title, setTitle] = useState("");

  const canSubmit =
    selectedGame &&
    patchFile &&
    ((mode === "variant" && label.trim() !== "") ||
      (mode === "standalone" && title.trim() !== ""));

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0] ?? null;
      setPatchFile(file);
    },
    [],
  );

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (!selectedGame || !patchFile) return;

      createRomHack.mutate(
        {
          baseGameId: selectedGame.id,
          patchFile,
          mode,
          label: mode === "variant" ? label.trim() : undefined,
          title: mode === "standalone" ? title.trim() : undefined,
        },
        {
          onSuccess: (data) => {
            toast("success", `ROM hack created: ${data.title}`);
            navigate(`/games/${data.id}`);
          },
          onError: (err) => {
            toast(
              "error",
              err instanceof Error ? err.message : "Failed to create ROM hack",
            );
          },
        },
      );
    },
    [selectedGame, patchFile, mode, label, title, createRomHack, toast, navigate],
  );

  return (
    <PageLayout title="Add ROM Hack" subtitle="Upload a patch file to apply to a base game ROM.">
      <SectionList className="max-w-2xl">
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Game picker */}
        <GamePicker
          selectedGame={selectedGame}
          onSelect={setSelectedGame}
          onClear={() => setSelectedGame(null)}
        />

        {/* Patch file upload */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-surface-300">
            Patch File
          </label>
          <div className="relative">
            <input
              type="file"
              accept={ACCEPTED_EXTENSIONS}
              onChange={handleFileChange}
              className="block w-full text-sm text-surface-400
                file:mr-4 file:py-2 file:px-4
                file:rounded-lg file:border-0
                file:text-sm file:font-medium
                file:bg-surface-800 file:text-surface-200
                file:cursor-pointer file:transition-colors
                hover:file:bg-surface-700
                focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500
                cursor-pointer"
              data-testid="patch-file-input"
            />
          </div>
          <p className="text-xs text-surface-500">
            Supported formats: .ips, .bps, .ups, .xdelta
          </p>
        </div>

        {/* Mode selector */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-surface-300">
            Mode
          </label>
          <div className="flex gap-2" role="radiogroup" aria-label="ROM hack mode">
            <button
              type="button"
              role="radio"
              aria-checked={mode === "variant"}
              onClick={() => setMode("variant")}
              className={`flex-1 px-4 py-2.5 rounded-lg text-sm font-medium transition-colors border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                mode === "variant"
                  ? "bg-brand-500/15 border-brand-500/40 text-brand-400"
                  : "bg-surface-800/50 border-surface-700 text-surface-400 hover:text-surface-200 hover:border-surface-600"
              }`}
              data-testid="mode-variant"
            >
              Add as variant
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={mode === "standalone"}
              onClick={() => setMode("standalone")}
              className={`flex-1 px-4 py-2.5 rounded-lg text-sm font-medium transition-colors border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                mode === "standalone"
                  ? "bg-brand-500/15 border-brand-500/40 text-brand-400"
                  : "bg-surface-800/50 border-surface-700 text-surface-400 hover:text-surface-200 hover:border-surface-600"
              }`}
              data-testid="mode-standalone"
            >
              Create standalone game
            </button>
          </div>
          <p className="text-xs text-surface-500">
            {mode === "variant"
              ? "The patched ROM will appear as a variant of the base game."
              : "The patched ROM will become its own game entry in the library."}
          </p>
        </div>

        {/* Label input (variant mode) */}
        {mode === "variant" && (
          <div className="space-y-1.5">
            <label
              htmlFor="hack-label"
              className="block text-sm font-medium text-surface-300"
            >
              Label
            </label>
            <Input
              id="hack-label"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder='e.g. "English Translation", "Bugfix v1.2"'
              data-testid="label-input"
            />
          </div>
        )}

        {/* Title input (standalone mode) */}
        {mode === "standalone" && (
          <div className="space-y-1.5">
            <label
              htmlFor="hack-title"
              className="block text-sm font-medium text-surface-300"
            >
              Game Title
            </label>
            <Input
              id="hack-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Title for the new game entry"
              data-testid="title-input"
            />
          </div>
        )}

        {/* Error display */}
        {createRomHack.isError && (
          <div
            className="rounded-xl border border-red-500/30 bg-red-500/10 p-4"
            role="alert"
            data-testid="error-message"
          >
            <p className="text-sm text-red-400">
              {createRomHack.error instanceof Error
                ? createRomHack.error.message
                : "An error occurred while creating the ROM hack."}
            </p>
          </div>
        )}

        {/* Submit button */}
        <Button
          type="submit"
          disabled={!canSubmit || createRomHack.isPending}
          loading={createRomHack.isPending}
          icon={
            createRomHack.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Puzzle className="h-4 w-4" />
            )
          }
          data-testid="create-button"
        >
          {createRomHack.isPending ? "Applying patch..." : "Create ROM Hack"}
        </Button>
      </form>
    </SectionList>
    </PageLayout>
  );
}
