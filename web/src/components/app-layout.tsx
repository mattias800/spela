import { useState, useEffect, useCallback } from "react";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
import {
  LayoutDashboard,
  Compass,
  Library,
  BarChart3,
  Activity,
  Flag,
  Repeat,
  Wifi,
  SlidersHorizontal,
  Settings,
  Users,
  ScanSearch,
  FileSearch,
  Cpu,
  Upload,
  Menu,
  Trophy,
  Gamepad2,
  GitCompareArrows,
} from "lucide-react";
import { Sidebar } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";
import { useGameScrapedListener } from "@/hooks/use-game-scraped-listener";
import { usePendingInvitationCount } from "@/hooks/use-shared-sessions";
import { usePendingNetplayInviteCount } from "@/hooks/use-netplay";
import { useNotifications } from "@/hooks/use-notifications";
import { useBiosStatus } from "@/hooks/use-bios";
import { useIgdbStatus } from "@/hooks/use-admin";
import { useHealth } from "@/hooks/use-health";
import { SearchPalette } from "@/features/search/components/search-palette";

export function AppLayout() {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  useGameScrapedListener();
  useNotifications();
  const { data: invitationCountData } = usePendingInvitationCount();
  const sharedSessionBadge = invitationCountData?.count;
  const { data: netplayInviteCountData } = usePendingNetplayInviteCount();
  const netplayBadge = netplayInviteCountData?.count;
  const { data: biosData } = useBiosStatus();
  const hasMissingBios =
    biosData?.consoles.some((c) => c.status === "missing") ?? false;
  const { data: igdbStatus } = useIgdbStatus();
  const igdbNotConfigured = !(igdbStatus?.configured ?? true);
  const { data: health } = useHealth();

  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const closeMobileMenu = useCallback(() => setMobileMenuOpen(false), []);

  // Close mobile menu on navigation
  useEffect(() => {
    closeMobileMenu();
  }, [location.pathname, closeMobileMenu]);

  const links = [
    {
      section: undefined,
      items: [
        { to: "/", icon: LayoutDashboard, label: "Dashboard" },
        { to: "/explore", icon: Compass, label: "Explore" },
        {
          to: "/consoles",
          icon: Library,
          label: "Library",
          matchPaths: [
            "/consoles",
            "/games",
            "/favorites",
            "/play-later",
            "/collections",
          ],
        },
        { to: "/stats", icon: BarChart3, label: "Stats" },
        { to: "/top-lists", icon: Trophy, label: "Top Lists" },
        { to: "/activity", icon: Activity, label: "Activity" },
        {
          to: "/challenges",
          icon: Flag,
          label: "Challenges",
          matchPaths: ["/challenges"],
        },
        {
          to: "/shared-sessions",
          icon: Repeat,
          label: "Shared Sessions",
          matchPaths: ["/shared-sessions"],
          badge: sharedSessionBadge,
        },
        {
          to: "/netplay",
          icon: Wifi,
          label: "Netplay",
          matchPaths: ["/netplay"],
          badge: netplayBadge,
        },
        { to: "/preferences", icon: SlidersHorizontal, label: "Preferences" },
      ],
    },
    ...(isAdmin
      ? [
          {
            section: "Admin",
            items: [
              { to: "/admin/users", icon: Users, label: "Users" },
              {
                to: "/admin/settings",
                icon: Settings,
                label: "Settings",
                warning: igdbNotConfigured,
              },
              {
                to: "/admin/bios",
                icon: Cpu,
                label: "BIOS Files",
                warning: hasMissingBios,
              },
              { to: "/admin/upload", icon: Upload, label: "Upload ROMs" },
              { to: "/admin/scan", icon: ScanSearch, label: "Library Scan" },
              {
                to: "/admin/metadata",
                icon: FileSearch,
                label: "Metadata Fix",
              },
              { to: "/admin/cheats", icon: Gamepad2, label: "Cheats" },
              {
                to: "/admin/core-compatibility",
                icon: GitCompareArrows,
                label: "Core Compatibility",
              },
            ],
          },
        ]
      : []),
  ];

  return (
    <div className="min-h-screen">
      {/* Mobile header bar — visible below lg */}
      <header className="fixed top-0 left-0 right-0 z-30 flex items-center gap-3 h-14 px-4 bg-surface-950 border-b border-surface-800/50 lg:hidden">
        <button
          onClick={() => setMobileMenuOpen(true)}
          className="p-2 -ml-1 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
          aria-label="Open navigation menu"
          aria-expanded={mobileMenuOpen}
        >
          <Menu className="h-5 w-5" />
        </button>
        <div className="flex items-center gap-2.5">
          <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center shadow-lg shadow-brand-600/20">
            <Gamepad2 className="h-4 w-4 text-white" />
          </div>
          <span className="text-base font-bold tracking-tight text-surface-100">
            Spela
          </span>
        </div>
      </header>

      <Sidebar
        links={links}
        user={user ? { username: user.username, role: user.role } : undefined}
        onLogout={() => {
          logout();
          navigate("/login");
        }}
        mobileOpen={mobileMenuOpen}
        onMobileClose={closeMobileMenu}
        version={health?.version}
        onSearchClick={() => window.dispatchEvent(new Event("open-search-palette"))}
      />

      {/* Content area — left padding on desktop, top padding on mobile */}
      <div className="pt-14 lg:pt-0 lg:pl-64">
        <main className="p-6">
          <Outlet />
        </main>
      </div>

      <SearchPalette />
    </div>
  );
}
