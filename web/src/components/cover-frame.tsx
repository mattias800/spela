import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

interface CoverFrameProps {
  /** Image URL, or null/undefined to show the placeholder. */
  src?: string | null;
  /** Alt text; first character is the default placeholder. */
  alt: string;
  /** Fixed-height mode: frame width follows the image's aspect ratio. */
  coverHeight?: number;
  /** Fixed-ratio mode: box keeps this ratio, image crops to fill. */
  aspectRatio?: number | string;
  /** Ratio used to size the placeholder when there is no image. */
  placeholderAspectRatio?: number | string;
  /** Placeholder content; defaults to the first character of `alt`. */
  placeholder?: ReactNode;
  placeholderClassName?: string;
  imgClassName?: string;
  className?: string;
  /** Absolutely-positioned overlays (badges, buttons, link hit areas). */
  children?: ReactNode;
}

/**
 * DESIGN component — the sizing frame for cover art.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Owns the cover sizing contract shared by all card covers so Content
 * components cannot diverge (#1672):
 *
 * - `coverHeight` set (carousel rows): the frame is `height: coverHeight;
 *   width: fit-content` and the image is a DIRECT child with `h-full w-auto`,
 *   so the card's width follows the cover's aspect ratio and the cover is
 *   fully visible. The percentage-height chain only resolves for a direct
 *   child — never wrap the image in an intermediate element here.
 * - `aspectRatio` set (grid tiles): fixed-ratio box, image crops to fill.
 * - neither: fluid — image spans the parent's width at its natural ratio.
 */
export function CoverFrame({
  src,
  alt,
  coverHeight,
  aspectRatio,
  placeholderAspectRatio = aspectRatio ?? "3/4",
  placeholder,
  placeholderClassName = "bg-surface-800",
  imgClassName,
  className,
  children,
}: CoverFrameProps) {
  return (
    <div
      data-comp="CoverFrame"
      className={cn("relative overflow-hidden", className)}
      style={
        coverHeight
          ? { height: coverHeight, width: "fit-content" }
          : aspectRatio
            ? { aspectRatio }
            : undefined
      }
    >
      {src ? (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          className={cn(
            coverHeight
              ? "h-full w-auto"
              : aspectRatio
                ? "h-full w-full object-cover"
                : "w-full",
            imgClassName,
          )}
        />
      ) : (
        <div
          className={cn(
            "flex items-center justify-center",
            !coverHeight && aspectRatio && "h-full w-full",
            placeholderClassName,
          )}
          style={
            coverHeight
              ? { height: coverHeight, aspectRatio: placeholderAspectRatio }
              : aspectRatio
                ? undefined
                : { aspectRatio: placeholderAspectRatio }
          }
        >
          {placeholder ?? (
            <span className="text-lg font-bold text-surface-600">
              {alt.charAt(0).toUpperCase()}
            </span>
          )}
        </div>
      )}
      {children}
    </div>
  );
}
