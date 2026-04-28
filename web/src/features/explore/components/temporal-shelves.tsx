import { getReleaseYear } from "@/lib/date-utils";
import { Calendar, Trophy, Heart, Clock } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Skeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type {
  Game,
  OnThisDayResponse,
  BestOfYearResponse,
  AnniversaryItem,
  DecadeResponse,
} from "@/types/api";

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
      {data?.games?.map((game) => (
        <div
          key={game.id}
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={game}
            showConsoleBadge
            subtitle={getReleaseYear(game.releaseDate) ? `Released ${getReleaseYear(game.releaseDate)}` : undefined}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
            coverHeight={CAROUSEL_CARD_HEIGHT}
          />
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
    <section data-comp="BestOfYearSection" data-testid="best-of-year-section">
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
            className={`px-2.5 py-1 text-xs rounded-full transition-colors cursor-pointer ${
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
  anniversaries: AnniversaryItem[] | null | undefined;
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
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
            coverHeight={CAROUSEL_CARD_HEIGHT}
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
      {data?.games?.map((game) => (
        <div
          key={game.id}
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={game}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
            coverHeight={CAROUSEL_CARD_HEIGHT}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}

export { DEFAULT_YEAR, YEAR_RANGE_START, YEAR_RANGE_END };
