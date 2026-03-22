import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";

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
  children,
}: CoverCardProps) {
  const content = (
    <div className={cn(width, dimmed && "opacity-50")}>
      <div
        className="w-full rounded-xl overflow-hidden bg-surface-900 border border-surface-800/50"
        style={{ aspectRatio }}
      >
        {imageUrl ? (
          <img
            src={imageUrl}
            alt={title}
            className="w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-xl font-bold text-surface-600">
            {title.slice(0, 2).toUpperCase()}
          </div>
        )}
      </div>
      <div className="mt-2 px-0.5">
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
