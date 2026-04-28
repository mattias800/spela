import { useState } from "react";
import { Star } from "lucide-react";
import { cn } from "@/lib/cn";

interface StarRatingProps {
  value: number;
  onChange?: (rating: number) => void;
  size?: "sm" | "md" | "lg";
  readonly?: boolean;
}

const sizeMap = {
  sm: "h-4 w-4",
  md: "h-5 w-5",
  lg: "h-6 w-6",
};

export function StarRating({
  value,
  onChange,
  size = "md",
  readonly = false,
}: StarRatingProps) {
  const [hovered, setHovered] = useState(0);

  const displayValue = hovered || value;
  const interactive = !readonly && !!onChange;

  return (
    <div data-comp="StarRating"
      className="inline-flex items-center gap-0.5"
      onMouseLeave={() => interactive && setHovered(0)}
      data-testid="star-rating"
    >
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          disabled={!interactive}
          className={cn(
            "transition-colors",
            interactive
              ? "cursor-pointer hover:scale-110 transition-transform"
              : "cursor-default",
          )}
          onMouseEnter={() => interactive && setHovered(star)}
          onClick={() => interactive && onChange(star)}
          data-testid={`star-${star}`}
        >
          <Star
            className={cn(
              sizeMap[size],
              star <= displayValue
                ? "text-amber-400 fill-amber-400"
                : "text-surface-600",
            )}
          />
        </button>
      ))}
    </div>
  );
}
