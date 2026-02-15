import { type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { cn } from "@/lib/cn";
import type { LucideIcon } from "lucide-react";

interface TabNavProps {
  children: ReactNode;
  className?: string;
}

export function TabNav({ children, className }: TabNavProps) {
  return (
    <nav
      className={cn(
        "flex flex-wrap items-center gap-1 border-b border-surface-800/50",
        className,
      )}
    >
      {children}
    </nav>
  );
}

interface TabItemProps {
  to: string;
  icon?: LucideIcon;
  label: string;
}

export function TabItem({ to, icon: Icon, label }: TabItemProps) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        cn(
          "flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors border-b-2 -mb-px",
          isActive
            ? "border-brand-500 text-brand-400"
            : "border-transparent text-surface-400 hover:text-surface-100 hover:border-surface-600",
        )
      }
    >
      {Icon && <Icon className="h-4 w-4" />}
      {label}
    </NavLink>
  );
}

interface StateTabNavProps {
  children: ReactNode;
  className?: string;
}

export function StateTabNav({ children, className }: StateTabNavProps) {
  return (
    <div
      role="tablist"
      className={cn(
        "flex gap-1 rounded-lg bg-surface-900/50 p-1 w-fit",
        className,
      )}
    >
      {children}
    </div>
  );
}

interface StateTabItemProps {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}

export function StateTabItem({
  active,
  onClick,
  children,
}: StateTabItemProps) {
  return (
    <button
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "px-4 py-2 text-sm font-medium rounded-md transition-colors flex items-center gap-2",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950",
        active
          ? "bg-surface-800 text-surface-100"
          : "text-surface-400 hover:text-surface-200",
      )}
    >
      {children}
    </button>
  );
}
