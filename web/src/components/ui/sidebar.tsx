import { type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { cn } from "@/lib/cn";
import { Gamepad2 } from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface SidebarLinkProps {
  to: string;
  icon: LucideIcon;
  label: string;
}

function SidebarLink({ to, icon: Icon, label }: SidebarLinkProps) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200",
          isActive
            ? "bg-brand-600/15 text-brand-400 shadow-sm"
            : "text-surface-400 hover:text-surface-100 hover:bg-surface-800/50",
        )
      }
    >
      <Icon className="h-5 w-5 flex-shrink-0" />
      <span>{label}</span>
    </NavLink>
  );
}

interface SidebarSectionProps {
  title?: string;
  children: ReactNode;
}

function SidebarSection({ title, children }: SidebarSectionProps) {
  return (
    <div className="space-y-1">
      {title && (
        <p className="px-3 py-2 text-xs font-semibold uppercase tracking-wider text-surface-500">
          {title}
        </p>
      )}
      {children}
    </div>
  );
}

interface SidebarProps {
  links: Array<{
    section?: string;
    items: SidebarLinkProps[];
  }>;
}

export function Sidebar({ links }: SidebarProps) {
  return (
    <aside className="fixed left-0 top-0 bottom-0 w-64 bg-surface-950 border-r border-surface-800/50 flex flex-col z-40">
      <div className="flex items-center gap-3 px-5 py-5 border-b border-surface-800/50">
        <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center shadow-lg shadow-brand-600/20">
          <Gamepad2 className="h-5 w-5 text-white" />
        </div>
        <span className="text-lg font-bold tracking-tight text-surface-100">Spela</span>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-6">
        {links.map((group, i) => (
          <SidebarSection key={i} title={group.section}>
            {group.items.map((item) => (
              <SidebarLink key={item.to} {...item} />
            ))}
          </SidebarSection>
        ))}
      </nav>
    </aside>
  );
}
