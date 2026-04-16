import { type ReactNode, useEffect, useRef } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { cn } from "@/lib/cn";
import { Divider } from "./divider";
import { Gamepad2, LogOut, Search } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface SidebarLinkProps {
  to: string;
  icon: LucideIcon;
  label: string;
  /** Additional path prefixes that should highlight this link. */
  matchPaths?: string[];
  /** Optional count badge shown next to the label. */
  badge?: number;
  /** Show a small warning dot indicator (orange). */
  warning?: boolean;
}

function SidebarLink({ to, icon: Icon, label, matchPaths, badge, warning }: SidebarLinkProps) {
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
      {warning && (
        <span className="h-2.5 w-2.5 rounded-full bg-warning-500 flex-shrink-0" aria-label="Warning" />
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

interface SidebarContentProps {
  links: Array<{
    section?: string;
    items: SidebarLinkProps[];
  }>;
  user?: {
    username: string;
    role: string;
  };
  onLogout?: () => void;
  version?: string;
  onSearchClick?: () => void;
}

function SidebarContent({ links, user, onLogout, version, onSearchClick }: SidebarContentProps) {
  return (
    <>
      <div className="flex items-center gap-3 px-5 py-5 border-b border-surface-800/50">
        <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center shadow-lg shadow-brand-600/20">
          <Gamepad2 className="h-5 w-5 text-white" />
        </div>
        <span className="text-lg font-bold tracking-tight text-surface-100">
          Spela
        </span>
      </div>

      {onSearchClick && (
        <div className="px-3 pt-3 pb-1">
          <button
            onClick={onSearchClick}
            className="flex items-center gap-3 w-full px-3 py-2.5 rounded-xl text-sm text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 transition-all duration-200 cursor-pointer"
            aria-label="Open search"
          >
            <Search className="h-5 w-5 flex-shrink-0" />
            <span className="flex-1 text-left">Search...</span>
            <kbd className="hidden sm:inline-flex items-center rounded border border-surface-700 bg-surface-800 px-1.5 py-0.5 text-xs text-surface-500">
              ⌘K
            </kbd>
          </button>
        </div>
      )}

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
        <>
        <Divider />
        <div className="px-3 py-4">
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
        </>
      )}

      {version && (
        <div className="px-5 pb-3 pt-1">
          <p className="text-xs text-surface-600">v{version}</p>
        </div>
      )}
    </>
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
  mobileOpen?: boolean;
  onMobileClose?: () => void;
  version?: string;
  onSearchClick?: () => void;
}

export function Sidebar({ links, user, onLogout, mobileOpen, onMobileClose, version, onSearchClick }: SidebarProps) {
  const drawerRef = useRef<HTMLElement>(null);

  // Close on Escape key
  useEffect(() => {
    if (!mobileOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onMobileClose?.();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [mobileOpen, onMobileClose]);

  // Lock body scroll when mobile drawer is open
  useEffect(() => {
    if (mobileOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileOpen]);

  return (
    <>
      {/* Desktop sidebar — always visible at lg and above */}
      <aside data-comp="Sidebar" className="hidden lg:flex fixed left-0 top-0 bottom-0 w-64 bg-surface-950 border-r border-surface-800/50 flex-col z-40">
        <SidebarContent links={links} user={user} onLogout={onLogout} version={version} onSearchClick={onSearchClick} />
      </aside>

      {/* Mobile drawer — visible below lg when open */}
      <div className="lg:hidden">
        {/* Backdrop */}
        <div
          className={cn(
            "fixed inset-0 z-40 bg-black/50 transition-opacity",
            mobileOpen
              ? "opacity-100 duration-300"
              : "opacity-0 pointer-events-none duration-200",
          )}
          onClick={onMobileClose}
          aria-hidden="true"
        />

        {/* Drawer panel */}
        <aside
          ref={drawerRef}
          role="dialog"
          aria-modal={mobileOpen ? "true" : undefined}
          aria-label="Navigation menu"
          className={cn(
            "fixed inset-y-0 left-0 z-50 w-64 bg-surface-950 border-r border-surface-800/50 flex flex-col shadow-2xl transition-transform",
            mobileOpen
              ? "translate-x-0 duration-300 ease-out"
              : "-translate-x-full duration-200 ease-in",
          )}
        >
          <SidebarContent links={links} user={user} onLogout={onLogout} version={version} onSearchClick={() => { onSearchClick?.(); onMobileClose?.(); }} />
        </aside>
      </div>
    </>
  );
}
