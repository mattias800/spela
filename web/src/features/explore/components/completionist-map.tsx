import { Map, CheckCircle2 } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { CompletionistMapResponse } from "@/types/api";

interface CompletionistMapProps {
  data: CompletionistMapResponse | undefined;
  isLoading: boolean;
}

export function CompletionistMapSection({
  data,
  isLoading,
}: CompletionistMapProps) {
  if (isLoading) {
    return (
      <section data-testid="completionist-map-skeleton">
        <Skeleton className="w-56 h-7 mb-5" />
        <Skeleton className="h-16 rounded-xl mb-4" />
        <div className="space-y-3">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-12 rounded-lg" />
          ))}
        </div>
      </section>
    );
  }

  if (!data || (data.consoles ?? []).length === 0) return null;

  return (
    <section data-comp="CompletionistMapSection" data-testid="completionist-map">
      <div className="flex items-center gap-2.5 mb-5">
        <Map className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">
          Completionist Map
        </h2>
      </div>

      {/* Overall progress */}
      <div className="rounded-xl border border-surface-800 bg-surface-900/50 p-5 mb-5">
        <div className="flex items-center justify-between mb-3">
          <div>
            <p className="text-sm text-surface-400">Overall Progress</p>
            <p className="text-2xl font-bold text-surface-100">
              {data.totalPlayed}{" "}
              <span className="text-base font-normal text-surface-500">
                / {data.totalGames} games played
              </span>
            </p>
          </div>
          <div className="text-right">
            <p className="text-3xl font-bold text-brand-400">
              {data.overallPct}%
            </p>
          </div>
        </div>
        <div className="h-3 rounded-full bg-surface-800 overflow-hidden">
          <div
            className="h-full rounded-full bg-brand-500 transition-all"
            style={{ width: `${data.overallPct}%` }}
          />
        </div>
      </div>

      {/* Per-console breakdown */}
      <div className="space-y-2">
        {(data.consoles ?? []).map((console) => (
          <div
            key={console.id}
            data-testid={`console-progress-${console.id}`}
            className="flex items-center gap-4 rounded-lg border border-surface-800/50 bg-surface-900/30 px-4 py-3"
          >
            <div className="w-28 flex-shrink-0">
              <p className="text-sm font-medium text-surface-200 truncate">
                {console.name}
              </p>
            </div>
            <div className="flex-1">
              <div className="h-2 rounded-full bg-surface-800 overflow-hidden">
                <div
                  className={cn(
                    "h-full rounded-full transition-all",
                    console.percentage === 100
                      ? "bg-emerald-500"
                      : console.percentage >= 50
                        ? "bg-brand-500"
                        : console.percentage > 0
                          ? "bg-brand-500/60"
                          : "bg-surface-700",
                  )}
                  style={{ width: `${console.percentage}%` }}
                />
              </div>
            </div>
            <div className="w-20 text-right flex items-center justify-end gap-1.5">
              {console.percentage === 100 && (
                <CheckCircle2 className="h-4 w-4 text-emerald-500 flex-shrink-0" />
              )}
              <span className="text-sm font-mono text-surface-400">
                {console.playedGames}/{console.totalGames}
              </span>
            </div>
            <span className="w-12 text-right text-sm font-mono text-surface-500">
              {console.percentage}%
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
