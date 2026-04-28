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

// Shared row + footer primitives used across most shelves. The 4
// GameCard-based shelves used to duplicate the same wrapper; now
// they delegate to [StandardShelfRow]. [RecentlyReviewedShelf]
// diverges enough (2-column layout with an inline review excerpt)
// that it uses [ReviewCard] instead.

interface ShelfGameHandlers {
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

interface StandardShelfRowProps extends ShelfGameHandlers {
  game: Game;
  footer?: React.ReactNode;
}

function StandardShelfRow({
  game,
  footer,
  onToggleFavorite,
  onTogglePlayLater,
}: StandardShelfRowProps) {
  return (
    <div data-comp="StandardShelfRow" className="flex-shrink-0" role="listitem">
      <GameCard
        game={game}
        showConsoleBadge
        coverHeight={CAROUSEL_CARD_HEIGHT}
        onToggleFavorite={onToggleFavorite}
        onTogglePlayLater={onTogglePlayLater}
      />
      {footer}
    </div>
  );
}

function ShelfFooter({ children }: { children: React.ReactNode }) {
  return (
    <div data-comp="ShelfFooter" className="flex items-center gap-1.5 mt-1.5 text-xs text-surface-400">
      {children}
    </div>
  );
}

// ─── Trending Shelf ───────────────────────────────────────────────

interface TrendingShelfProps extends ShelfGameHandlers {
  games: TrendingGame[] | null | undefined;
  isLoading: boolean;
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
        <StandardShelfRow
          key={item.game.id}
          game={item.game}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
          footer={
            <ShelfFooter>
              <Users className="h-3 w-3" />
              <span data-testid="players-count">
                {item.playersThisWeek} player{item.playersThisWeek !== 1 ? "s" : ""} this week
              </span>
            </ShelfFooter>
          }
        />
      ))}
    </ScrollShelf>
  );
}

// ─── Community Top Shelf ──────────────────────────────────────────

interface CommunityTopShelfProps extends ShelfGameHandlers {
  games: CommunityTopGame[] | null | undefined;
  isLoading: boolean;
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
        <StandardShelfRow
          key={item.game.id}
          game={item.game}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
          footer={
            <ShelfFooter>
              <Star className="h-3 w-3 text-amber-400 fill-amber-400" />
              <span data-testid="community-rating">
                {item.avgRating.toFixed(1)}/5
              </span>
              <span className="text-surface-500">
                ({item.ratingCount} rating{item.ratingCount !== 1 ? "s" : ""})
              </span>
            </ShelfFooter>
          }
        />
      ))}
    </ScrollShelf>
  );
}

// ─── Cult Classics Shelf ──────────────────────────────────────────

interface CultClassicsShelfProps extends ShelfGameHandlers {
  games: CultClassicGame[] | null | undefined;
  isLoading: boolean;
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
        <StandardShelfRow
          key={item.game.id}
          game={item.game}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
          footer={
            <ShelfFooter>
              <Star className="h-3 w-3 text-amber-400 fill-amber-400" />
              <span data-testid="cult-community-rating">
                {item.communityRating.toFixed(1)}/5
              </span>
              <span className="text-surface-500">
                vs IGDB {item.igdbCriticsRating.toFixed(0)}/100
              </span>
            </ShelfFooter>
          }
        />
      ))}
    </ScrollShelf>
  );
}

// ─── Recently Reviewed Shelf ──────────────────────────────────────

interface RecentlyReviewedShelfProps extends ShelfGameHandlers {
  reviews: RecentReviewItem[] | null | undefined;
  isLoading: boolean;
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
        <ReviewCard
          key={`${item.game.id}-${item.reviewerName}`}
          review={item}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
        />
      ))}
    </ScrollShelf>
  );
}

interface ReviewCardProps extends ShelfGameHandlers {
  review: RecentReviewItem;
}

// Exported so profile / game-detail pages can render the same
// treatment without duplicating the 5-star + line-clamp-3 layout.
export function ReviewCard({
  review,
  onToggleFavorite,
  onTogglePlayLater,
}: ReviewCardProps) {
  return (
    <div data-comp="ReviewCard" className="w-56 sm:w-60 flex-shrink-0" role="listitem">
      <div className="flex gap-3">
        <div className="w-24 flex-shrink-0">
          <GameCard
            game={review.game}
            showConsoleBadge={false}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
        <div className="flex flex-col min-w-0 py-0.5">
          <Link
            to={`/games/${review.game.id}`}
            className="text-sm font-semibold text-surface-100 truncate hover:text-brand-400 transition-colors"
          >
            {review.game.title}
          </Link>
          <div className="flex items-center gap-1 mt-0.5">
            {Array.from({ length: 5 }, (_, i) => (
              <Star
                key={i}
                className={`h-3 w-3 ${
                  i < review.rating
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
            {review.review}
          </p>
          <p className="text-xs text-surface-500 mt-auto">
            — {review.reviewerName}
          </p>
        </div>
      </div>
    </div>
  );
}

// ─── Active Now Shelf ─────────────────────────────────────────────

interface ActiveNowShelfProps extends ShelfGameHandlers {
  games: ActiveNowItem[] | null | undefined;
  isLoading: boolean;
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
        <StandardShelfRow
          key={item.game.id}
          game={item.game}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
          footer={
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
          }
        />
      ))}
    </ScrollShelf>
  );
}
