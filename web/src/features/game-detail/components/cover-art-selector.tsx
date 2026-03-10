import { Check } from "lucide-react";
import { useToast, Modal } from "@/components/ui";
import {
  useGameCovers,
  useSetGameCover,
  type CoverOption,
} from "@/hooks/use-admin";
import { cn } from "@/lib/cn";

interface CoverArtSelectorProps {
  gameId: string;
  aspectRatio?: number;
  open: boolean;
  onClose: () => void;
}

export function CoverArtSelector({
  gameId,
  aspectRatio,
  open,
  onClose,
}: CoverArtSelectorProps) {
  const { data, isLoading, isError } = useGameCovers(gameId);
  const setCover = useSetGameCover();
  const { toast } = useToast();

  if (isLoading || isError || !data) return null;

  // Only show if there are multiple cover options
  if (data.covers.length < 2) return null;

  function handleSelect(cover: CoverOption) {
    if (cover.source === data!.active && cover.source !== "libretro-regional") {
      return;
    }
    setCover.mutate(
      {
        gameId,
        source: cover.source,
        libretroName: cover.libretroName,
      },
      {
        onSuccess: () => {
          const label = cover.label ?? cover.source;
          toast("success", `Cover art set to ${label}`);
          onClose();
        },
        onError: () => {
          toast("error", "Failed to update cover art");
        },
      },
    );
  }

  return (
    <Modal open={open} onClose={onClose} title="Cover Art Source" size="lg">
      <div className="flex flex-wrap gap-4" data-testid="cover-art-selector">
        {data.covers.map((cover, index) => {
          const isActive =
            cover.source === data.active &&
            cover.source !== "libretro-regional";
          const label = cover.label ?? cover.source;
          const key =
            cover.source === "libretro-regional"
              ? `regional-${index}`
              : cover.source;
          return (
            <button
              key={key}
              onClick={() => handleSelect(cover)}
              disabled={setCover.isPending}
              data-testid={`cover-option-${key}`}
              className={cn(
                "group relative rounded-xl overflow-hidden border-2 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-900",
                isActive
                  ? "border-brand-500 ring-2 ring-brand-500/30"
                  : "border-surface-700 hover:border-surface-500",
                setCover.isPending && "opacity-50 cursor-not-allowed",
              )}
            >
              <div
                className="w-24 md:w-32"
                style={{ aspectRatio: aspectRatio ?? 3 / 4 }}
              >
                <img
                  src={cover.url}
                  alt={`${label} cover`}
                  className="h-full w-full object-cover"
                  loading="lazy"
                />
              </div>
              {!isActive && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
                  <span className="rounded-lg bg-brand-500 px-3 py-1.5 text-xs font-semibold text-white">
                    Select
                  </span>
                </div>
              )}
              {isActive && (
                <div className="absolute top-1.5 right-1.5 rounded-full bg-brand-500 p-0.5">
                  <Check className="h-3 w-3 text-white" />
                </div>
              )}
              <div className="bg-surface-900/90 px-2 py-1 text-center">
                <span className="text-xs font-medium text-surface-300">
                  {label}
                </span>
              </div>
            </button>
          );
        })}
      </div>
    </Modal>
  );
}
