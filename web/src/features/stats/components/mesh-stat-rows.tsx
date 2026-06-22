import { Globe } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { formatPlayTime } from "@/lib/format";
import type { AggregatedStat } from "@/generated/schemas";
import { RankBadge } from "./rank-badge";

// Renders the "across connected servers" leaderboard rows. The mesh aggregate is
// metadata-only (label + playtime + player count) — no covers/consoles/avatars —
// so these rows are intentionally leaner than the local ones.
export function MeshStatRows({
  stats,
  isLoading,
  showPlayers,
  emptyDescription,
}: {
  stats: AggregatedStat[] | undefined;
  isLoading: boolean;
  showPlayers: boolean;
  emptyDescription: string;
}) {
  if (isLoading) return <MeshStatRowsSkeleton />;

  if (!stats || stats.length === 0) {
    return (
      <EmptyState
        icon={Globe}
        title="Nothing across connected servers yet"
        description={emptyDescription}
      />
    );
  }

  return (
    <div data-comp="MeshStatRows" className="space-y-2" data-testid="mesh-stat-rows">
      {stats.map((s, index) => (
        <div
          key={`${s.key}-${index}`}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
          data-testid="mesh-stat-row"
        >
          <RankBadge rank={index + 1} />
          <span className="flex-1 min-w-0 truncate text-sm font-medium text-surface-200">
            {s.label}
          </span>
          <span className="whitespace-nowrap font-mono text-sm text-surface-400">
            {formatPlayTime(s.totalPlayTimeSeconds)}
          </span>
          {showPlayers && (
            <span className="whitespace-nowrap text-sm text-surface-500">
              {s.totalPlayers} {s.totalPlayers === 1 ? "player" : "players"}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

function MeshStatRowsSkeleton() {
  return (
    <div data-comp="MeshStatRowsSkeleton" className="space-y-2">
      {Array.from({ length: 6 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}
