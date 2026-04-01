import { Star } from "lucide-react";
import { cn } from "@/lib/cn";

interface RatingDisplayProps {
  /** Rating value to display. Shown as-is for "compact", mapped to 0-5 stars for "stars" (assumes 0-10 scale). */
  value: number;
  /** "compact" = single star + number. "stars" = 5 filled/empty stars + number. */
  variant?: "compact" | "stars";
  className?: string;
}

/**
 * DESIGN component — displays a rating value with star icon(s).
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Defines the visual styling for rating displays — amber color, star
 * sizing, number formatting. No domain knowledge.
 */
export function RatingDisplay({
  value,
  variant = "compact",
  className,
}: RatingDisplayProps) {
  if (variant === "stars") {
    const starValue = value / 2;
    const fullStars = Math.round(starValue);

    return (
      <span data-comp="RatingDisplay" className={cn("inline-flex items-center gap-1", className)}>
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            className={cn(
              "h-3.5 w-3.5",
              star <= fullStars
                ? "text-amber-400 fill-amber-400"
                : "text-surface-600",
            )}
          />
        ))}
        <span className="text-xs text-surface-400 ml-0.5">
          {value.toFixed(1)}
        </span>
      </span>
    );
  }

  return (
    <span
      data-comp="RatingDisplay"
      className={cn(
        "inline-flex items-center gap-1 text-xs text-amber-400 font-medium",
        className,
      )}
    >
      <Star className="h-3.5 w-3.5 fill-amber-400" />
      {value.toFixed(1)}
    </span>
  );
}
