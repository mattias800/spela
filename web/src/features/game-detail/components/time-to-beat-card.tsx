import { Clock } from "lucide-react";
import type { Game } from "@/types/api";

interface TimeToBeatCardProps {
  game: Game;
}

interface Tier {
  label: string;
  hours: number;
  tooltip: string;
}

export function TimeToBeatCard({ game }: TimeToBeatCardProps) {
  const hastily = (game.timeToBeatHastily ?? 0) / 3600;
  const normally = (game.timeToBeatNormally ?? 0) / 3600;
  const completely = (game.timeToBeatCompletely ?? 0) / 3600;

  if (!hastily && !normally && !completely) return null;

  const tiers: Tier[] = [
    { label: "Main", hours: hastily, tooltip: "Time to finish the main story" },
    { label: "Extras", hours: normally, tooltip: "Main story plus side content" },
    { label: "All", hours: completely, tooltip: "Everything — 100% completion" },
  ];

  return (
    <div
      data-comp="TimeToBeatCard"
      data-testid="time-to-beat-card"
      className="rounded-xl bg-surface-800/30 p-4"
    >
      <div className="flex items-center gap-2.5 mb-3">
        <Clock className="h-4 w-4 text-brand-400" />
        <h3 className="text-sm font-semibold text-surface-200">Time to Beat</h3>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {tiers.map((tier) => (
          <div
            key={tier.label}
            className="rounded-lg bg-surface-900/50 px-2.5 py-2"
            title={tier.tooltip}
          >
            <div className="text-[10px] uppercase tracking-wide text-surface-500 truncate">
              {tier.label}
            </div>
            <div className="text-base font-semibold text-surface-100 tabular-nums mt-0.5">
              {tier.hours > 0 ? (
                formatHours(tier.hours)
              ) : (
                <span className="text-surface-600">—</span>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function formatHours(hours: number): string {
  if (hours < 1) return "<1h";
  if (hours === Math.floor(hours)) return `${hours}h`;
  return `${hours.toFixed(1)}h`;
}
