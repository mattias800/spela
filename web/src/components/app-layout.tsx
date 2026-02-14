import { Outlet, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  Library,
  BarChart3,
  Activity,
  SlidersHorizontal,
  Settings,
  Users,
  ScanSearch,
  FileSearch,
} from "lucide-react";
import { Sidebar } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";
import { useGameScrapedListener } from "@/hooks/use-game-scraped-listener";

export function AppLayout() {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  useGameScrapedListener();

  const links = [
    {
      section: undefined,
      items: [
        { to: "/", icon: LayoutDashboard, label: "Dashboard" },
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
        { to: "/activity", icon: Activity, label: "Activity" },
        { to: "/preferences", icon: SlidersHorizontal, label: "Preferences" },
      ],
    },
    ...(isAdmin
      ? [
          {
            section: "Admin",
            items: [
              { to: "/admin/users", icon: Users, label: "Users" },
              { to: "/admin/settings", icon: Settings, label: "Settings" },
              { to: "/admin/scan", icon: ScanSearch, label: "Library Scan" },
              {
                to: "/admin/metadata",
                icon: FileSearch,
                label: "Metadata Fix",
              },
            ],
          },
        ]
      : []),
  ];

  return (
    <div className="min-h-screen">
      <Sidebar
        links={links}
        user={user ? { username: user.username, role: user.role } : undefined}
        onLogout={() => {
          logout();
          navigate("/login");
        }}
      />

      <div className="pl-64">
        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
