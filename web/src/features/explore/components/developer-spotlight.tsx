import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight, ArrowRight } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import type { DeveloperSpotlightResponse, Game } from "@/types/api";

interface DeveloperSpotlightProps {
  spotlight: DeveloperSpotlightResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

function DeveloperSpotlightSkeleton() {
  return (
    <section data-testid="developer-spotlight-skeleton">
      <Skeleton className="w-full h-64 rounded-2xl" />
    </section>
  );
}

export function DeveloperSpotlight({
  spotlight,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: DeveloperSpotlightProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 0);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
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
  }, [updateScrollState, spotlight]);

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
    return <DeveloperSpotlightSkeleton />;
  }

  if (!spotlight) {
    return null;
  }

  return (
    <section
      data-testid="developer-spotlight"
      className="group/spotlight relative"
    >
      {/* Full-width card with hero background */}
      <div className="relative rounded-2xl overflow-hidden border border-white/[0.06]">
        {/* Background image */}
        {spotlight.heroUrl ? (
          <img
            src={spotlight.heroUrl}
            alt=""
            className="absolute inset-0 w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="absolute inset-0 bg-gradient-to-br from-brand-900/80 via-brand-800/60 to-surface-950/80" />
        )}

        {/* Gradient overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/60 to-black/30 pointer-events-none" />

        {/* Content */}
        <div className="relative p-6 sm:p-8">
          <div className="flex items-center justify-between mb-1">
            <p className="text-xs font-medium uppercase tracking-wider text-brand-400">
              Developer Spotlight
            </p>
            <Link
              to={`/explore/developers/${encodeURIComponent(spotlight.name)}`}
              className="inline-flex items-center gap-1 text-sm text-surface-300 hover:text-brand-400 transition-colors"
            >
              See all games
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          <h2 className="text-2xl font-bold text-white mb-2">
            {spotlight.name}
          </h2>
          <p className="text-sm text-white/70 mb-6">
            {spotlight.gameCount}{" "}
            {spotlight.gameCount === 1 ? "game" : "games"}
            {spotlight.avgRating > 0 && (
              <> | Avg rating: {spotlight.avgRating.toFixed(1)}</>
            )}
            {spotlight.consoles.length > 0 && (
              <> | {spotlight.consoles.join(", ")}</>
            )}
          </p>

          {/* Scrollable game row */}
          {spotlight.topGames.length > 0 && (
            <div className="relative">
              {/* Scroll arrows */}
              {canScrollLeft && (
                <button
                  onClick={() => scroll("left")}
                  className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/spotlight:opacity-100 group-focus-within/spotlight:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
                  aria-label="Scroll spotlight games left"
                >
                  <ChevronLeft className="h-5 w-5" />
                </button>
              )}
              {canScrollRight && (
                <button
                  onClick={() => scroll("right")}
                  className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/spotlight:opacity-100 group-focus-within/spotlight:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
                  aria-label="Scroll spotlight games right"
                >
                  <ChevronRight className="h-5 w-5" />
                </button>
              )}

              <div
                ref={scrollRef}
                className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
                style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
                role="list"
                aria-label="Developer spotlight games"
              >
                {spotlight.topGames.map((game) => (
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
          )}
        </div>
      </div>
    </section>
  );
}
