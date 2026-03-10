import { useRef, useState, useEffect, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import type { ForYouRow, Game } from "@/types/api";

interface ForYouSectionProps {
  rows: ForYouRow[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

function ForYouSkeleton() {
  return (
    <div data-testid="for-you-skeleton" className="space-y-10">
      {Array.from({ length: 3 }, (_, i) => (
        <section key={i}>
          <div className="h-7 w-64 rounded bg-surface-800 animate-pulse mb-5" />
          <div className="flex gap-5 overflow-hidden">
            {Array.from({ length: 6 }, (_, j) => (
              <div key={j} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
                <GameCardSkeleton />
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function ForYouShelf({
  row,
  onToggleFavorite,
  onTogglePlayLater,
}: {
  row: ForYouRow;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}) {
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
  }, [updateScrollState, row.games]);

  const scroll = useCallback((direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.7;
    el.scrollBy({
      left: direction === "left" ? -scrollAmount : scrollAmount,
      behavior: "smooth",
    });
  }, []);

  if (!row.games || row.games.length === 0) {
    return null;
  }

  const testId = `for-you-row-${row.type}`;

  return (
    <section data-testid={testId} className="group/shelf relative">
      <div className="flex items-center gap-3 mb-5">
        {/* Show source game thumbnail for "because_you_played" rows */}
        {row.type === "because_you_played" && row.source_game?.coverUrl && (
          <img
            src={row.source_game.coverUrl}
            alt={row.source_game.title}
            className="h-8 w-6 rounded object-cover flex-shrink-0"
            data-testid="source-game-cover"
          />
        )}
        <div>
          <h3 className="text-xl font-bold text-surface-100">{row.title}</h3>
          {row.type === "expand_horizons" && row.genre && (
            <p className="text-sm text-surface-400 mt-0.5">
              Try something from <span className="text-brand-400 font-medium">{row.genre}</span>
            </p>
          )}
        </div>
      </div>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label={`Scroll ${row.title} left`}
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label={`Scroll ${row.title} right`}
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
          aria-label={row.title}
        >
          {row.games.map((game) => (
            <div
              key={game.id}
              className="w-40 sm:w-44 lg:w-48 flex-shrink-0"
              role="listitem"
            >
              <GameCard
                game={game}
                showConsoleBadge
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

export function ForYouSection({
  rows,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: ForYouSectionProps) {
  if (isLoading) {
    return <ForYouSkeleton />;
  }

  if (!rows || rows.length === 0) {
    return null;
  }

  return (
    <div data-testid="for-you-section" className="space-y-10">
      {rows.map((row, index) => (
        <ForYouShelf
          key={`${row.type}-${index}`}
          row={row}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
        />
      ))}
    </div>
  );
}
