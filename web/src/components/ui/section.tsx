import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

interface SectionProps extends HTMLAttributes<HTMLDivElement> {
  hover?: boolean;
}

/**
 * DESIGN component — a container with surface background, border, and rounded corners.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Use for grouping related content. For sections with a title bar, use
 * TitledSection from @/components/layout instead.
 */
export function Section({ className, hover, children, ...props }: SectionProps) {
  return (
    <div
      data-comp="Section"
      className={cn(
        "rounded-2xl bg-surface-900/50 border border-surface-800/50 overflow-hidden",
        hover &&
          "transition-all duration-300 hover:bg-surface-800/60 hover:border-surface-700/50 hover:shadow-xl hover:shadow-black/20 hover:-translate-y-0.5",
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}

// Re-export layout components for backward compatibility.
// New code should import from @/components/layout directly.
export { TitledSection } from "@/components/layout";
export { SectionList } from "@/components/layout";
