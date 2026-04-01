import { Clock, Zap, Gamepad2, Trophy } from "lucide-react";
import { Section } from "@/components/ui";
import type { Game } from "@/types/api";

interface TimeToBeatCardProps {
  game: Game;
}

export function TimeToBeatCard({ game }: TimeToBeatCardProps) {
  // Backend stores time-to-beat in seconds (from IGDB API)
  const hastily = (game.timeToBeatHastily ?? 0) / 3600;
  const normally = (game.timeToBeatNormally ?? 0) / 3600;
  const completely = (game.timeToBeatCompletely ?? 0) / 3600;

  if (!hastily && !normally && !completely) return null;

  const maxHours = Math.max(hastily, normally, completely);

  const tiers = [
    {
      label: "Main Story",
      hours: hastily,
      icon: Zap,
      barClass: "bg-brand-400",
      iconClass: "text-brand-400",
      textClass: "text-brand-300",
    },
    {
      label: "Main + Extras",
      hours: normally,
      icon: Gamepad2,
      barClass: "bg-amber-400",
      iconClass: "text-amber-400",
      textClass: "text-amber-300",
    },
    {
      label: "Completionist",
      hours: completely,
      icon: Trophy,
      barClass: "bg-purple-400",
      iconClass: "text-purple-400",
      textClass: "text-purple-300",
    },
  ].filter((t) => t.hours > 0);

  return (
    <Section className="p-6" data-testid="time-to-beat-card">
      <div className="flex items-center gap-2.5 mb-5">
        <Clock className="h-5 w-5 text-brand-400" />
        <h3 className="text-lg font-bold text-surface-100">How Long to Beat</h3>
      </div>
      <div className="space-y-4">
        {tiers.map((tier) => {
          const Icon = tier.icon;
          const pct = maxHours > 0 ? (tier.hours / maxHours) * 100 : 0;
          return (
            <div key={tier.label}>
              <div className="flex items-center justify-between mb-1.5">
                <div className="flex items-center gap-2">
                  <Icon className={`h-4 w-4 ${tier.iconClass}`} />
                  <span className="text-sm font-medium text-surface-200">{tier.label}</span>
                </div>
                <span className={`text-sm font-bold tabular-nums ${tier.textClass}`}>
                  {formatHours(tier.hours)}
                </span>
              </div>
              <div className="h-2 w-full rounded-full bg-surface-800/80 overflow-hidden">
                <div
                  className={`h-full rounded-full ${tier.barClass} transition-all duration-700 ease-out`}
                  style={{ width: `${Math.max(pct, 4)}%`, opacity: 0.85 }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </Section>
  );
}

function formatHours(hours: number): string {
  if (hours < 1) return "<1 hr";
  if (hours === 1) return "1 hr";
  if (hours === Math.floor(hours)) return `${hours} hrs`;
  return `${hours.toFixed(1)} hrs`;
}
