import { type ReactNode } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { cn } from "@/lib/cn";
import { Gamepad2, LogOut } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface SidebarLinkProps {
  to: string;
  icon: LucideIcon;
  label: string;
  /** Additional path prefixes that should highlight this link. */
  matchPaths?: string[];
  /** Optional count badge shown next to the label. */
  badge?: number;
}

function SidebarLink({ to, icon: Icon, label, matchPaths, badge }: SidebarLinkProps) {
  const location = useLocation();

  return (
    <NavLink
      to={to}
      className={({ isActive }) => {
        const active =
          isActive ||
          (matchPaths?.some((p) => location.pathname.startsWith(p)) ?? false);
        return cn(
          "flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200",
          active
            ? "bg-brand-600/15 text-brand-400 shadow-sm"
            : "text-surface-400 hover:text-surface-100 hover:bg-surface-800/50",
        );
      }}
    >
      <Icon className="h-5 w-5 flex-shrink-0" />
      <span className="flex-1">{label}</span>
      {badge != null && badge > 0 && (
        <span className="inline-flex items-center justify-center h-5 min-w-5 px-1.5 rounded-full text-xs font-medium bg-brand-500/15 text-brand-400">
          {badge}
        </span>
      )}
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
  user?: {
    username: string;
    role: string;
  };
  onLogout?: () => void;
}

export function Sidebar({ links, user, onLogout }: SidebarProps) {
  return (
    <aside className="fixed left-0 top-0 bottom-0 w-64 bg-surface-950 border-r border-surface-800/50 flex flex-col z-40">
      <div className="flex items-center gap-3 px-5 py-5 border-b border-surface-800/50">
        <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center shadow-lg shadow-brand-600/20">
          <Gamepad2 className="h-5 w-5 text-white" />
        </div>
        <span className="text-lg font-bold tracking-tight text-surface-100">
          Spela
        </span>
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

      {user && (
        <div className="border-t border-surface-800/50 px-3 py-4">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 flex-shrink-0 rounded-full bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-sm font-bold text-white">
              {user.username.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-surface-200 truncate">
                {user.username}
              </p>
              <p className="text-xs text-surface-500 capitalize">{user.role}</p>
            </div>
            {onLogout && (
              <button
                onClick={onLogout}
                className="p-2 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 transition-colors cursor-pointer"
                title="Logout"
                aria-label="Logout"
              >
                <LogOut className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
      )}
    </aside>
  );
}
