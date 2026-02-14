import { useState } from "react";
import { Star, Trash2 } from "lucide-react";
import { Button } from "@/components/ui";
import { StarRating } from "@/components/game-detail/star-rating";
import { useMyRating, useRateGame, useDeleteRating } from "@/hooks/use-ratings";

interface UserRatingProps {
  gameId: string;
}

export function UserRating({ gameId }: UserRatingProps) {
  const { data: myRating } = useMyRating(gameId);
  const rateGame = useRateGame();
  const deleteRating = useDeleteRating();
  const [review, setReview] = useState("");
  const [showReviewInput, setShowReviewInput] = useState(false);

  const currentRating = myRating?.rating ?? 0;
  const currentReview = myRating?.review ?? "";

  const handleRate = (rating: number) => {
    rateGame.mutate({
      gameId,
      rating,
      review: showReviewInput ? review : currentReview,
    });
  };

  const handleSubmitReview = () => {
    if (currentRating === 0) return;
    rateGame.mutate({
      gameId,
      rating: currentRating,
      review,
    });
    setShowReviewInput(false);
  };

  const handleDelete = () => {
    deleteRating.mutate(gameId);
    setReview("");
    setShowReviewInput(false);
  };

  return (
    <div className="rounded-xl bg-surface-900/50 p-4" data-testid="user-rating">
      <div className="flex items-center gap-2.5 mb-3">
        <Star className="h-4 w-4 text-brand-400" />
        <h3 className="text-sm font-semibold text-surface-200">Your Rating</h3>
      </div>

      <div className="flex items-center gap-3">
        <StarRating value={currentRating} onChange={handleRate} size="lg" />
        {currentRating > 0 && (
          <button
            onClick={handleDelete}
            className="p-1.5 rounded-lg text-surface-500 hover:text-danger-500 hover:bg-danger-500/10 transition-colors"
            title="Remove rating"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        )}
      </div>

      {currentRating > 0 && !showReviewInput && !currentReview && (
        <button
          onClick={() => setShowReviewInput(true)}
          className="text-xs text-surface-500 hover:text-brand-400 transition-colors mt-2"
        >
          Add a review...
        </button>
      )}

      {currentReview && !showReviewInput && (
        <div className="mt-2">
          <p className="text-sm text-surface-400">{currentReview}</p>
          <button
            onClick={() => {
              setReview(currentReview);
              setShowReviewInput(true);
            }}
            className="text-xs text-surface-500 hover:text-brand-400 transition-colors mt-1"
          >
            Edit review
          </button>
        </div>
      )}

      {showReviewInput && (
        <div className="mt-3 space-y-2">
          <textarea
            value={review}
            onChange={(e) => setReview(e.target.value)}
            placeholder="Write a review..."
            className="w-full rounded-lg bg-surface-800 border border-surface-700 px-3 py-2 text-sm text-surface-200 placeholder:text-surface-500 focus:outline-none focus:ring-2 focus:ring-brand-500 resize-none"
            rows={3}
          />
          <div className="flex gap-2">
            <Button size="sm" onClick={handleSubmitReview}>
              Save Review
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowReviewInput(false)}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
