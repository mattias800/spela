import { Outlet, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  Gamepad2,
  Library,
  Heart,
  SlidersHorizontal,
  Settings,
  Users,
  ScanSearch,
  LogOut,
  FileSearch,
} from "lucide-react";
import { Sidebar } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const isAdmin = user?.role === "admin" || user?.role === "owner";

  const links = [
    {
      section: undefined,
      items: [
        { to: "/", icon: LayoutDashboard, label: "Dashboard" },
        { to: "/consoles", icon: Gamepad2, label: "Consoles" },
        { to: "/games", icon: Library, label: "Games" },
        { to: "/favorites", icon: Heart, label: "Favorites" },
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
              { to: "/admin/metadata", icon: FileSearch, label: "Metadata Fix" },
            ],
          },
        ]
      : []),
  ];

  return (
    <div className="min-h-screen">
      <Sidebar links={links} />

      {/* Top bar */}
      <div className="pl-64">
        <header className="sticky top-0 z-30 flex items-center justify-end gap-4 px-6 py-3 border-b border-surface-800/50 bg-surface-950/80 backdrop-blur-xl">
          <div className="flex items-center gap-3">
            <div className="text-right">
              <p className="text-sm font-medium text-surface-200">{user?.username}</p>
              <p className="text-xs text-surface-500 capitalize">{user?.role}</p>
            </div>
            <div className="h-9 w-9 rounded-full bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-sm font-bold text-white">
              {user?.username?.charAt(0).toUpperCase()}
            </div>
            <button
              onClick={() => {
                logout();
                navigate("/login");
              }}
              className="p-2 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
              title="Logout"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </header>

        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
