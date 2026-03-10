import { useRef, useState, useEffect, useCallback } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Calendar,
  Trophy,
  Heart,
  Clock,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Skeleton } from "@/components/ui";
import type {
  Game,
  OnThisDayResponse,
  BestOfYearResponse,
  AnniversaryItem,
  DecadeResponse,
} from "@/types/api";

// --- Shared scroll shelf wrapper (same pattern as social-shelves.tsx) ---

function ScrollShelf({
  title,
  subtitle,
  icon: Icon,
  testId,
  isLoading,
  isEmpty,
  children,
}: {
  title: string;
  subtitle?: string;
  icon: React.ElementType;
  testId: string;
  isLoading: boolean;
  isEmpty: boolean;
  children: React.ReactNode;
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
  }, [updateScrollState, children]);

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
    return (
      <section data-testid={`${testId}-skeleton`}>
        <div className="flex items-center gap-2 mb-1">
          <Icon className="h-5 w-5 text-surface-400" />
          <Skeleton className="h-7 w-60 rounded" />
        </div>
        {subtitle && (
          <Skeleton className="h-4 w-40 rounded mt-1 mb-5" />
        )}
        <div className="flex gap-5 overflow-hidden mt-4">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
              <GameCardSkeleton />
            </div>
          ))}
        </div>
      </section>
    );
  }

  if (isEmpty) return null;

  return (
    <section data-testid={testId} className="group/shelf relative">
      <div className="flex items-center gap-2 mb-1">
        <Icon className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">{title}</h2>
      </div>
      {subtitle && (
        <p className="text-sm text-surface-400 mb-4">{subtitle}</p>
      )}

      <div className="relative">
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all duration-300 shadow-lg"
            aria-label={`Scroll ${title} left`}
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all duration-300 shadow-lg"
            aria-label={`Scroll ${title} right`}
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        <div
          ref={scrollRef}
          className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label={title}
        >
          {children}
        </div>
      </div>
    </section>
  );
}

// --- On This Day Shelf ---

interface OnThisDayShelfProps {
  data: OnThisDayResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function OnThisDayShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: OnThisDayShelfProps) {
  const dateLabel = data?.date ?? "";

  return (
    <ScrollShelf
      title={dateLabel ? `On This Day in Gaming — ${dateLabel}` : "On This Day in Gaming"}
      subtitle="Games released on this date across the years"
      icon={Calendar}
      testId="on-this-day-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((game) => (
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
          {game.releaseDate && (
            <p
              className="text-xs text-surface-400 mt-1.5"
              data-testid="release-year"
            >
              Released {new Date(game.releaseDate).getFullYear()}
            </p>
          )}
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Best of Year Section ---

const YEAR_RANGE_START = 1985;
const YEAR_RANGE_END = 2005;
const DEFAULT_YEAR = 1995;

interface BestOfYearSectionProps {
  year: number;
  onYearChange: (year: number) => void;
  data: BestOfYearResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function BestOfYearSection({
  year,
  onYearChange,
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: BestOfYearSectionProps) {
  if (isLoading) {
    return (
      <section data-testid="best-of-year-skeleton">
        <div className="flex items-center gap-2 mb-1">
          <Trophy className="h-5 w-5 text-surface-400" />
          <Skeleton className="h-7 w-60 rounded" />
        </div>
        <Skeleton className="h-4 w-40 rounded mt-1 mb-5" />
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-5 mt-4">
          {Array.from({ length: 6 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </div>
      </section>
    );
  }

  if (!data?.games || data.games.length === 0) return null;

  return (
    <section data-testid="best-of-year-section">
      <div className="flex items-center gap-2 mb-1">
        <Trophy className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">
          Best of {year}
        </h2>
      </div>
      <p className="text-sm text-surface-400 mb-4">
        Top games from {year} in your library
      </p>

      {/* Year selector */}
      <div
        className="flex flex-wrap gap-1.5 mb-6"
        data-testid="year-selector"
        role="group"
        aria-label="Select year"
      >
        {Array.from(
          { length: YEAR_RANGE_END - YEAR_RANGE_START + 1 },
          (_, i) => YEAR_RANGE_START + i,
        ).map((y) => (
          <button
            key={y}
            onClick={() => onYearChange(y)}
            className={`px-2.5 py-1 text-xs rounded-full transition-colors ${
              y === year
                ? "bg-brand-500 text-white font-semibold"
                : "bg-surface-800 text-surface-400 hover:bg-surface-700 hover:text-surface-200"
            }`}
            aria-pressed={y === year}
          >
            {y}
          </button>
        ))}
      </div>

      {/* Game grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-5">
        {data.games.map((game) => (
          <GameCard
            key={game.id}
            game={game}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        ))}
      </div>
    </section>
  );
}

// --- Anniversaries Shelf ---

interface AnniversariesShelfProps {
  anniversaries: AnniversaryItem[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function AnniversariesShelf({
  anniversaries,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: AnniversariesShelfProps) {
  return (
    <ScrollShelf
      title="Your Gaming Anniversaries"
      subtitle="Milestones from your play history"
      icon={Heart}
      testId="anniversaries-shelf"
      isLoading={isLoading}
      isEmpty={!anniversaries || anniversaries.length === 0}
    >
      {anniversaries?.map((item) => (
        <div
          key={`${item.game.id}-${item.yearsAgo}`}
          className="w-40 sm:w-44 lg:w-48 flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <p
            className="text-xs text-amber-400 mt-1.5 font-medium"
            data-testid="anniversary-label"
          >
            {item.yearsAgo} year{item.yearsAgo !== 1 ? "s" : ""} ago you
            played this
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Decade Spotlight ---

interface DecadeSpotlightProps {
  data: DecadeResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function DecadeSpotlight({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: DecadeSpotlightProps) {
  return (
    <ScrollShelf
      title={data?.label ?? "Decade Spotlight"}
      subtitle={`The defining games of the ${data?.decade ?? "era"}`}
      icon={Clock}
      testId="decade-spotlight"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((game) => (
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
    </ScrollShelf>
  );
}

export { DEFAULT_YEAR, YEAR_RANGE_START, YEAR_RANGE_END };
