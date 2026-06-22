import { useState } from "react";
import { Link } from "react-router-dom";
import { Trophy } from "lucide-react";
import { Badge, Skeleton, EmptyState } from "@/components/ui";
import { CoverImage } from "@/components/cover-image";
import { formatPlayTime } from "@/lib/format";
import { useMostPlayedGames } from "@/hooks/use-stats";
import { useFederationAggregatedStats } from "@/hooks/use-federation-stats";
import { RankBadge } from "./rank-badge";
import { MeshStatRows } from "./mesh-stat-rows";
import { StatsSectionHeader, type StatsScope } from "./stats-section-header";

// Self-contained "most played games" section with a This-server | Across-servers
// scope toggle. Owns its own data (local + federated) and every state; no props,
// so it can be dropped anywhere on the Stats screen (or moved/reordered freely).
export function MostPlayedStatsSection() {
  const [scope, setScope] = useState<StatsScope>("this_server");
  const { data: local, isLoading: localLoading } = useMostPlayedGames();
  const { data: mesh, isLoading: meshLoading } = useFederationAggregatedStats(
    "game_play",
    { enabled: scope === "across" },
  );

  return (
    <section data-comp="MostPlayedStatsSection">
      <StatsSectionHeader
        icon={Trophy}
        title="Hall of Fame"
        scope={scope}
        onScopeChange={setScope}
        testId="most-played-scope"
      />

      {scope === "across" ? (
        <MeshStatRows
          stats={mesh}
          isLoading={meshLoading}
          showPlayers
          emptyDescription="Most-played games will appear here once connected servers share their stats."
        />
      ) : localLoading ? (
        <MostPlayedSkeleton />
      ) : !local?.games || local.games.length === 0 ? (
        <EmptyState
          icon={Trophy}
          title="No games played yet"
          description="Play some games to build your hall of fame."
        />
      ) : (
        <div className="space-y-2">
          {local.games.slice(0, 25).map((entry, index) => (
            <Link
              key={entry.game.id}
              to={`/games/${entry.game.id}`}
              className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
            >
              <RankBadge rank={index + 1} />
              <CoverImage
                src={entry.game.coverUrl}
                alt={entry.game.title}
                className="h-10 w-8 rounded-lg flex-shrink-0"
              />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-surface-200 truncate">
                  {entry.game.title}
                </p>
                {entry.game.consoleName && (
                  <Badge variant="brand" className="mt-0.5">
                    {entry.game.consoleName}
                  </Badge>
                )}
              </div>
              <span className="text-sm font-mono text-surface-400 whitespace-nowrap">
                {formatPlayTime(entry.totalPlayTime)}
              </span>
              <span className="text-sm text-surface-500 whitespace-nowrap">
                {entry.totalPlayers}{" "}
                {entry.totalPlayers === 1 ? "player" : "players"}
              </span>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function MostPlayedSkeleton() {
  return (
    <div data-comp="MostPlayedSkeleton" className="space-y-2">
      {Array.from({ length: 10 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-10 w-8 rounded-lg" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-20" />
          </div>
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-4 w-20" />
        </div>
      ))}
    </div>
  );
}
