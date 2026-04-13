import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";
import { CoverImage } from "@/components/cover-image";

/**
 * CONTENT component — a cover art card with title and subtitle.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Renders a cover image with title, subtitle, and an optional extras slot
 * (for badges, ratings, etc). No domain knowledge — accepts generic props.
 *
 * Role components (TopRatedGameCard, etc.) should delegate to this.
 */

interface CoverCardProps {
  imageUrl?: string | null;
  title: string;
  subtitle?: string;
  linkTo?: string;
  dimmed?: boolean;
  aspectRatio?: string;
  width?: string;
  coverHeight?: number;
  /** Content rendered over the cover image (badges, tags, etc). */
  overlay?: ReactNode;
  children?: ReactNode;
}

export function CoverCard({
  imageUrl,
  title,
  subtitle,
  linkTo,
  dimmed = false,
  aspectRatio = "3/4",
  width = "w-36",
  coverHeight,
  overlay,
  children,
}: CoverCardProps) {
  const content = (
    <div data-comp="CoverCard" className={cn(coverHeight ? "flex-shrink-0 inline-block" : width, dimmed && "opacity-50")}>
      <div
        className="relative border border-surface-800/50"
        style={coverHeight ? { height: coverHeight, width: "fit-content" } : { aspectRatio }}
      >
        <CoverImage src={imageUrl} alt={title} className={coverHeight ? "h-full w-auto rounded-xl" : "w-full h-full rounded-xl"} objectFit={coverHeight ? "contain" : "cover"} />
        {overlay && (
          <div className="absolute bottom-2 left-2 z-10">{overlay}</div>
        )}
      </div>
      <div className={cn("mt-2 px-0.5", coverHeight && "w-0 min-w-full overflow-hidden")}>
        <p className="text-sm font-medium text-surface-200 truncate">
          {title}
        </p>
        {subtitle && (
          <p className="text-xs text-surface-500 truncate mt-0.5">
            {subtitle}
          </p>
        )}
        {children && <div className="mt-1">{children}</div>}
      </div>
    </div>
  );

  if (linkTo) {
    return (
      <Link
        to={linkTo}
        className="group block hover:opacity-80 transition-opacity"
      >
        {content}
      </Link>
    );
  }

  return content;
}
