import type { HTMLAttributes, ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/cn";

interface TitledSectionProps extends HTMLAttributes<HTMLDivElement> {
  title: string;
  icon?: LucideIcon;
  /** Optional content rendered on the right side of the title bar (e.g. "View all" link). */
  renderRight?: ReactNode;
  /** When true, renders a subtle card background behind the section. */
  contained?: boolean;
  children: ReactNode;
}

/**
 * Core layout component — a section with a title bar and consistent spacing.
 *
 * Always renders the heading at the same horizontal position regardless of
 * the `contained` prop. When `contained` is true, a subtle card background
 * wraps the section. This makes headings visually consistent across a page
 * whether sections have card backgrounds or not.
 *
 * This component is allowed to own its own padding (px-5 pt-5 pb-4) because
 * it is a core layout component. See AGENTS.md in this directory.
 */
export function TitledSection({
  title,
  icon: Icon,
  renderRight,
  contained = false,
  children,
  className,
  id,
  ...rest
}: TitledSectionProps) {
  return (
    <div
      data-comp="TitledSection"
      className={cn(
        "px-5 pt-5 pb-4",
        contained && "rounded-2xl bg-white/[0.03]",
        className,
      )}
      {...rest}
    >
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
