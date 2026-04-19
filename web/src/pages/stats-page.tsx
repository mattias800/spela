import { Link } from "react-router-dom";
import { BarChart3, Calendar, Trophy, Users } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Badge, Skeleton, EmptyState } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { PlayHeatmap } from "@/components/play-heatmap";
import { useMostPlayedGames, useMostActivePlayers } from "@/hooks/use-stats";
import { useMyPlayHeatmap } from "@/hooks/use-play-heatmap";
import { useExplorerBadges, useCompletionistMap } from "@/hooks/use-explore";
import { ExplorerBadgesSection } from "@/features/explore/components/explorer-badges";
import { CompletionistMapSection } from "@/features/explore/components/completionist-map";
import { CoverImage } from "@/components/cover-image";
import { formatPlayTime, formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/cn";

function rankStyle(rank: number): { text: string; bg: string } {
  if (rank === 1)
    return {
      text: "text-amber-400",
      bg: "bg-amber-400/15 border border-amber-400/30",
    };
  if (rank === 2)
    return {
      text: "text-surface-300",
      bg: "bg-surface-300/15 border border-surface-300/30",
    };
  if (rank === 3)
    return {
      text: "text-amber-700",
      bg: "bg-amber-700/15 border border-amber-700/30",
    };
  return { text: "text-surface-500", bg: "" };
}

function RankBadge({ rank }: { rank: number }) {
  const style = rankStyle(rank);
  const isTop3 = rank <= 3;

  if (isTop3) {
    return (
      <span
        className={cn(
          "inline-flex items-center justify-center h-7 w-7 rounded-full text-xs font-bold",
          style.bg,
          style.text,
        )}
      >
        {rank}
      </span>
    );
  }

  return (
    <span className="inline-flex items-center justify-center h-7 w-7 text-sm font-medium text-surface-500">
      {rank}
    </span>
  );
}

function MostPlayedSkeleton() {
  return (
    <div className="space-y-2">
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

function MostActiveSkeleton() {
  return (
    <div className="space-y-2">
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

export function StatsPage() {
  const { data: heatmapData, isLoading: isLoadingHeatmap } =
    useMyPlayHeatmap();
  const { data: mostPlayed, isLoading: isLoadingMostPlayed } =
    useMostPlayedGames();
  const { data: mostActive, isLoading: isLoadingMostActive } =
    useMostActivePlayers();
  const { data: badgesData, isLoading: isLoadingBadges } = useExplorerBadges();
  const { data: completionistData, isLoading: isLoadingCompletionist } =
    useCompletionistMap();

  const hasNoData =
    !isLoadingMostPlayed &&
    !isLoadingMostActive &&
    (!mostPlayed?.games || mostPlayed.games.length === 0) &&
    (!mostActive?.players || mostActive.players.length === 0);

  return (
    <PageLayout title="Stats" subtitle="See how you stack up against other players." icon={BarChart3}>
      <SectionList className="max-w-5xl">
      {hasNoData && (
        <EmptyState
          icon={Trophy}
          title="No play data yet"
          description="Play some games to see the leaderboard come alive."
        />
      )}

      {/* Your Activity Heatmap */}
      {(isLoadingHeatmap || (heatmapData && heatmapData.length > 0)) && (
        <section>
          <div className="flex items-center gap-2.5 mb-5">
            <Calendar className="h-5 w-5 text-brand-400" />
            <h2 className="text-xl font-bold text-surface-100">
              Your Activity
            </h2>
          </div>

          {isLoadingHeatmap ? (
            <Skeleton className="h-[140px] w-full rounded-xl" />
          ) : (
            <PlayHeatmap data={heatmapData ?? []} />
          )}
        </section>
      )}

      {/* Explorer Badges */}
      <ExplorerBadgesSection
        badges={badgesData?.badges}
        isLoading={isLoadingBadges}
      />

      {/* Completionist Map */}
      <CompletionistMapSection
        data={completionistData}
        isLoading={isLoadingCompletionist}
      />

      {/* Most Played Games / Hall of Fame */}
      {(isLoadingMostPlayed ||
        (mostPlayed?.games && mostPlayed.games.length > 0)) && (
        <section>
          <div className="flex items-center gap-2.5 mb-5">
            <Trophy className="h-5 w-5 text-brand-400" />
            <h2 className="text-xl font-bold text-surface-100">Hall of Fame</h2>
          </div>

          {isLoadingMostPlayed ? (
            <MostPlayedSkeleton />
          ) : (
            <div className="space-y-2">
              {mostPlayed?.games?.slice(0, 25).map((entry, index) => {
                const rank = index + 1;
                return (
                  <Link
                    key={entry.game.id}
                    to={`/games/${entry.game.id}`}
                    className="flex items-center gap-4 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
                  >
                    <RankBadge rank={rank} />
                    <CoverImage src={entry.game.coverUrl} alt={entry.game.title} className="h-10 w-8 rounded-lg flex-shrink-0" />
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
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* Most Active Players */}
      {!hasNoData && (
        <section>
          <div className="flex items-center gap-2.5 mb-5">
            <Users className="h-5 w-5 text-brand-400" />
            <h2 className="text-xl font-bold text-surface-100">
              Most Active Players
            </h2>
          </div>

          {isLoadingMostActive ? (
            <MostActiveSkeleton />
          ) : !mostActive?.players || mostActive.players.length === 0 ? (
            <EmptyState
              icon={Trophy}
              title="No player activity yet"
              description="Start playing to see rankings here."
            />
          ) : (
            <div className="space-y-2">
              {mostActive.players.map((player, index) => {
                const rank = index + 1;
                return (
                  <div
                    key={player.userId}
                    className="flex items-center gap-3 rounded-xl px-4 py-3 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
                  >
                    <RankBadge rank={rank} />
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
                );
              })}
            </div>
          )}
        </section>
      )}
    </SectionList>
    </PageLayout>
  );
}

export default StatsPage;
