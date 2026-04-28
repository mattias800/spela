import {
  Globe,
  Palette,
  Clock,
  Gamepad2,
  Timer,
  Medal,
} from "lucide-react";
import { Skeleton } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { ExplorerBadge } from "@/types/api";

const BADGE_ICONS: Record<string, React.ElementType> = {
  "console-explorer": Globe,
  "genre-master": Palette,
  "time-traveler": Clock,
  centurion: Gamepad2,
  "dedicated-gamer": Timer,
  "marathon-runner": Medal,
};

interface ExplorerBadgesProps {
  badges: ExplorerBadge[] | undefined;
  isLoading: boolean;
}

export function ExplorerBadgesSection({
  badges,
  isLoading,
}: ExplorerBadgesProps) {
  if (isLoading) {
    return (
      <section data-testid="explorer-badges-skeleton">
        <Skeleton className="w-48 h-7 mb-5" />
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-36 rounded-xl" />
          ))}
        </div>
      </section>
    );
  }

  if (!badges || badges.length === 0) return null;

  return (
    <section data-comp="ExplorerBadgesSection" data-testid="explorer-badges">
      <div className="flex items-center gap-2.5 mb-5">
        <Medal className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">Explorer Badges</h2>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        {badges.map((badge) => {
          const Icon = BADGE_ICONS[badge.id] ?? Medal;
          const pct =
            badge.target > 0
              ? Math.min(100, Math.round((badge.progress / badge.target) * 100))
              : 0;

          return (
            <div
              key={badge.id}
              data-testid={`badge-${badge.id}`}
              className={cn(
                "rounded-xl border p-4 transition-colors",
                badge.earned
                  ? "border-brand-500/50 bg-brand-500/10"
                  : "border-surface-800 bg-surface-900/50",
              )}
            >
              <div className="flex items-center gap-3 mb-3">
                <div
                  className={cn(
                    "h-10 w-10 rounded-full flex items-center justify-center",
                    badge.earned
                      ? "bg-brand-500/20 text-brand-400"
                      : "bg-surface-800 text-surface-500",
                  )}
                >
                  <Icon className="h-5 w-5" />
                </div>
                {badge.earned && (
                  <span className="text-xs font-bold text-brand-400 uppercase tracking-wide">
                    Earned
                  </span>
                )}
              </div>

              <h3
                className={cn(
                  "text-sm font-bold",
                  badge.earned ? "text-surface-100" : "text-surface-300",
                )}
              >
                {badge.name}
              </h3>
              <p className="text-xs text-surface-500 mt-0.5">
                {badge.description}
              </p>

              {/* Progress bar */}
              <div className="mt-3">
                <div className="flex justify-between text-xs mb-1">
                  <span className="text-surface-400">
                    {badge.progress} / {badge.target}
                  </span>
                  <span className="text-surface-500">{pct}%</span>
                </div>
                <div className="h-1.5 rounded-full bg-surface-800 overflow-hidden">
                  <div
                    className={cn(
                      "h-full rounded-full transition-all",
                      badge.earned ? "bg-brand-500" : "bg-surface-600",
                    )}
                    style={{ width: `${pct}%` }}
                  />
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
