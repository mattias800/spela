import { Star } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { StarRating } from "@/features/game-detail/components/star-rating";
import { useGameRatingSummary } from "@/hooks/use-ratings";

function DistributionBar({
  star,
  count,
  maxCount,
}: {
  star: number;
  count: number;
  maxCount: number;
}) {
  const percentage = maxCount > 0 ? (count / maxCount) * 100 : 0;

  return (
    <div data-comp="DistributionBar" className="flex items-center gap-2">
      <span className="text-xs text-surface-400 w-4 text-right">{star}</span>
      <Star className="h-3 w-3 text-amber-400 fill-amber-400 flex-shrink-0" />
      <div className="flex-1 h-2 rounded-full bg-surface-800 overflow-hidden">
        <div
          className="h-full rounded-full bg-amber-400 transition-all duration-300"
          style={{ width: `${percentage}%` }}
        />
      </div>
      <span className="text-xs text-surface-500 w-6 text-right">{count}</span>
    </div>
  );
}

function RatingSummarySkeleton() {
  return (
    <div data-comp="RatingSummarySkeleton" className="space-y-3">
      <div className="flex items-center gap-3">
        <Skeleton className="h-10 w-16" />
        <Skeleton className="h-5 w-28" />
      </div>
      {[5, 4, 3, 2, 1].map((i) => (
        <div key={i} className="flex items-center gap-2">
          <Skeleton className="h-3 w-4" />
          <Skeleton className="h-3 w-3" />
          <Skeleton className="h-2 flex-1" />
          <Skeleton className="h-3 w-6" />
        </div>
      ))}
    </div>
  );
}

interface RatingSummaryProps {
  gameId: string;
}

export function RatingSummaryCard({ gameId }: RatingSummaryProps) {
  const { data: summary, isLoading } = useGameRatingSummary(gameId);

  if (isLoading) {
    return (
      <div data-testid="rating-summary">
        <div className="flex items-center gap-2.5 mb-4">
          <Star className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Ratings</h2>
        </div>
        <RatingSummarySkeleton />
      </div>
    );
  }

  if (!summary || summary.totalRatings === 0) {
    return (
      <div data-testid="rating-summary">
        <div className="flex items-center gap-2.5 mb-4">
          <Star className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Ratings</h2>
        </div>
        <EmptyState
          icon={Star}
          title="No ratings yet"
          description="Be the first to rate this game."
          className="py-8"
        />
      </div>
    );
  }

  const maxCount = Math.max(...Object.values(summary.distribution));

  return (
    <div data-comp="RatingSummaryCard" data-testid="rating-summary">
      <div className="flex items-center gap-2.5 mb-4">
        <Star className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-semibold text-surface-100">Ratings</h2>
      </div>

      <div className="flex items-center gap-4 mb-4">
        <span className="text-4xl font-bold text-surface-100">
          {summary.averageRating.toFixed(1)}
        </span>
        <div>
          <StarRating
            value={Math.round(summary.averageRating)}
            readonly
            size="sm"
          />
          <p className="text-xs text-surface-500 mt-0.5">
            {summary.totalRatings}{" "}
            {summary.totalRatings === 1 ? "rating" : "ratings"}
          </p>
        </div>
      </div>

      <div className="space-y-1.5">
        {[5, 4, 3, 2, 1].map((star) => (
          <DistributionBar
            key={star}
            star={star}
            count={summary.distribution[String(star)] ?? 0}
            maxCount={maxCount}
          />
        ))}
      </div>
    </div>
  );
}
