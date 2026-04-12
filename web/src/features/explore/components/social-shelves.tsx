import { Link } from "react-router-dom";
import {
  TrendingUp,
  Star,
  Gem,
  Radio,
  MessageSquare,
  Users,
  Swords,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { Badge } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type {
  Game,
  TrendingGame,
  CommunityTopGame,
  CultClassicGame,
  RecentReviewItem,
  ActiveNowItem,
} from "@/types/api";

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
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
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
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
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
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <div className="flex items-center gap-1.5 mt-1.5 text-xs text-surface-400">
            <Star className="h-3 w-3 text-amber-400 fill-amber-400" />
            <span data-testid="cult-community-rating">
              {item.communityRating.toFixed(1)}/5
            </span>
            <span className="text-surface-500">vs IGDB {item.igdbCriticsRating.toFixed(0)}/100</span>
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
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
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
