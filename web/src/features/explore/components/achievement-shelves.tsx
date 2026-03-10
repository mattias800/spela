import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  Award,
  Mountain,
  Target,
  Sparkles,
  Swords,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Skeleton } from "@/components/ui";
import type {
  Game,
  EasyToCompleteResponse,
  HardestGamesResponse,
  AlmostDoneResponse,
  FreshChallengesResponse,
  ActiveChallengesResponse,
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

// --- Easy to Complete Shelf ---

interface EasyToCompleteShelfProps {
  data: EasyToCompleteResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function EasyToCompleteShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: EasyToCompleteShelfProps) {
  return (
    <ScrollShelf
      title="Easy to 100%"
      subtitle="Games with the highest achievement completion rates"
      icon={Award}
      testId="easy-to-complete-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((item) => (
        <div
          key={item.game.id}
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
            className="text-xs text-surface-400 mt-1.5"
            data-testid="avg-completion"
          >
            {item.avgCompletion}% avg completion
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Hardest Games Shelf ---

interface HardestGamesShelfProps {
  data: HardestGamesResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function HardestGamesShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: HardestGamesShelfProps) {
  return (
    <ScrollShelf
      title="Mount Everest"
      subtitle="The toughest achievement challenges"
      icon={Mountain}
      testId="hardest-games-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((item) => (
        <div
          key={item.game.id}
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
            className="text-xs text-surface-400 mt-1.5"
            data-testid="hardest-completion"
          >
            {item.avgCompletion}% avg · {item.playersCompleted}/{item.playersAttempted} completed
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Almost Done Shelf ---

interface AlmostDoneShelfProps {
  data: AlmostDoneResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function AlmostDoneShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: AlmostDoneShelfProps) {
  return (
    <ScrollShelf
      title="Almost Done"
      subtitle="You're so close to 100%!"
      icon={Target}
      testId="almost-done-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((item) => (
        <div
          key={item.game.id}
          className="w-40 sm:w-44 lg:w-48 flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <div className="mt-1.5">
            <div
              className="w-full h-1.5 bg-surface-700 rounded-full overflow-hidden"
              role="progressbar"
              aria-valuenow={item.completionPercent}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label={`${item.unlockedCount} of ${item.totalCount} achievements unlocked`}
            >
              <div
                className="h-full bg-brand-500 rounded-full transition-all"
                style={{ width: `${item.completionPercent}%` }}
                data-testid="progress-bar"
              />
            </div>
            <p
              className="text-xs text-surface-400 mt-1"
              data-testid="achievement-progress"
            >
              {item.unlockedCount}/{item.totalCount} achievements ({item.completionPercent}%)
            </p>
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Fresh Challenges Shelf ---

interface FreshChallengesShelfProps {
  data: FreshChallengesResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function FreshChallengesShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: FreshChallengesShelfProps) {
  return (
    <ScrollShelf
      title="Fresh Achievement Challenges"
      subtitle="Games with achievements you haven't started yet"
      icon={Sparkles}
      testId="fresh-challenges-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((item) => (
        <div
          key={item.game.id}
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
            className="text-xs text-surface-400 mt-1.5"
            data-testid="fresh-challenge-info"
          >
            {item.totalAchievements} achievements &middot; {item.totalPoints} points
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Active Challenges Shelf ---

interface ActiveChallengesShelfProps {
  data: ActiveChallengesResponse | undefined;
  isLoading: boolean;
}

export function ActiveChallengesShelf({
  data,
  isLoading,
}: ActiveChallengesShelfProps) {
  return (
    <ScrollShelf
      title="Active Challenges"
      subtitle="Open challenges from the community"
      icon={Swords}
      testId="active-challenges-shelf"
      isLoading={isLoading}
      isEmpty={!data?.challenges || data.challenges.length === 0}
    >
      {data?.challenges.map((ch) => (
        <div key={ch.id} className="w-64 flex-shrink-0" role="listitem">
          <Link to={`/challenges/${ch.id}`} className="block">
            <div className="bg-surface-800 rounded-lg p-4 hover:bg-surface-700 transition-colors">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-brand-500/20 text-brand-400">
                  {ch.type}
                </span>
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-surface-600 text-surface-300">
                  {ch.difficulty}
                </span>
              </div>
              <h3
                className="text-sm font-semibold text-surface-100 truncate"
                data-testid="challenge-name"
              >
                {ch.name}
              </h3>
              <p className="text-xs text-surface-400 truncate mt-1">
                {ch.gameTitle}
              </p>
              <div className="flex items-center gap-3 mt-3 text-xs text-surface-400">
                <span>{ch.attemptCount} attempts</span>
                <span>{ch.completionCount} completed</span>
              </div>
            </div>
          </Link>
        </div>
      ))}
    </ScrollShelf>
  );
}
