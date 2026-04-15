import { Check } from "lucide-react";
import { useToast, Modal } from "@/components/ui";
import {
  useGameHeroes,
  useSetGameHero,
  type HeroOption,
} from "@/hooks/use-admin";
import { cn } from "@/lib/cn";

interface HeroArtSelectorProps {
  gameId: string;
  open: boolean;
  onClose: () => void;
}

export function HeroArtSelector({
  gameId,
  open,
  onClose,
}: HeroArtSelectorProps) {
  const { data, isLoading } = useGameHeroes(gameId);
  const setHero = useSetGameHero();
  const { toast } = useToast();

  const hasHeroes = data && data.heroes.length >= 2;

  function handleSelect(hero: HeroOption) {
    setHero.mutate(
      { gameId, url: hero.url },
      {
        onSuccess: () => {
          toast("success", "Hero art updated");
          onClose();
        },
        onError: () => {
          toast("error", "Failed to update hero art");
        },
      },
    );
  }

  return (
    <Modal open={open} onClose={onClose} title="Hero Art" size="lg">
      {isLoading ? (
        <p className="text-sm text-surface-400 py-4">
          Loading hero options...
        </p>
      ) : !hasHeroes ? (
        <p className="text-sm text-surface-400 py-4">
          No alternative hero art available. Scrape the game&apos;s metadata to
          find hero options from SteamGridDB.
        </p>
      ) : (
        <div
          className="flex flex-wrap gap-4"
          data-testid="hero-art-selector"
        >
          {data!.heroes.map((hero) => {
            const isActive = data.activeUrl && hero.thumb === data.activeUrl;
            return (
              <button
                key={hero.id}
                onClick={() => handleSelect(hero)}
                disabled={setHero.isPending}
                data-testid={`hero-option-${hero.id}`}
                className={cn(
                  "group relative rounded-xl overflow-hidden border-2 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-900",
                  isActive
                    ? "border-brand-500 ring-2 ring-brand-500/30"
                    : "border-surface-700 hover:border-surface-500",
                  setHero.isPending && "opacity-50 cursor-not-allowed",
                )}
              >
                <div className="w-48 md:w-64">
                  <img
                    src={hero.thumb}
                    alt="Hero art option"
                    className="w-full h-auto"
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
              </button>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
