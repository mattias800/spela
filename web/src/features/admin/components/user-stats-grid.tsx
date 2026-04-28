import { Users, Gamepad2, BarChart3, Save } from "lucide-react";
import { Section } from "@/components/ui";

interface AdminStats {
  users: number;
  games: number;
  consoles: number;
  saves: number;
}

interface UserStatsGridProps {
  stats: AdminStats;
}

export function UserStatsGrid({ stats }: UserStatsGridProps) {
  const items = [
    { icon: Users, value: stats.users, label: "Users" },
    { icon: Gamepad2, value: stats.games, label: "Games" },
    { icon: BarChart3, value: stats.consoles, label: "Consoles" },
    { icon: Save, value: stats.saves, label: "Saves" },
  ];

  return (
    <div data-comp="UserStatsGrid" className="grid grid-cols-2 md:grid-cols-4 gap-4">
      {items.map(({ icon: Icon, value, label }) => (
        <Section key={label}>
          <div className="px-5 pb-5 flex items-center gap-3 py-4">
            <div className="h-10 w-10 rounded-xl bg-brand-500/10 flex items-center justify-center">
              <Icon className="h-5 w-5 text-brand-400" />
            </div>
            <div>
              <p className="text-2xl font-bold text-surface-100">{value}</p>
              <p className="text-xs text-surface-500">{label}</p>
            </div>
          </div>
        </Section>
      ))}
    </div>
  );
}
