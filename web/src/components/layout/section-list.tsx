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
 * This component is allowed to own its own gap because it is a core layout
 * component. See AGENTS.md in this directory.
 */
export function SectionList({ children, className }: SectionListProps) {
  return (
    <div data-comp="SectionList" className={cn("flex flex-col gap-8", className)}>
      {children}
    </div>
  );
}
