import { useState } from "react";
import { Users } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { formatPlayTime, formatRelativeTime } from "@/lib/format";
import { useMostActivePlayers } from "@/hooks/use-stats";
import { useFederationAggregatedStats } from "@/hooks/use-federation-stats";
import { RankBadge } from "./rank-badge";
import { MeshStatRows } from "./mesh-stat-rows";
import { StatsSectionHeader, type StatsScope } from "./stats-section-header";

// Self-contained "most active players" section with a This-server | Across-servers
// scope toggle. Owns its own data (local + federated) and every state; no props,
// so it can be dropped anywhere on the Stats screen (or moved/reordered freely).
export function MostActivePlayersStatsSection() {
  const [scope, setScope] = useState<StatsScope>("this_server");
  const { data: local, isLoading: localLoading } = useMostActivePlayers();
  const { data: mesh, isLoading: meshLoading } = useFederationAggregatedStats(
    "player_play",
    { enabled: scope === "across" },
  );

  return (
    <section data-comp="MostActivePlayersStatsSection">
      <StatsSectionHeader
        icon={Users}
        title="Most Active Players"
        scope={scope}
        onScopeChange={setScope}
        testId="most-active-scope"
      />

      {scope === "across" ? (
        <MeshStatRows
          stats={mesh}
          isLoading={meshLoading}
          showPlayers={false}
          emptyDescription="Active players will appear here once connected servers share their stats."
        />
      ) : localLoading ? (
        <MostActiveSkeleton />
      ) : !local?.players || local.players.length === 0 ? (
        <EmptyState
          icon={Users}
          title="No player activity yet"
          description="Start playing to see rankings here."
        />
      ) : (
        <div className="space-y-2">
          {local.players.map((player, index) => (
            <div
              key={player.userId}
              className="flex items-center gap-3 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
            >
              <RankBadge rank={index + 1} />
              <PlayerAvatar
                username={player.username}
                avatarUrl={player.avatarUrl}
              />
              <span className="text-sm font-medium text-surface-200 flex-1 min-w-0 truncate">
                {player.username}
              </span>
              <span className="text-sm font-mono text-surface-400 whitespace-nowrap">
                {formatPlayTime(player.totalPlayTime)}
              </span>
              <span className="text-sm text-surface-500 whitespace-nowrap">
                {player.gamesPlayed}{" "}
                {player.gamesPlayed === 1 ? "game" : "games"}
              </span>
              <span className="text-sm text-surface-500 whitespace-nowrap">
                {formatRelativeTime(player.lastPlayed)}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function MostActiveSkeleton() {
  return (
    <div data-comp="MostActiveSkeleton" className="space-y-2">
      {Array.from({ length: 10 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-3 rounded-xl px-4 py-3 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-8 w-8 rounded-full" />
          <div className="flex-1">
            <Skeleton className="h-4 w-32" />
          </div>
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}
