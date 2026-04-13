import { cn } from "@/lib/cn";

interface CoverImageProps {
  /** Image URL, or null/undefined to show placeholder. */
  src?: string | null;
  /** Alt text for accessibility. First character used as placeholder. */
  alt: string;
  className?: string;
  /** How the image fits its container. Default "cover" crops to fill. */
  objectFit?: "cover" | "contain";
}

/**
 * DESIGN component — renders a cover art image with fallback placeholder.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Handles image rendering with lazy loading and a character-based
 * placeholder when no image is available. Fills its container.
 */
export function CoverImage({ src, alt, className, objectFit = "cover" }: CoverImageProps) {
  return (
    <div
      data-comp="CoverImage"
      className={cn(
        "overflow-hidden bg-surface-800 flex-shrink-0",
        className,
      )}
    >
      {src ? (
        <img
          src={src}
          alt={alt}
          className={cn("h-full w-full", objectFit === "cover" ? "object-cover" : "object-contain")}
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
