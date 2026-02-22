import { Outlet, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
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
} from "lucide-react";
import { Sidebar } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";
import { useGameScrapedListener } from "@/hooks/use-game-scraped-listener";
import { usePendingInvitationCount } from "@/hooks/use-relays";
import { useNotifications } from "@/hooks/use-notifications";
import { useBiosStatus } from "@/hooks/use-bios";

export function AppLayout() {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  useGameScrapedListener();
  useNotifications();
  const { data: invitationCountData } = usePendingInvitationCount();
  const relayBadge = invitationCountData?.count;
  const { data: biosData } = useBiosStatus();
  const hasMissingBios =
    biosData?.consoles.some((c) => c.status === "missing") ?? false;

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
        {
          to: "/challenges",
          icon: Flag,
          label: "Challenges",
          matchPaths: ["/challenges"],
        },
        {
          to: "/relays",
          icon: Repeat,
          label: "Relays",
          matchPaths: ["/relays"],
          badge: relayBadge,
        },
        {
          to: "/netplay",
          icon: Wifi,
          label: "Netplay",
          matchPaths: ["/netplay"],
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
              { to: "/admin/settings", icon: Settings, label: "Settings" },
              {
                to: "/admin/bios",
                icon: Cpu,
                label: "BIOS Files",
                warning: hasMissingBios,
              },
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
