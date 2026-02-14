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
    <nav className={cn("flex flex-wrap items-center gap-1 border-b border-surface-800/50", className)}>
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
