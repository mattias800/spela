import { useRef, useState, useEffect, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import type { Game, GameSummary } from "@/types/api";

interface PlayersLikeYouShelfProps {
  games: GameSummary[] | undefined;
  isLoading: boolean;
  similarUsersCount: number;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

function PlayersLikeYouSkeleton() {
  return (
    <section data-testid="players-like-you-skeleton">
      <div className="h-7 w-80 rounded bg-surface-800 animate-pulse mb-2" />
      <div className="h-4 w-56 rounded bg-surface-800/60 animate-pulse mb-5" />
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

export function PlayersLikeYouShelf({
  games,
  isLoading,
  similarUsersCount,
  onToggleFavorite,
  onTogglePlayLater,
}: PlayersLikeYouShelfProps) {
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
    return <PlayersLikeYouSkeleton />;
  }

  if (!games || games.length === 0) {
    return null;
  }

  const title = "Players like you also enjoyed";

  return (
    <section data-testid="players-like-you-shelf" className="group/shelf relative">
      <h2 className="text-xl font-bold text-surface-100 mb-1">{title}</h2>
      {similarUsersCount > 0 && (
        <p className="text-sm text-surface-400 mb-5" data-testid="similar-users-count">
          Based on {similarUsersCount} player{similarUsersCount !== 1 ? "s" : ""} with similar taste
        </p>
      )}

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
