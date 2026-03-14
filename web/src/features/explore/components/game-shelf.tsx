import { useRef, useState, useEffect, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import type { Game } from "@/types/api";

interface GameShelfProps {
  title: string;
  games: Game[] | undefined;
  isLoading: boolean;
  hideConsoleName?: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

function GameShelfSkeleton({ title }: { title: string }) {
  return (
    <section data-testid={`shelf-skeleton-${title}`}>
      <h2 className="text-xl font-bold text-surface-100 mb-5">{title}</h2>
      <div className="flex gap-5 overflow-hidden">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
            <GameCardSkeleton />
          </div>
        ))}
      </div>
    </section>
  );
}

export function GameShelf({
  title,
  games,
  isLoading,
  hideConsoleName,
  onToggleFavorite,
  onTogglePlayLater,
}: GameShelfProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 0);
    setCanScrollRight(
      el.scrollLeft + el.clientWidth < el.scrollWidth - 1,
    );
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    updateScrollState();
    el.addEventListener("scroll", updateScrollState, { passive: true });
    window.addEventListener("resize", updateScrollState);
    return () => {
      el.removeEventListener("scroll", updateScrollState);
      window.removeEventListener("resize", updateScrollState);
    };
  }, [updateScrollState, games]);

  const scroll = useCallback((direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.7;
    el.scrollBy({
      left: direction === "left" ? -scrollAmount : scrollAmount,
      behavior: "smooth",
    });
  }, []);

  if (isLoading) {
    return <GameShelfSkeleton title={title} />;
  }

  if (!games || games.length === 0) {
    return null;
  }

  return (
    <section data-testid={`shelf-${title}`} className="group/shelf relative">
      <h2 className="text-xl font-bold text-surface-100 mb-5">{title}</h2>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label={`Scroll ${title} left`}
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label={`Scroll ${title} right`}
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        {/* Scrollable row */}
        <div
          ref={scrollRef}
          className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label={title}
        >
          {games.map((game) => (
            <div
              key={game.id}
              className="w-40 sm:w-44 lg:w-48 flex-shrink-0"
              role="listitem"
            >
              <GameCard
                game={game}
                showConsoleBadge={!hideConsoleName}
                hideConsoleName={hideConsoleName}
                onToggleFavorite={onToggleFavorite}
                onTogglePlayLater={onTogglePlayLater}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
