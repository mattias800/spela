import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  TrendingUp,
  Star,
  Gem,
  Radio,
  MessageSquare,
  Users,
  Swords,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Badge, Skeleton } from "@/components/ui";
import type {
  Game,
  TrendingGame,
  CommunityTopGame,
  CultClassicGame,
  RecentReviewItem,
  ActiveNowItem,
} from "@/types/api";

// --- Shared scroll shelf wrapper ---

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

// --- Trending Shelf ---

interface TrendingShelfProps {
  games: TrendingGame[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function TrendingShelf({
  games,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: TrendingShelfProps) {
  return (
    <ScrollShelf
      title="Trending on Your Server"
      subtitle="Most played games this week"
      icon={TrendingUp}
      testId="trending-shelf"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((item) => (
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
          <div className="flex items-center gap-1 mt-1.5 text-xs text-surface-400">
            <Users className="h-3 w-3" />
            <span data-testid="players-count">
              {item.playersThisWeek} player{item.playersThisWeek !== 1 ? "s" : ""} this week
            </span>
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Community Top Shelf ---

interface CommunityTopShelfProps {
  games: CommunityTopGame[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function CommunityTopShelf({
  games,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: CommunityTopShelfProps) {
  return (
    <ScrollShelf
      title="Community Favorites"
      subtitle="Highest rated by players on your server"
      icon={Star}
      testId="community-top-shelf"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((item) => (
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
          <div className="flex items-center gap-1.5 mt-1.5 text-xs text-surface-400">
            <Star className="h-3 w-3 text-amber-400 fill-amber-400" />
            <span data-testid="community-rating">
              {item.avgRating.toFixed(1)}/5
            </span>
            <span className="text-surface-500">
              ({item.ratingCount} rating{item.ratingCount !== 1 ? "s" : ""})
            </span>
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Cult Classics Shelf ---

interface CultClassicsShelfProps {
  games: CultClassicGame[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function CultClassicsShelf({
  games,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: CultClassicsShelfProps) {
  return (
    <ScrollShelf
      title="Cult Classics"
      subtitle="Your community rates these higher than the critics"
      icon={Gem}
      testId="cult-classics-shelf"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((item) => (
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
          <div className="flex items-center gap-1.5 mt-1.5 text-xs text-surface-400">
            <Star className="h-3 w-3 text-amber-400 fill-amber-400" />
            <span data-testid="cult-community-rating">
              {item.communityRating.toFixed(1)}/5
            </span>
            <span className="text-surface-500">vs IGDB {item.igdbRating.toFixed(0)}/100</span>
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Recently Reviewed Shelf ---

interface RecentlyReviewedShelfProps {
  reviews: RecentReviewItem[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function RecentlyReviewedShelf({
  reviews,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: RecentlyReviewedShelfProps) {
  return (
    <ScrollShelf
      title="Recently Reviewed"
      subtitle="Latest reviews from your community"
      icon={MessageSquare}
      testId="recently-reviewed-shelf"
      isLoading={isLoading}
      isEmpty={!reviews || reviews.length === 0}
    >
      {reviews?.map((item) => (
        <div
          key={`${item.game.id}-${item.reviewerName}`}
          className="w-56 sm:w-60 flex-shrink-0"
          role="listitem"
        >
          <div className="flex gap-3">
            <div className="w-24 flex-shrink-0">
              <GameCard
                game={item.game}
                showConsoleBadge={false}
                onToggleFavorite={onToggleFavorite}
                onTogglePlayLater={onTogglePlayLater}
              />
            </div>
            <div className="flex flex-col min-w-0 py-0.5">
              <Link
                to={`/games/${item.game.id}`}
                className="text-sm font-semibold text-surface-100 truncate hover:text-brand-400 transition-colors"
              >
                {item.game.title}
              </Link>
              <div className="flex items-center gap-1 mt-0.5">
                {Array.from({ length: 5 }, (_, si) => (
                  <Star
                    key={si}
                    className={`h-3 w-3 ${
                      si < item.rating
                        ? "text-amber-400 fill-amber-400"
                        : "text-surface-600"
                    }`}
                  />
                ))}
              </div>
              <p
                className="text-xs text-surface-400 mt-1 line-clamp-3"
                data-testid="review-text"
              >
                {item.review}
              </p>
              <p className="text-xs text-surface-500 mt-auto">
                — {item.reviewerName}
              </p>
            </div>
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Active Now Shelf ---

interface ActiveNowShelfProps {
  games: ActiveNowItem[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function ActiveNowShelf({
  games,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: ActiveNowShelfProps) {
  return (
    <ScrollShelf
      title="Active Right Now"
      subtitle="Games with live sessions and challenges"
      icon={Radio}
      testId="active-now-shelf"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((item) => (
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
          <div className="flex flex-wrap gap-1.5 mt-1.5">
            {item.activeSessions > 0 && (
              <Badge
                variant="default"
                className="text-[10px] px-1.5 py-0 gap-1 bg-green-500/15 text-green-400 border-green-500/30"
              >
                <Users className="h-2.5 w-2.5" />
                {item.activeSessions} session{item.activeSessions !== 1 ? "s" : ""}
              </Badge>
            )}
            {item.activeChallenges > 0 && (
              <Badge
                variant="default"
                className="text-[10px] px-1.5 py-0 gap-1 bg-orange-500/15 text-orange-400 border-orange-500/30"
              >
                <Swords className="h-2.5 w-2.5" />
                {item.activeChallenges} challenge{item.activeChallenges !== 1 ? "s" : ""}
              </Badge>
            )}
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}
