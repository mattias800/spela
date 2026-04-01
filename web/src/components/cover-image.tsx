import { cn } from "@/lib/cn";

interface CoverImageProps {
  /** Image URL, or null/undefined to show placeholder. */
  src?: string | null;
  /** Alt text for accessibility. First character used as placeholder. */
  alt: string;
  className?: string;
}

/**
 * DESIGN component — renders a cover art image with fallback placeholder.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Handles image rendering with lazy loading and a character-based
 * placeholder when no image is available. Fills its container.
 */
export function CoverImage({ src, alt, className }: CoverImageProps) {
  return (
    <div
      className={cn(
        "overflow-hidden bg-surface-800 flex-shrink-0",
        className,
      )}
    >
      {src ? (
        <img
          src={src}
          alt={alt}
          className="h-full w-full object-cover"
          loading="lazy"
        />
      ) : (
        <div className="h-full w-full flex items-center justify-center text-lg font-bold text-surface-600">
          {alt.charAt(0).toUpperCase()}
        </div>
      )}
    </div>
  );
}
