import { useState } from "react";
import { MessageSquare } from "lucide-react";
import { Button, Skeleton, EmptyState } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { StarRating } from "@/features/game-detail/components/star-rating";
import { formatRelativeTime } from "@/lib/format";
import { useGameRatings } from "@/hooks/use-ratings";

function ReviewsSkeleton() {
  return (
    <div data-comp="ReviewsSkeleton" className="space-y-4">
      {Array.from({ length: 3 }, (_, i) => (
        <div
          key={i}
          className="flex items-start gap-3 px-4 py-3 rounded-xl bg-surface-800/30"
        >
          <Skeleton className="h-8 w-8 rounded-full flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-3 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}

interface GameReviewsProps {
  gameId: string;
}

export function GameReviews({ gameId }: GameReviewsProps) {
  const [page, setPage] = useState(1);
  const pageSize = 10;
  const { data, isLoading } = useGameRatings(gameId, page, pageSize);

  const hasMore = data ? page * pageSize < data.total : false;

  return (
    <div data-comp="GameReviews" data-testid="game-reviews">
      <div className="flex items-center gap-2.5 mb-4">
        <MessageSquare className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-semibold text-surface-100">Reviews</h2>
        {data && data.total > 0 && (
          <span className="text-sm text-surface-500">({data.total})</span>
        )}
      </div>

      {isLoading && page === 1 && <ReviewsSkeleton />}

      {!isLoading && (!data?.data || data.data.length === 0) && (
        <EmptyState
          icon={MessageSquare}
          title="No reviews yet"
          description="Rate this game and add a review to share your thoughts."
          className="py-8"
        />
      )}

      {data?.data && data.data.length > 0 && (
        <div className="space-y-3">
          {data.data.map((rating) => (
            <div
              key={rating.id}
              className="flex items-start gap-3 px-4 py-3 rounded-xl bg-surface-800/30"
              data-testid={`review-${rating.id}`}
            >
              <PlayerAvatar
                username={rating.username}
                avatarUrl={rating.avatarUrl}
              />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-surface-200">
                    {rating.username}
                  </span>
                  <StarRating value={rating.rating} readonly size="sm" />
                  <span className="text-xs text-surface-500">
                    {formatRelativeTime(rating.createdAt)}
                  </span>
                </div>
                {rating.review && (
                  <p className="text-sm text-surface-400 mt-1">
                    {rating.review}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {data && (data.data?.length ?? 0) > 0 && (
        <div className="flex items-center justify-center gap-3 mt-6">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            disabled={page === 1}
          >
            Previous
          </Button>
          <span className="text-sm text-surface-400">
            Page {page} of {Math.ceil(data.total / pageSize)}
          </span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setPage((p) => p + 1)}
            disabled={!hasMore}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
