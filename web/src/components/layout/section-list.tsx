import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

interface SectionListProps {
  children: ReactNode;
  className?: string;
}

/**
 * Core layout component — a vertical list of sections with standardized spacing.
 *
 * Use as the top-level container for a page's section layout. Direct children
 * should be TitledSection components.
 *
 * Uses block layout with space-y (margin-based) instead of flex gap so that
 * zero-height elements (e.g., IntersectionObserver sentinels) don't create
 * extra gaps — CSS margin collapsing handles them naturally.
 *
 * This component is allowed to own its own gap because it is a core layout
 * component. See AGENTS.md in this directory.
 */
export function SectionList({ children, className }: SectionListProps) {
  return (
    <div data-comp="SectionList" className={cn("space-y-8", className)}>
      {children}
    </div>
  );
}
