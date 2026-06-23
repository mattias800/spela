import { useState } from "react";
import { Award } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { useFederationAchievements } from "@/hooks/use-federation-achievements";
import { RankBadge } from "./rank-badge";
import { StatsSectionHeader, type StatsScope } from "./stats-section-header";

// Self-contained, movable "Top Achievers" leaderboard: achievement-unlock counts
// per player, with a This-server | Across-servers scope toggle. Owns its own data
// (the federated achievements aggregate) and all states; zero props, so it can be
// dropped anywhere / reordered freely as the UX is iterated on.
//
// The aggregate always includes local players (hop 0) plus connected-server
// players (hop >= 1); "This server" filters to hop 0 client-side. The mesh rows
// are lean (rank + name + count + server) — the aggregate has no avatars.
export function TopAchieversSection() {
  const [scope, setScope] = useState<StatsScope>("across");
  const { data, isLoading } = useFederationAchievements();

  const rows = (data ?? []).filter((e) =>
    scope === "this_server" ? e.hops === 0 : true,
  );

  return (
    <section data-comp="TopAchieversSection">
      <StatsSectionHeader
        icon={Award}
        title="Top Achievers"
        scope={scope}
        onScopeChange={setScope}
        testId="top-achievers-scope"
      />

      {isLoading ? (
        <TopAchieversSkeleton />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={Award}
          title={
            scope === "this_server"
              ? "No achievements unlocked yet"
              : "Nothing across connected servers yet"
          }
          description={
            scope === "this_server"
              ? "Unlock achievements to climb the leaderboard."
              : "Top achievers will appear here once connected servers share their achievements."
          }
        />
      ) : (
        <div
          data-comp="TopAchieversRows"
          className="space-y-2"
          data-testid="top-achievers-rows"
        >
          {rows.map((e, index) => (
            // Stable identity: (server, username). originFingerprint is stripped
            // from the user-facing response, so it can't key these; index would
            // change on scope re-filter and force remounts.
            <div
              key={`${e.serverName}-${e.username}`}
              className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
              data-testid="top-achievers-row"
            >
              <RankBadge rank={index + 1} />
              <span className="flex-1 min-w-0 truncate text-sm font-medium text-surface-200">
                {e.username}
              </span>
              <span className="whitespace-nowrap font-mono text-sm text-surface-400">
                {e.count} {e.count === 1 ? "achievement" : "achievements"}
              </span>
              {e.serverName && (
                <span className="hidden whitespace-nowrap text-sm text-surface-500 sm:block">
                  on {e.serverName}
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function TopAchieversSkeleton() {
  return (
    <div data-comp="TopAchieversSkeleton" className="space-y-2">
      {Array.from({ length: 6 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-24" />
        </div>
      ))}
    </div>
  );
}
