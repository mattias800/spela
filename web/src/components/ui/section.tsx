import type { HTMLAttributes, ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

interface SectionProps extends HTMLAttributes<HTMLDivElement> {
  hover?: boolean;
}

/**
 * DESIGN component — a container with surface background, border, and rounded corners.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Use for grouping related content. For sections with a title, use TitledSection instead.
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

interface TitledSectionProps {
  title: string;
  icon?: LucideIcon;
  /** Optional content rendered on the right side of the title bar (e.g. "View all" link). */
  renderRight?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
}

/**
 * DESIGN component — a section container with a title bar.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Renders a title (with optional icon and right-side content) above
 * the section's children.
 */
export function TitledSection({
  title,
  icon: Icon,
  renderRight,
  children,
  className,
  id,
}: TitledSectionProps) {
  return (
    <div data-comp="TitledSection" className={className}>
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-2.5">
          {Icon && <Icon className="h-5 w-5 text-brand-400" />}
          <h2 id={id} className="text-xl font-bold text-surface-100">
            {title}
          </h2>
        </div>
        {renderRight}
      </div>
      {children}
    </div>
  );
}

interface SectionListProps {
  children: ReactNode;
  className?: string;
}

/**
 * DESIGN component — a vertical list of sections with standardized spacing.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Use as the top-level container for a page's section layout.
 */
export function SectionList({ children, className }: SectionListProps) {
  return (
    <div data-comp="SectionList" className={cn("flex flex-col gap-8", className)}>
      {children}
    </div>
  );
}
